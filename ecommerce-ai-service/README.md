# ecommerce-ai-service

基于 Spring AI 的 AI 客服模块，支持：

- 一般咨询：LLM + 本地知识库检索（RAG）
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

- `ai.model`：LLM 模型名称
- `ai.api-key`：OpenAI API Key
- `ai.chat-service-base-url`：网关地址（默认 `http://localhost:8080`）
- `ai.confirmation-ttl-seconds`：确认令牌有效期
- `ai.rag.knowledge-files`：知识库文件列表

知识库默认文件：`src/main/resources/rag/customer-service-knowledge.md`

