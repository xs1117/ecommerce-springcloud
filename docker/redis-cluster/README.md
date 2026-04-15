# Local Redis Cluster (Learning)

This folder starts a lightweight local Redis Cluster that exposes six nodes on ports `7000-7005`.

## Start

```powershell
# from project root:
docker compose -f docker/redis-cluster/docker-compose.yml up -d

# or from docker/redis-cluster folder:
docker compose up -d
```

## Verify

```powershell
docker exec -it redis-cluster-local redis-cli -p 7000 cluster nodes
```

## Stop

```powershell
# from project root:
docker compose -f docker/redis-cluster/docker-compose.yml down

# or from docker/redis-cluster folder:
docker compose down
```

## Restart

```powershell
# 重启
docker compose -f "C:\Users\zeihai\Desktop\code\ecommerce-springcloud\docker\redis-cluster\docker-compose.yml" up -d
docker exec redis-cluster-local redis-cli -p 7000 cluster info

# 删除并重启
# 在项目根目录执行
docker compose -f "C:\Users\zeihai\Desktop\code\ecommerce-springcloud\docker\redis-cluster\docker-compose.yml" down -v
docker rm -f redis-cluster-local
docker compose -f "C:\Users\zeihai\Desktop\code\ecommerce-springcloud\docker\redis-cluster\docker-compose.yml" up -d

# 验证
docker exec redis-cluster-local redis-cli -p 7000 cluster info
docker exec redis-cluster-local redis-cli -p 7000 cluster slots
```

## Troubleshooting

If logs show `Could not connect to Redis at 0.0.0.0:7000`, cluster bootstrap is using an invalid target address.

Run a clean restart after config changes:

```powershell
docker compose -f docker/redis-cluster/docker-compose.yml down -v
docker rm -f redis-cluster-local
docker compose -f docker/redis-cluster/docker-compose.yml up -d
docker logs -f redis-cluster-local
```

Expected check:

```powershell
docker exec -it redis-cluster-local redis-cli -p 7000 cluster info
```

