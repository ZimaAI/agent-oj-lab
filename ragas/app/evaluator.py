from __future__ import annotations

import logging
import math
import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Mapping, Optional, Tuple
from urllib.parse import urlparse, urlunparse

from .schemas import EvaluateResult, EvaluateSample

logger = logging.getLogger(__name__)
# 预编译分词正则，避免降级路径中重复编译导致额外内存压力。
_TOKEN_PATTERN = re.compile(r"[A-Za-z0-9_]+|[\u4e00-\u9fff]")
_RAGAS_METRIC_KEYS = (
    "answer_relevancy",
    "faithfulness",
    "context_precision",
    "context_recall",
)


@dataclass
class _RagasRuntime:
    dataset_cls: Any
    evaluate_fn: Any
    metrics: Tuple[Any, Any, Any, Any]

    openai_client_cls: Optional[Any] = None
    llm_factory_fn: Optional[Any] = None
    embedding_factory_fn: Optional[Any] = None
    langchain_chat_openai_cls: Optional[Any] = None
    langchain_openai_embeddings_cls: Optional[Any] = None


@dataclass(frozen=True)
class RagasLlmConfig:
    api_key: Optional[str]
    base_url: Optional[str]
    llm_model: str
    embedding_model: str

    # 返回默认配置文件路径（ragas/config.json）。
    @staticmethod
    def default_config_path() -> Path:
        return Path(__file__).resolve().parents[1] / "config.json"

    # 优先从配置文件读取，缺失项回退到环境变量，保证无缝兼容。
    @classmethod
    def from_file(
        cls,
        config_path: Optional[Path] = None,
        env: Optional[Mapping[str, str]] = None,
    ) -> "RagasLlmConfig":
        file_values = cls._load_file_values(config_path or cls.default_config_path())
        source = os.environ if env is None else env
        return cls(
            api_key=cls._first_non_blank(file_values, "api_key")
            or cls._first_non_blank(source, "RAGAS_OPENAI_API_KEY", "OPENAI_API_KEY"),
            base_url=cls._first_non_blank(file_values, "base_url")
            or cls._first_non_blank(source, "RAGAS_OPENAI_BASE_URL", "OPENAI_BASE_URL"),
            llm_model=cls._first_non_blank(file_values, "llm_model")
            or cls._first_non_blank(source, "RAGAS_OPENAI_MODEL")
            or "gpt-4o-mini",
            embedding_model=cls._first_non_blank(file_values, "embedding_model")
            or cls._first_non_blank(source, "RAGAS_OPENAI_EMBEDDING_MODEL")
            or "text-embedding-3-small",
        )

    # 从环境变量加载 RAGAS 所需的大模型连接配置。
    @classmethod
    def from_env(cls, env: Optional[Mapping[str, str]] = None) -> "RagasLlmConfig":
        source = os.environ if env is None else env
        return cls(
            api_key=cls._first_non_blank(source, "RAGAS_OPENAI_API_KEY", "OPENAI_API_KEY"),
            base_url=cls._first_non_blank(source, "RAGAS_OPENAI_BASE_URL", "OPENAI_BASE_URL"),
            llm_model=cls._first_non_blank(source, "RAGAS_OPENAI_MODEL") or "gpt-4o-mini",
            embedding_model=cls._first_non_blank(source, "RAGAS_OPENAI_EMBEDDING_MODEL")
            or "text-embedding-3-small",
        )

    # 解析配置文件中的 openai 段，异常或缺失时返回空映射以便走兜底。
    @staticmethod
    def _load_file_values(config_path: Path) -> Mapping[str, str]:
        try:
            if not config_path.exists():
                return {}
            payload = json.loads(config_path.read_text(encoding="utf-8"))
            if not isinstance(payload, dict):
                return {}
            openai_section = payload.get("openai", payload)
            if not isinstance(openai_section, dict):
                return {}
            return {
                str(k): str(v)
                for k, v in openai_section.items()
                if isinstance(k, str) and v is not None
            }
        except Exception:  # pragma: no cover - 防御式兜底
            return {}

    # 获取第一个非空白配置值，空字符串按未配置处理。
    @staticmethod
    def _first_non_blank(source: Mapping[str, str], *keys: str) -> Optional[str]:
        for key in keys:
            raw_value = source.get(key)
            if raw_value is None:
                continue
            value = raw_value.strip()
            if value:
                return value
        return None

    # 归一化 OpenAI 兼容 base_url，兼容 DashScope 的标准路径约定。
    @staticmethod
    def normalize_base_url(base_url: Optional[str]) -> Optional[str]:
        if not base_url:
            return None

        value = base_url.strip()
        if not value:
            return None

        try:
            parsed = urlparse(value)
        except Exception:
            return value

        host = (parsed.hostname or "").lower()
        if host.endswith("dashscope.aliyuncs.com"):
            path = (parsed.path or "").rstrip("/")
            if "/compatible-mode/v1" not in path.lower():
                normalized = parsed._replace(path="/compatible-mode/v1")
                return urlunparse(normalized)
        return value


