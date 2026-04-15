# RabbitMQ Local

## Start

```powershell
# from project root
docker compose -f docker/rabbitmq/docker-compose.yml up -d

# or from docker/rabbitmq folder
docker compose up -d
```

## Stop

```powershell
# from project root
docker compose -f docker/rabbitmq/docker-compose.yml down

# or from docker/rabbitmq folder
docker compose down
```

## Management Console

- URL: http://localhost:15672
- Username: `ecommerce`
- Password: `ecommerce123`

## Health Check

```powershell
docker exec ecommerce-rabbitmq rabbitmq-diagnostics ping
docker exec ecommerce-rabbitmq rabbitmqctl list_queues
```

