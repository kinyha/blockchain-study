# Урок 4: Практика — Контейнеризация Blockchain Node

## Цели урока
В этом уроке вы:
- Создадите Docker-образ для blockchain-приложения с нуля
- Настроите multi-stage build для оптимизации
- Запустите сеть из нескольких blockchain-нод
- Протестируете взаимодействие нод через REST API
- Отладите типичные проблемы контейнеризации

---

## 1. Обзор приложения

### Архитектура Blockchain Node

```
┌─────────────────────────────────────────────────────────────────┐
│                     Blockchain Node (Spring Boot)                │
├─────────────────────────────────────────────────────────────────┤
│  REST API                                                        │
│  ├── /api/blocks          — работа с блоками                    │
│  ├── /api/transactions    — создание транзакций                 │
│  ├── /api/wallet          — баланс и UTXOs                      │
│  ├── /api/mining          — майнинг блоков                      │
│  └── /api/node            — статус ноды и peers                 │
├─────────────────────────────────────────────────────────────────┤
│  Services                                                        │
│  ├── BlockchainService    — главный сервис                      │
│  ├── MiningService        — proof-of-work                       │
│  ├── TransactionService   — создание транзакций                 │
│  └── SyncService          — синхронизация с peers               │
├─────────────────────────────────────────────────────────────────┤
│  Core                                                            │
│  ├── Blockchain           — цепочка блоков                      │
│  ├── UTXOPool             — неизрасходованные выходы            │
│  └── Wallet               — ключи и подписи                     │
└─────────────────────────────────────────────────────────────────┘
```

### Требования к контейнеру

1. **Runtime:** Java 21
2. **Порт:** 8080
3. **Конфигурация через environment:**
   - `NODE_ID` — идентификатор ноды
   - `DIFFICULTY` — сложность майнинга
   - `PORT` — порт приложения (опционально)
4. **Health check:** `/api/node/health`

---

## 2. Шаг 1: Подготовка проекта

### Проверка сборки

Убедитесь, что проект собирается локально:

```bash
# Перейти в директорию проекта
cd blockchain-study

# Собрать JAR
./gradlew bootJar

# Проверить что JAR создан
ls -la build/libs/
# blockchain-study-0.0.1-SNAPSHOT.jar
```

### Проверка запуска

```bash
# Запустить локально
java -jar build/libs/blockchain-study-0.0.1-SNAPSHOT.jar

# В другом терминале — проверить API
curl http://localhost:8080/api/node/health
# OK
```

---

## 3. Шаг 2: Создание простого Dockerfile

### Наивный подход (для понимания проблем)

Создайте файл `Dockerfile` в корне проекта:

```dockerfile
# Dockerfile.simple — НЕ для production!
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Копируем всё
COPY . .

# Собираем
RUN ./gradlew bootJar --no-daemon

# Запускаем
CMD ["java", "-jar", "build/libs/blockchain-study-0.0.1-SNAPSHOT.jar"]
```

### Сборка и проверка размера

```bash
docker build -f Dockerfile.simple -t blockchain:simple .

docker images blockchain:simple
# REPOSITORY   TAG      SIZE
# blockchain   simple   ~850MB   ← Огромный!
```

### Проблемы этого подхода

| Проблема | Последствия |
|----------|-------------|
| Размер ~850MB | Долгий pull/push, больше места |
| JDK вместо JRE | Лишние 200MB (компилятор не нужен в runtime) |
| Исходники в образе | Утечка кода, увеличение размера |
| Gradle cache в образе | +200MB мусора |
| Root пользователь | Уязвимость безопасности |

---

## 4. Шаг 3: Multi-stage Dockerfile

### Оптимизированный Dockerfile

Создайте `docker/Dockerfile`:

