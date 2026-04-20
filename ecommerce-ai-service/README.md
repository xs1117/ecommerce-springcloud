# ecommerce-ai-service

基于 Spring AI 的 AI 客服模块，支持：

- 一般咨询：LLM + Qdrant 向量检索（RAG）
- 业务操作：识别退货意图并生成待确认动作
- 二次确认：用户确认后才执行售后动作
- 微服务联动：调用 `ecommerce-chat-service` 的售后接口完成 `APPLY_RETURN`

## 关键接口

- `POST /api/ai/chat`

请求体示例：

```json
{
  "message": "帮我把这个订单退货，订单号 202604150001",
  "orderNo": "202604150001",
  "confirmationToken": null,
  "confirm": false
}
```

确认执行示例：

```json
{
  "message": "确认",
  "confirmationToken": "<token>",
  "confirm": true
}
```

## 主要配置

- `ai.model`：LLM 模型名称（默认 `MiniMax-M2.7`）
- `ai.api-key`：中转平台 API Key（也可通过 `MINIMAX_API_KEY` 配置，代码会自动按 `Bearer sk-xxxx` 发送）
- `ai.base-url`：中转平台 Base URL（默认 `https://api.whatai.cc`）
- `ai.chat-path`：OpenAI 兼容聊天路径（默认 `/v1/chat/completions`）
- `ai.group-id`：仅在直连 MiniMax 原生接口时使用（中转 OpenAI 兼容接口可留空）
- `ai.chat-timeout-millis`：AI 请求读取超时（默认 `20000` 毫秒）
- `ai.max-retry-attempts`：AI 请求最大重试次数（默认 `3`）
- `ai.retry-backoff-millis`：重试退避基准时长（默认 `350` 毫秒）
- `ai.chat-service-base-url`：网关地址（默认 `http://localhost:8080`）
- `ai.confirmation-ttl-seconds`：确认令牌有效期
- `ai.rag.vector-enabled`：是否启用 Qdrant 向量检索
- `ai.rag.index-on-startup`：是否在启动时自动重建索引
- `ai.rag.qdrant-url`：Qdrant 地址
- `ai.rag.qdrant-collection`：Qdrant 集合名
- `ai.rag.embedding-base-url`：Embedding 服务地址（可为空；为空时默认复用 `ai.base-url` / `MINIMAX_BASE_URL`）
- `ai.rag.knowledge-files`：知识库文件列表

知识库默认文件：`src/main/resources/rag/customer-service-knowledge.md`

