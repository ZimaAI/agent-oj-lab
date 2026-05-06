from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, List
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.evaluator import RagasEvaluator, _RagasRuntime
from app.schemas import EvaluateSample


class _FakeDataset:
    @staticmethod
    def from_dict(payload: Dict[str, List[Any]]) -> Dict[str, List[Any]]:
        return payload


class _FakeRagasResult:
    def __init__(self, scores: List[Dict[str, Any]]) -> None:
        self.scores = scores


def _build_sample() -> EvaluateSample:
    return EvaluateSample(
        id=1,
        question="q",
        answer="a",
        ground_truth="gt",
        contexts=["c1", "c2"],
    )


def test_evaluate_with_ragas_disables_raise_exceptions() -> None:
    captured_kwargs: Dict[str, Any] = {}

    def fake_evaluate_fn(**kwargs: Any) -> _FakeRagasResult:
        captured_kwargs.update(kwargs)
        return _FakeRagasResult(
            scores=[
                {
                    "answer_relevancy": 0.1,
                    "faithfulness": 0.2,
                    "context_precision": 0.3,
                    "context_recall": 0.4,
                }
            ]
        )

    evaluator = RagasEvaluator.__new__(RagasEvaluator)
    evaluator._runtime = _RagasRuntime(
        dataset_cls=_FakeDataset,
        evaluate_fn=fake_evaluate_fn,
        metrics=("m1", "m2", "m3", "m4"),
    )
    evaluator._llm_config = None
    evaluator._build_ragas_runtime_kwargs = lambda: {}

    rows = evaluator._evaluate_with_ragas([_build_sample()])

    assert captured_kwargs["raise_exceptions"] is False
    assert rows == [
        {
            "answer_relevancy": 0.1,
            "faithfulness": 0.2,
            "context_precision": 0.3,
            "context_recall": 0.4,
        }
    ]


def test_evaluate_batch_keeps_other_metrics_when_one_metric_fails() -> None:
    class _FakeOutputParserException(RuntimeError):
        pass

    per_metric_scores = {
        "answer_relevancy": 0.1,
        "context_precision": 0.3,
        "context_recall": 0.4,
    }

    def fake_evaluate_fn(**kwargs: Any) -> _FakeRagasResult:
        metrics = kwargs["metrics"]
        if len(metrics) > 1:
            raise _FakeOutputParserException("faithfulness parse failed")

        metric_name = metrics[0]
        if metric_name == "faithfulness":
            raise _FakeOutputParserException("faithfulness parse failed")

        return _FakeRagasResult(scores=[{metric_name: per_metric_scores[metric_name]}])

    evaluator = RagasEvaluator.__new__(RagasEvaluator)
    evaluator._runtime = _RagasRuntime(
        dataset_cls=_FakeDataset,
        evaluate_fn=fake_evaluate_fn,
        metrics=("answer_relevancy", "faithfulness", "context_precision", "context_recall"),
    )
    evaluator._llm_config = None
    evaluator._init_error = None
    evaluator._build_ragas_runtime_kwargs = lambda: {}

    degraded, results = evaluator.evaluate_batch([_build_sample()])

    assert degraded is False
    assert len(results) == 1
    assert results[0].answer_relevancy == 0.1
    assert results[0].faithfulness is None
    assert results[0].context_precision == 0.3
    assert results[0].context_recall == 0.4
    assert results[0].overall_score == 0.266667
    assert results[0].error_message is None