```dockerfile
# =============================================================================
# Blockchain Node — Production Dockerfile
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Копируем файлы Gradle (для кэширования зависимостей)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Скачиваем зависимости (этот слой кэшируется)
RUN chmod +x gradlew && \
    ./gradlew dependencies --no-daemon || true

# Копируем исходники
COPY src src

# Собираем JAR
RUN ./gradlew bootJar --no-daemon

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Метаданные
LABEL maintainer="blockchain-study"
LABEL description="Blockchain Node for Docker/K8s learning"

# Создаём непривилегированного пользователя
RUN addgroup -g 1000 blockchain && \
    adduser -u 1000 -G blockchain -D blockchain

WORKDIR /app

# Копируем JAR из build stage
COPY --from=builder --chown=blockchain:blockchain \
    /build/build/libs/*.jar app.jar

# Переключаемся на непривилегированного пользователя
USER blockchain

# Порт приложения
EXPOSE 8080

# Настройки JVM для контейнеров
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/api/node/health || exit 1

# Точка входа
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### Сборка оптимизированного образа

```bash
# Сборка
docker build -f docker/Dockerfile -t blockchain:optimized .

# Сравнение размеров
docker images | grep blockchain
# REPOSITORY   TAG        SIZE
# blockchain   simple     ~850MB
# blockchain   optimized  ~210MB   ← В 4 раза меньше!
```

### Проверка работы

```bash
# Запуск
docker run -d --name node1 -p 8080:8080 blockchain:optimized

# Проверка health
docker ps
# STATUS: Up 30 seconds (healthy)

# Тест API
curl http://localhost:8080/api/node/health
# OK

curl http://localhost:8080/api/blocks
# [{"index":0,"timestamp":...,"hash":"..."}]

# Остановка
docker stop node1 && docker rm node1
```

---

## 5. Шаг 4: Настройка .dockerignore

Создайте `.dockerignore` в корне проекта:

```dockerignore
# =============================================================================
# .dockerignore — исключения из контекста сборки
# =============================================================================

# Build outputs (собираем внутри Docker)
build/
.gradle/
out/
bin/

# IDE
.idea/
*.iml
.vscode/
*.swp
*~

# Git
.git/
.gitignore
.gitattributes

# Tests (опционально — если не нужны в образе)
# src/test/

# Documentation
*.md
docs/
lessons/
lessons-docker-k8s/

# Docker files (не нужны внутри контекста)
Dockerfile*
docker-compose*.yml
.dockerignore

# Misc
*.log
.env.local
*.bak
```

### Проверка размера контекста

```bash
# До .dockerignore — отправляется всё
# После — только нужные файлы

# Проверить что отправляется (первые строки вывода build)
docker build -f docker/Dockerfile -t blockchain:test . 2>&1 | head -5
# Sending build context to Docker daemon  XXX MB
```

---

## 6. Шаг 5: Docker Compose для сети нод

### Создание docker-compose.yml

Создайте `docker/docker-compose.yml`:

```yaml
# =============================================================================
# Blockchain Network — 3 ноды
# =============================================================================

services:
  # ===========================================================================
  # Node 1 — первичная нода
  # ===========================================================================
  node1:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    container_name: blockchain-node1
    hostname: node1
    ports:
      - "8001:8080"
    environment:
      - NODE_ID=node-1
      - DIFFICULTY=4
    networks:
      - blockchain-net
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/api/node/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 30s

  # ===========================================================================
  # Node 2
  # ===========================================================================
  node2:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    container_name: blockchain-node2
    hostname: node2
    ports:
      - "8002:8080"
    environment:
      - NODE_ID=node-2
      - DIFFICULTY=4
    networks:
      - blockchain-net
    depends_on:
      node1:
        condition: service_healthy

  # ===========================================================================
  # Node 3
  # ===========================================================================
  node3:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    container_name: blockchain-node3
    hostname: node3
    ports:
      - "8003:8080"
    environment:
      - NODE_ID=node-3
      - DIFFICULTY=4
    networks:
      - blockchain-net
    depends_on:
      node1:
        condition: service_healthy

networks:
  blockchain-net:
    name: blockchain-network
    driver: bridge
```

### Запуск сети

```bash
# Перейти в директорию docker
cd docker

# Собрать и запустить
docker compose up -d --build

# Проверить статус
docker compose ps
```

Ожидаемый вывод:
```
NAME                IMAGE                STATUS                   PORTS
blockchain-node1    docker-node1         Up 30 seconds (healthy)  0.0.0.0:8001->8080/tcp
blockchain-node2    docker-node2         Up 20 seconds (healthy)  0.0.0.0:8002->8080/tcp
blockchain-node3    docker-node3         Up 20 seconds (healthy)  0.0.0.0:8003->8080/tcp
```

---

## 7. Шаг 6: Тестирование сети

### Проверка каждой ноды

```bash
# Health check
curl http://localhost:8001/api/node/health && echo ""
curl http://localhost:8002/api/node/health && echo ""
curl http://localhost:8003/api/node/health && echo ""

