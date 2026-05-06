# RAGAS 评估服务（Python）

本目录提供一个独立可运行的 RAGAS 评估服务，使用 FastAPI 实现。

- 接口：`POST /evaluate`
- 输入：批量样本（`id/question/answer/groundTruth/contexts`）
- 输出：每条样本的四项指标 + `overallScore` + `errorMessage`
- 兼容降级：若未安装 `ragas`/`datasets` 或运行失败，会自动降级并返回 `degraded=true`，接口仍可用

## 目录结构

```text
ragas/
  app/
    __init__.py
    main.py
    schemas.py
    evaluator.py
  tests/
    test_evaluate_api.py
    test_evaluator.py
  requirements.txt
  README.md
```

## 安装依赖

```bash
pip install -r requirements.txt
```

如需启用真实 RAGAS 指标（非降级模式），额外安装：

```bash
pip install ragas datasets
```

## 大模型 API 配置

启用真实 RAGAS 评估时，请在 `ragas/config.json` 配置 OpenAI 兼容参数（配置文件优先）：

```json
{
  "openai": {
    "api_key": "sk-***",
    "base_url": "https://api.openai.com/v1",
    "llm_model": "gpt-4o-mini",
    "embedding_model": "text-embedding-3-small"
  }
}
```

字段说明：

- `openai.api_key`：OpenAI 兼容 API Key（必填）
- `openai.base_url`：OpenAI 兼容接口地址（可选）
- `openai.llm_model`：评估使用的对话模型（默认 `gpt-4o-mini`）
- `openai.embedding_model`：评估使用的向量模型（默认 `text-embedding-3-small`）

兼容说明：

- 若 `ragas/config.json` 缺失或字段为空，服务会回退读取环境变量（`RAGAS_OPENAI_*`/`OPENAI_*`）

## 启动服务

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

健康检查：

```bash
curl http://127.0.0.1:8000/health
```

## 接口示例

### 请求

```bash
curl -X POST "http://127.0.0.1:8000/evaluate" \
  -H "Content-Type: application/json" \
  -d '{
    "samples": [
      {
        "id": 1,
        "question": "什么是二分查找？",
        "answer": "二分查找是有序数组上的对半查找方法。",
        "groundTruth": "二分查找在有序序列中每次将搜索区间缩小一半。",
        "contexts": ["二分查找适用于有序数组", "时间复杂度 O(log n)"]
      }
    ]
  }'
```

### 返回

```json
{
  "degraded": true,
  "results": [
    {
      "id": 1,
      "answerRelevancy": 0.3,
      "faithfulness": 0.4,
      "contextPrecision": 0.35,
      "contextRecall": 0.2,
      "overallScore": 0.3125,
      "errorMessage": "RAGAS dependencies unavailable: ..."
    }
  ]
}
```

## 运行测试

```bash
pytest -q
```
