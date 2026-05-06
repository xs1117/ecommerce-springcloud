# E-commerce-Microservices-System

一个覆盖用户、商家、管理员三类角色，并打通订单、售后与 AI 客服闭环的电商微服务系统。

当前阶段已完成：
- Vue 前端登录页与更完整的商城主页
- 登录已拆分到独立的用户微服务
- 新增商家微服务（入驻申请、管理员审核、建店、上架商品）
- Spring Cloud Gateway 网关转发
- Dashboard 服务只负责商城主页与搜索
- AI Service 保持不动

## 模块说明

- `ecommerce-gateway`：统一入口，转发 `/api/user/**`、`/api/admin/**`、`/api/merchant/**`、`/api/home/**`、`/api/ai/**`
- `ecommerce-user-service`：用户微服务，负责注册、登录、认证、权限、积分会员、管理员隔离登录
- `ecommerce-merchant-service`：商家微服务，负责商家申请、审核、店铺、商品管理
- `ecommerce-dashboard`：商城主页服务，只负责首页与搜索
- `ecommerce-ai-service`：AI 客服接口（当前保持原样）
- `ecommerce-frontend`：Vue 3 页面

## 数据库

当前有两个业务域：

- `ecommerce_user`：用户微服务数据库
- `ecommerce_merchant`：商家微服务数据库
- `ecommerce`：商城主页数据库（当前主页服务已不依赖数据库，可先不建）

建议先创建用户数据库：

- `CREATE DATABASE ecommerce_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
- `CREATE DATABASE ecommerce_merchant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`

`ecommerce-user-service/src/main/resources/application.yml` 默认连接：
- 地址：`localhost:3306/ecommerce_user`
- 用户名：`root`
- 密码：`736100`

默认账号：
- 用户端：`user / 123456`
- 管理端：`admin / 123456`

## Redis Cluster（本地学习）

项目已提供 `docker/redis-cluster/docker-compose.yml`，可直接启动本地 6 节点集群（7000-7005）：

```powershell
docker compose -f docker/redis-cluster/docker-compose.yml up -d
docker exec -it redis-cluster-local redis-cli -p 7000 cluster nodes
```

## Qdrant 向量数据库（AI RAG 联调）

项目已提供 Qdrant 单节点配置：`docker/qdrant/docker-compose.yml`

```powershell
docker compose -f docker/qdrant/docker-compose.yml up -d
curl http://localhost:6333/healthz
```

如果你要联调 `ecommerce-ai-service` 的向量检索，请先启动它。

## Nacos 配置中心（本地开发）

仓库里已经补了一份可直接导入的配置模板：`nacos-config/`

建议使用：
- Namespace：`dev`
- Group：`ECOMMERCE_DEV`

建议的 DataId：
- `ecommerce-common.yaml`
- `ecommerce-gateway.yaml`
- `ecommerce-user-service.yaml`
- `ecommerce-merchant-service.yaml`
- `ecommerce-dashboard.yaml`
- `ecommerce-ai-service.yaml`

导入顺序建议：
1. 先导入 `ecommerce-common.yaml`
2. 再导入各服务专属配置
3. 最后启动各个微服务

> 当前各服务本地 `application.yml` 仍保留兜底值；Nacos 配置用于覆盖这些默认值。

## 本地启动

### 1) 启动基础设施

先启动 MySQL、Nacos、Redis Cluster 和 Qdrant。

Redis Cluster：

```powershell
docker compose -f .\docker\redis-cluster\docker-compose.yml up -d
docker exec redis-cluster-local redis-cli -p 7000 cluster info
```

Qdrant：

```powershell
docker compose -f .\docker\qdrant\docker-compose.yml up -d
curl http://localhost:6333/healthz
```

建议先确认：
- `cluster_state:ok`
- `cluster_slots_assigned:16384`

### 2) 导入 Nacos 配置

打开 Nacos 控制台，把 `nacos-config/` 里的 YAML 模板导入对应的 DataId。

推荐顺序：
- `ecommerce-common.yaml`
- `ecommerce-user-service.yaml`
- `ecommerce-merchant-service.yaml`
- `ecommerce-dashboard.yaml`
- `ecommerce-ai-service.yaml`
- `ecommerce-gateway.yaml`

### 3) 启动后端服务

在项目根目录执行：

```powershell
mvn -pl ecommerce-user-service spring-boot:run
mvn -pl ecommerce-merchant-service spring-boot:run
mvn -pl ecommerce-dashboard spring-boot:run
mvn -pl ecommerce-gateway spring-boot:run
```

> 这一步当前不需要改动 `ecommerce-ai-service`。

建议启动顺序：
1. `ecommerce-user-service`
2. `ecommerce-merchant-service`
3. `ecommerce-dashboard`
4. `ecommerce-ai-service`
5. `ecommerce-gateway`

启动后访问：
- 用户登录：`http://localhost:5173/login`
- 主页：`http://localhost:5173/home`

登录接口前缀：
- 用户登录：`/api/user/auth/login`
- 用户注册：`/api/user/auth/register`
- 管理员登录：`/api/admin/auth/login`

商家接口前缀：
- 用户申请商家：`POST /api/merchant/applications/apply`
- 用户查看申请：`GET /api/merchant/applications/me`
- 管理员审核：`POST /api/admin/merchant/applications/{id}/review`
- 商家建店：`POST /api/merchant/stores`
- 商家上架商品：`POST /api/merchant/products`
- 热门商品（公开）：`GET /api/merchant/products/hot`

### 4) 启动前端

```powershell
Set-Location .\ecommerce-frontend
npm install
npm run dev
```

浏览器访问：`http://localhost:5173`

## 主页内容

- 搜索框
- 商品分类
- 广告位
- 热门搜索
- 推荐商品
- 商城公告

## 后续扩展建议

- 商品表、订单表、购物车表
- Redis 做登录态和首页缓存
- JWT / AOP / 线程池 / MyBatis / JPA 混合落地
- AI 服务后续再接你自己的大模型 Key