# Статус нод
curl http://localhost:8001/api/node/status | jq .
curl http://localhost:8002/api/node/status | jq .
```

### Майнинг блоков

```bash
# Майним блок на node1
curl -X POST http://localhost:8001/api/mining/mine | jq .

# Проверяем высоту цепочки на всех нодах
echo "Node 1:" && curl -s http://localhost:8001/api/blocks/height
echo "Node 2:" && curl -s http://localhost:8002/api/blocks/height
echo "Node 3:" && curl -s http://localhost:8003/api/blocks/height
```

### Проверка балансов

```bash
# Баланс на node1 (после майнинга genesis = 10 coins)
curl http://localhost:8001/api/wallet/balance
# 10.0

# Баланс на node2 (ещё не майнил)
curl http://localhost:8002/api/wallet/balance
# 0.0
```

### Полный тестовый сценарий

```bash
#!/bin/bash
# test-network.sh

echo "=== Blockchain Network Test ==="

echo -e "\n1. Checking node health..."
for port in 8001 8002 8003; do
    status=$(curl -s http://localhost:$port/api/node/health)
    echo "   Node on port $port: $status"
done

echo -e "\n2. Mining genesis block on node1..."
curl -s -X POST http://localhost:8001/api/mining/mine | jq -r '.hash'

echo -e "\n3. Checking balances..."
for port in 8001 8002 8003; do
    balance=$(curl -s http://localhost:$port/api/wallet/balance)
    echo "   Node $port balance: $balance"
done

echo -e "\n4. Mining more blocks..."
for i in {1..3}; do
    echo "   Mining block $i..."
    curl -s -X POST http://localhost:8001/api/mining/mine > /dev/null
done

echo -e "\n5. Final chain height:"
for port in 8001 8002 8003; do
    height=$(curl -s http://localhost:$port/api/blocks/height)
    echo "   Node $port height: $height"
done

echo -e "\n=== Test Complete ==="
```

---

## 8. Шаг 7: Просмотр логов и отладка

### Логи контейнеров

```bash
# Логи всех контейнеров
docker compose logs

# Логи конкретной ноды
docker compose logs node1

# Логи в реальном времени
docker compose logs -f node1

# Последние 50 строк
docker compose logs --tail=50 node1
```

### Вход в контейнер

```bash
# Интерактивный shell
docker compose exec node1 sh

# Внутри контейнера:
/ $ whoami
blockchain

/ $ ps aux
PID   USER       COMMAND
  1   blockchain java -XX:MaxRAMPercentage=75.0 ...

/ $ wget -qO- http://localhost:8080/api/node/health
OK

/ $ exit
```

### Проверка сети между контейнерами

```bash
# Из node1 обратиться к node2
docker compose exec node1 wget -qO- http://node2:8080/api/node/health
# OK

# Проверить DNS
docker compose exec node1 nslookup node2
# Name:      node2
# Address 1: 172.20.0.3 node2.blockchain-network
```

### Проверка ресурсов

```bash
# Использование ресурсов контейнерами
docker stats --no-stream

# CONTAINER ID   NAME               CPU %   MEM USAGE / LIMIT
# abc123         blockchain-node1   0.50%   256MiB / 512MiB
# def456         blockchain-node2   0.30%   245MiB / 512MiB
# ghi789         blockchain-node3   0.25%   240MiB / 512MiB
```

---

## 9. Типичные проблемы и решения

### Проблема: Сборка падает с "permission denied" на gradlew

```bash
/bin/sh: ./gradlew: Permission denied
```

**Решение:** Добавьте `chmod +x gradlew` в Dockerfile:
```dockerfile
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
```

### Проблема: Health check не проходит

```bash
STATUS: Up 30 seconds (unhealthy)
```

**Диагностика:**
```bash
# Проверить логи
docker compose logs node1

# Проверить health check вручную
docker compose exec node1 wget -qO- http://localhost:8080/api/node/health
```

**Возможные причины:**
1. Приложение ещё стартует → увеличьте `start_period`
2. Неправильный URL health check → проверьте endpoint
3. Приложение упало → смотрите логи

### Проблема: Нода не видит другие ноды

**Проверка:**
```bash
# Проверить что ноды в одной сети
docker network inspect blockchain-network

# Проверить DNS
docker compose exec node1 nslookup node2
```

### Проблема: Изменения кода не применяются

**Решение:**
```bash
# Пересобрать образы
docker compose up -d --build

# Или принудительно без кэша
docker compose build --no-cache
docker compose up -d
```

---

## 10. Финальный чеклист

### Структура проекта

```
blockchain-study/
├── docker/
│   ├── Dockerfile              ✓ Multi-stage build
│   └── docker-compose.yml      ✓ 3 ноды
├── src/
│   └── main/java/...
├── build.gradle.kts
├── gradlew
├── gradle/
│   └── wrapper/
└── .dockerignore               ✓ Оптимизация контекста
```

### Команды для работы

```bash
# Сборка и запуск
cd docker
docker compose up -d --build

# Проверка статуса
docker compose ps

# Тестирование
curl http://localhost:8001/api/node/health
curl -X POST http://localhost:8001/api/mining/mine

# Логи
docker compose logs -f

# Остановка
docker compose down
```

### Проверка качества образа

| Критерий | Проверка | Ожидание |
|----------|----------|----------|
| Размер образа | `docker images blockchain` | < 250MB |
| Non-root user | `docker exec ... whoami` | `blockchain` |
| Health check | `docker ps` | `(healthy)` |
| Кэширование | Повторная сборка | < 30 сек |

---

## 11. Вопросы для самопроверки

1. **Почему multi-stage build уменьшает размер образа?**
   <details>
   <summary>Ответ</summary>
   В финальный образ попадает только результат сборки (JAR), без JDK, исходников, Gradle и кэша зависимостей. Build stage отбрасывается.
   </details>

2. **Зачем копировать build.gradle.kts отдельно от исходников?**
   <details>
   <summary>Ответ</summary>
   Для оптимизации кэширования. Зависимости меняются редко, поэтому их скачивание кэшируется. При изменении только исходников не нужно заново скачивать зависимости.
   </details>

3. **Как контейнеры в Docker Compose находят друг друга по сети?**
   <details>
   <summary>Ответ</summary>
   Docker Compose создаёт сеть и настраивает DNS. Контейнеры обращаются друг к другу по имени сервиса (node1, node2), которое резолвится во внутренний IP.
   </details>

4. **Что произойдёт если убрать `depends_on` из node2?**
   <details>
   <summary>Ответ</summary>
   Node2 может запуститься раньше node1 или одновременно. Если node2 пытается подключиться к node1 при старте — получит ошибку connection refused.
   </details>

5. **Почему используется alpine-based образ для runtime?**
   <details>
   <summary>Ответ</summary>
   Alpine Linux очень маленький (~5MB), что уменьшает размер финального образа. Для Java runtime достаточно минимальной ОС.
   </details>

6. **Как проверить что health check работает?**
   <details>
   <summary>Ответ</summary>
   `docker ps` покажет статус `(healthy)` или `(unhealthy)`. Также можно проверить вручную: `docker exec container wget -qO- http://localhost:8080/api/node/health`
   </details>

7. **Зачем нужен JAVA_OPTS с `-XX:MaxRAMPercentage=75.0`?**
   <details>
   <summary>Ответ</summary>
   JVM должен знать лимиты памяти контейнера. MaxRAMPercentage указывает использовать 75% доступной памяти, оставляя 25% для ОС и off-heap.
   </details>

---

## Итоги урока

Вы научились:
- ✅ Создавать Docker-образ для Java/Spring Boot приложения
- ✅ Оптимизировать размер образа с multi-stage build
- ✅ Настраивать кэширование слоёв для быстрой пересборки
- ✅ Запускать сеть из нескольких контейнеров с Docker Compose
- ✅ Тестировать и отлаживать контейнеризированное приложение

**Следующий урок:** Production Ready — безопасность, JVM в контейнерах, graceful shutdown.
