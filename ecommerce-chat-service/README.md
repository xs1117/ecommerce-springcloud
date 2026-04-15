# ecommerce-chat-service

客服会话服务：

- `POST /api/chat/conversations/open`
- `GET /api/chat/conversations`
- `GET /api/chat/conversations/{id}/messages`
- `POST /api/chat/conversations/{id}/messages`
- `POST /api/chat/conversations/{id}/read`

## 本地启动依赖

- MySQL：`ecommerce_chat`
- Nacos：`localhost:8848`
- 网关：`ecommerce-gateway`

## 启动顺序建议

1. Nacos / MySQL / Redis / RabbitMQ
2. `ecommerce-merchant-service`
3. `ecommerce-chat-service`
4. `ecommerce-gateway`
5. `ecommerce-frontend`

