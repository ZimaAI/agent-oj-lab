from __future__ import annotations

from functools import lru_cache

from fastapi import FastAPI

from .evaluator import RagasEvaluator
from .schemas import EvaluateRequest, EvaluateResponse


# 创建 FastAPI 应用并暴露健康检查与评估接口。
app = FastAPI(title="RAGAS Evaluation Service", version="0.1.0")


@lru_cache(maxsize=1)
def get_evaluator() -> RagasEvaluator:
    # 缓存评估器，避免重复初始化依赖。
    return RagasEvaluator()


@app.get("/health")
def health() -> dict:
    # 返回服务健康状态。
    return {"status": "ok"}


@app.post("/evaluate", response_model=EvaluateResponse)
def evaluate(request: EvaluateRequest) -> EvaluateResponse:
    # 执行批量评估并返回标准化结果。
    degraded, results = get_evaluator().evaluate_batch(request.samples)
    return EvaluateResponse(degraded=degraded, results=results)
