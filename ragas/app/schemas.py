from __future__ import annotations

from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field


# 定义单条评估样本请求结构。
class EvaluateSample(BaseModel):
    id: int
    question: str
    answer: str
    ground_truth: str = Field(alias="groundTruth")
    contexts: List[str]

    model_config = ConfigDict(populate_by_name=True)


# 定义批量评估请求结构。
class EvaluateRequest(BaseModel):
    samples: List[EvaluateSample]


# 定义单条评估结果返回结构。
class EvaluateResult(BaseModel):
    id: int
    answer_relevancy: Optional[float] = Field(default=None, alias="answerRelevancy")
    faithfulness: Optional[float] = None
    context_precision: Optional[float] = Field(default=None, alias="contextPrecision")
    context_recall: Optional[float] = Field(default=None, alias="contextRecall")
    overall_score: Optional[float] = Field(default=None, alias="overallScore")
    error_message: Optional[str] = Field(default=None, alias="errorMessage")

    model_config = ConfigDict(populate_by_name=True)


# 定义批量评估接口返回结构。
class EvaluateResponse(BaseModel):
    degraded: bool
    results: List[EvaluateResult]
