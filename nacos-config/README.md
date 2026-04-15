# Nacos 配置中心模板

> 这里放的是本地开发环境可直接导入 Nacos 的配置模板。
> 建议统一使用：
> - Namespace：`dev`
> - Group：`ECOMMERCE_DEV`

## 建议的 DataId

- `ecommerce-common.yaml`
- `ecommerce-gateway.yaml`
- `ecommerce-user-service.yaml`
- `ecommerce-merchant-service.yaml`
- `ecommerce-cart-service.yaml`
- `ecommerce-inventory-service.yaml`
- `ecommerce-order-service.yaml`
- `ecommerce-payment-service.yaml`
- `ecommerce-dashboard.yaml`
- `ecommerce-ai-service.yaml`

## 导入顺序建议

1. 先导入 `ecommerce-common.yaml`
2. 再导入各服务专属配置
3. 启动服务时，先启动 Nacos，再启动后端服务

## 使用原则

- 本地 `application.yml` 保留最小兜底配置，避免 Nacos 不可用时服务无法启动。
- Nacos 里的配置用于覆盖本地默认值，方便统一管理。
- 网关路由、数据库连接、Redis/RabbitMQ、上传目录、业务开关等都适合放进 Nacos。