# 封装 RAGAS 评估与降级逻辑，确保服务在缺少依赖时仍可用。
class RagasEvaluator:
    def __init__(self, llm_config: Optional[RagasLlmConfig] = None) -> None:
        self._llm_config = llm_config or RagasLlmConfig.from_file()
        self._runtime, self._init_error = self._try_init_ragas_runtime()

    @property
    def degraded(self) -> bool:
        return self._runtime is None

    # 按批量样本执行评估，失败时自动降级到本地启发式评分。
    def evaluate_batch(self, samples: List[EvaluateSample]) -> Tuple[bool, List[EvaluateResult]]:
        if self._runtime is None:
            return True, [self._fallback_evaluate(sample, self._init_error) for sample in samples]

        try:
            ragas_rows = self._evaluate_with_ragas(samples)
            results = [self._build_result(sample.id, ragas_rows[idx], None) for idx, sample in enumerate(samples)]
            return False, results
        except Exception as ex:  # pragma: no cover - 防御式兜底
            logger.exception("RAGAS evaluation failed, fallback to degraded mode: %s", ex)
            message = f"RAGAS runtime failed, fallback used: {ex}"
            return True, [self._fallback_evaluate(sample, message) for sample in samples]

    # 尝试加载 RAGAS 运行时依赖。
    @staticmethod
    def _try_init_ragas_runtime() -> Tuple[Optional[_RagasRuntime], Optional[str]]:
        try:
            from datasets import Dataset
            from ragas import evaluate
            from ragas.metrics import answer_relevancy, context_precision, context_recall, faithfulness

            # 尝试加载可选工厂，用于显式注入 llm/embeddings 配置。
            openai_client_cls = None
            llm_factory_fn = None
            embedding_factory_fn = None
            langchain_chat_openai_cls = None
            langchain_openai_embeddings_cls = None

            # 先加载 RAGAS 官方 llm/embedding 工厂，作为通用兜底能力。
            try:
                from openai import OpenAI
                from ragas.embeddings.base import embedding_factory
                from ragas.llms.base import llm_factory

                openai_client_cls = OpenAI
                llm_factory_fn = llm_factory
                embedding_factory_fn = embedding_factory
            except Exception:  # pragma: no cover - 兼容不同 ragas 版本
                pass

            # 再尝试加载 LangChain OpenAI 适配器，用于兼容部分网关的请求格式约束。
            try:
                from langchain_openai import ChatOpenAI as LangchainChatOpenAI
                from langchain_openai import OpenAIEmbeddings as LangchainOpenAIEmbeddings

                langchain_chat_openai_cls = LangchainChatOpenAI
                langchain_openai_embeddings_cls = LangchainOpenAIEmbeddings
            except Exception:  # pragma: no cover - 兼容不同 ragas 版本
                pass

            runtime = _RagasRuntime(
                dataset_cls=Dataset,
                evaluate_fn=evaluate,
                metrics=(answer_relevancy, faithfulness, context_precision, context_recall),
                openai_client_cls=openai_client_cls,
                llm_factory_fn=llm_factory_fn,
                embedding_factory_fn=embedding_factory_fn,
                langchain_chat_openai_cls=langchain_chat_openai_cls,
                langchain_openai_embeddings_cls=langchain_openai_embeddings_cls,
            )
            return runtime, None
        except Exception as ex:
            return None, f"RAGAS dependencies unavailable: {ex}"

    # 调用 RAGAS 官方评估并转换成统一行结构。
    def _evaluate_with_ragas(self, samples: List[EvaluateSample]) -> List[Dict[str, Optional[float]]]:
        assert self._runtime is not None
        dataset = self._runtime.dataset_cls.from_dict(
            {
                "question": [s.question for s in samples],
                "answer": [s.answer for s in samples],
                "ground_truth": [s.ground_truth for s in samples],
                "contexts": [s.contexts for s in samples],
            }
        )

        ragas_kwargs = self._build_ragas_runtime_kwargs()
        try:
            return self._run_ragas_evaluation(
                dataset=dataset,
                metrics=list(self._runtime.metrics),
                ragas_kwargs=ragas_kwargs,
                expected_size=len(samples),
            )
        except Exception as ex:
            logger.warning("RAGAS batch evaluation failed, retrying per metric: %s", ex)
            return self._evaluate_metrics_individually(dataset, ragas_kwargs, len(samples), ex)

    def _run_ragas_evaluation(
        self,
        dataset: Any,
        metrics: List[Any],
        ragas_kwargs: Dict[str, Any],
        expected_size: int,
    ) -> List[Dict[str, Optional[float]]]:
        assert self._runtime is not None
        ragas_result = self._runtime.evaluate_fn(
            dataset=dataset,
            metrics=metrics,
            # 指标级异常（如 faithfulness 解析失败）按空分处理，避免整批评估直接失败。
            raise_exceptions=False,
            **ragas_kwargs,
        )
        rows = self._extract_ragas_rows(ragas_result)
        if len(rows) != expected_size:
            raise RuntimeError(f"Unexpected RAGAS result size: expected {expected_size}, got {len(rows)}")
        return rows

    def _evaluate_metrics_individually(
        self,
        dataset: Any,
        ragas_kwargs: Dict[str, Any],
        expected_size: int,
        initial_error: Exception,
    ) -> List[Dict[str, Optional[float]]]:
        assert self._runtime is not None

        rows = [self._empty_metric_row() for _ in range(expected_size)]
        success = False
        last_error: Exception = initial_error

        for metric in self._runtime.metrics:
            metric_name = self._metric_name(metric)
            if metric_name is None:
                continue

            try:
                metric_rows = self._run_ragas_evaluation(
                    dataset=dataset,
                    metrics=[metric],
                    ragas_kwargs=ragas_kwargs,
                    expected_size=expected_size,
                )
                for idx, metric_row in enumerate(metric_rows):
                    rows[idx][metric_name] = metric_row.get(metric_name)
                success = True
            except Exception as metric_error:
                last_error = metric_error
                logger.warning("RAGAS metric %s failed, keeping null score: %s", metric_name, metric_error)

        if success:
            return rows

        raise RuntimeError("RAGAS metric-level fallback failed for all metrics") from last_error

    @staticmethod
    def _metric_name(metric: Any) -> Optional[str]:
        if isinstance(metric, str):
            name = metric
        else:
            name = getattr(metric, "name", None)

        if not isinstance(name, str):
            return None

        normalized_name = name.strip().lower()
        return normalized_name if normalized_name in _RAGAS_METRIC_KEYS else None

    @staticmethod
    def _empty_metric_row() -> Dict[str, Optional[float]]:
        return {metric_key: None for metric_key in _RAGAS_METRIC_KEYS}

    def _extract_ragas_rows(self, ragas_result: Any) -> List[Dict[str, Optional[float]]]:
        rows: List[Dict[str, Optional[float]]] = []

        if hasattr(ragas_result, "to_pandas"):
            frame = ragas_result.to_pandas()
            for _, row in frame.iterrows():
                rows.append(
                    {
                        "answer_relevancy": self._safe_float(row.get("answer_relevancy")),
                        "faithfulness": self._safe_float(row.get("faithfulness")),
                        "context_precision": self._safe_float(row.get("context_precision")),
                        "context_recall": self._safe_float(row.get("context_recall")),
                    }
                )
            return rows

        # 兼容不同版本可能的 scores 结构。
        scores = getattr(ragas_result, "scores", None)
        if isinstance(scores, list):
            for row in scores:
                rows.append(
                    {
                        "answer_relevancy": self._safe_float(row.get("answer_relevancy")) if isinstance(row, dict) else None,
                        "faithfulness": self._safe_float(row.get("faithfulness")) if isinstance(row, dict) else None,
                        "context_precision": self._safe_float(row.get("context_precision")) if isinstance(row, dict) else None,
                        "context_recall": self._safe_float(row.get("context_recall")) if isinstance(row, dict) else None,
                    }
                )
            return rows

        raise RuntimeError("Unsupported RAGAS result format")

    # 构建 evaluate 所需的 llm 与 embeddings 参数，确保使用显式 API 配置。
    def _build_ragas_runtime_kwargs(self) -> Dict[str, Any]:
        assert self._runtime is not None

        if self._runtime.langchain_chat_openai_cls is None and (
            self._runtime.openai_client_cls is None or self._runtime.llm_factory_fn is None
        ):
            return {}

        if not self._llm_config.api_key:
            raise RuntimeError(
                "RAGAS OpenAI API key is missing, set ragas/config.json -> openai.api_key "
                "or RAGAS_OPENAI_API_KEY/OPENAI_API_KEY"
            )

        normalized_base_url = RagasLlmConfig.normalize_base_url(self._llm_config.base_url)
        if normalized_base_url != self._llm_config.base_url:
            logger.warning(
                "RAGAS base_url adjusted for OpenAI compatibility: %s -> %s",
                self._llm_config.base_url,
                normalized_base_url,
            )

        client = None
        if self._runtime.openai_client_cls is not None:
            client_kwargs: Dict[str, str] = {"api_key": self._llm_config.api_key}
            if normalized_base_url:
                client_kwargs["base_url"] = normalized_base_url
            client = self._runtime.openai_client_cls(**client_kwargs)

        # 优先使用 LangChain ChatOpenAI，并显式关闭 responses API 以兼容更多 OpenAI 网关。
        if self._runtime.langchain_chat_openai_cls is not None:
            langchain_llm_kwargs: Dict[str, Any] = {
                "model": self._llm_config.llm_model,
                "api_key": self._llm_config.api_key,
                "use_responses_api": False,
            }
            if normalized_base_url:
                langchain_llm_kwargs["base_url"] = normalized_base_url
            llm = self._runtime.langchain_chat_openai_cls(**langchain_llm_kwargs)
        elif self._runtime.llm_factory_fn is not None and client is not None:
            llm = self._runtime.llm_factory_fn(
                model=self._llm_config.llm_model,
                provider="openai",
                client=client,
            )
        else:
            raise RuntimeError("RAGAS LLM runtime unavailable")

        # 优先使用 LangChain Embeddings 适配旧评估链路对 embed_query 的调用。
        if self._runtime.langchain_openai_embeddings_cls is not None:
            langchain_embedding_kwargs: Dict[str, Any] = {
                "model": self._llm_config.embedding_model,
                "api_key": self._llm_config.api_key,
                # 关闭本地分词截断，避免向兼容网关透传 token id 导致 400。
                "check_embedding_ctx_length": False,
            }
            if normalized_base_url:
                langchain_embedding_kwargs["base_url"] = normalized_base_url
            embeddings = self._runtime.langchain_openai_embeddings_cls(**langchain_embedding_kwargs)
        elif self._runtime.embedding_factory_fn is not None and client is not None:
            embedding_kwargs: Dict[str, Any] = {
                "provider": "openai",
                "model": self._llm_config.embedding_model,
                "client": client,
            }
            embeddings = self._runtime.embedding_factory_fn(**embedding_kwargs)
        else:
            raise RuntimeError("RAGAS embeddings runtime unavailable")

        return {"llm": llm, "embeddings": embeddings}

    # 在降级模式下用轻量规则计算可用分数，避免接口不可用。
    def _fallback_evaluate(self, sample: EvaluateSample, error_message: Optional[str]) -> EvaluateResult:
        answer_relevancy = self._jaccard(sample.answer, sample.question)
        faithfulness = self._jaccard(sample.answer, " ".join(sample.contexts)) if sample.contexts else None

        context_precision = None
        if sample.contexts:
            per_context_scores = [self._jaccard(sample.answer, context) for context in sample.contexts]
            # 过滤空分值，避免降级场景中 None 参与求和导致异常。
            valid_context_scores = [score for score in per_context_scores if score is not None]
            context_precision = (
                self._round(sum(valid_context_scores) / len(valid_context_scores))
                if valid_context_scores
                else None
            )

        context_recall = self._jaccard(sample.ground_truth, " ".join(sample.contexts)) if sample.contexts else None
        return self._build_result(
            sample_id=sample.id,
            metrics={
                "answer_relevancy": answer_relevancy,
                "faithfulness": faithfulness,
                "context_precision": context_precision,
                "context_recall": context_recall,
            },
            error_message=error_message,
        )

    # 组装统一返回结构并计算 overallScore。
    def _build_result(
        self,
        sample_id: int,
        metrics: Dict[str, Optional[float]],
        error_message: Optional[str],
    ) -> EvaluateResult:
        values = [
            metrics.get("answer_relevancy"),
            metrics.get("faithfulness"),
            metrics.get("context_precision"),
            metrics.get("context_recall"),
        ]
        non_null_values = [v for v in values if v is not None]
        overall = self._round(sum(non_null_values) / len(non_null_values)) if non_null_values else None

        return EvaluateResult(
            id=sample_id,
            answerRelevancy=metrics.get("answer_relevancy"),
            faithfulness=metrics.get("faithfulness"),
            contextPrecision=metrics.get("context_precision"),
            contextRecall=metrics.get("context_recall"),
            overallScore=overall,
            errorMessage=error_message,
        )

    @staticmethod
    def _safe_float(value: Any) -> Optional[float]:
        if value is None:
            return None
        try:
            numeric = float(value)
            if math.isnan(numeric):
                return None
            return RagasEvaluator._round(numeric)
        except (TypeError, ValueError):
            return None

    @staticmethod
    def _tokenize(text: str) -> List[str]:
        if not text:
            return []
        try:
            return _TOKEN_PATTERN.findall(text.lower())
        except MemoryError:  # pragma: no cover - 极端内存压力兜底
            logger.exception("Tokenization skipped due to memory pressure")
            return []

    @staticmethod
    def _jaccard(left: str, right: str) -> Optional[float]:
        left_tokens = set(RagasEvaluator._tokenize(left))
        right_tokens = set(RagasEvaluator._tokenize(right))
        if not left_tokens or not right_tokens:
            return None
        union = left_tokens | right_tokens
        if not union:
            return None
        return RagasEvaluator._round(len(left_tokens & right_tokens) / len(union))

    @staticmethod
    def _round(value: float) -> float:
        return round(value, 6)
