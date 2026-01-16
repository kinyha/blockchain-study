# Урок 3: Docker Compose

## Цели урока
После этого урока вы будете:
- Понимать, какую проблему решает Docker Compose
- Знать структуру и синтаксис docker-compose.yml
- Уметь описывать многоконтейнерные приложения
- Настраивать сети, volumes и зависимости между сервисами
- Управлять lifecycle приложения через docker-compose CLI

---

## 1. Зачем нужен Docker Compose

### Проблема: многоконтейнерные приложения

Реальное приложение редко состоит из одного контейнера. Типичный веб-сервис:

```
┌─────────────────────────────────────────────────────────────┐
│                       Приложение                             │
├─────────────┬─────────────┬─────────────┬──────────────────┤
│   Backend   │  Database   │    Cache    │   Message Queue  │
│  (Java/Go)  │ (PostgreSQL)│   (Redis)   │    (RabbitMQ)    │
└─────────────┴─────────────┴─────────────┴──────────────────┘
```

Без Docker Compose нужно запускать каждый контейнер отдельно:

```bash
# Создать сеть
docker network create myapp-network

# Запустить PostgreSQL
docker run -d \
  --name postgres \
  --network myapp-network \
  -e POSTGRES_PASSWORD=secret \
  -v pgdata:/var/lib/postgresql/data \
  postgres:16

# Запустить Redis
docker run -d \
  --name redis \
  --network myapp-network \
  redis:7

# Запустить приложение
docker run -d \
  --name app \
  --network myapp-network \
  -e DATABASE_URL=postgres://postgres:secret@postgres:5432/mydb \
  -e REDIS_URL=redis://redis:6379 \
  -p 8080:8080 \
  myapp:1.0
```

**Проблемы:**
- Много команд для запуска
- Сложно запомнить все параметры
- Нужно соблюдать порядок запуска
- Нет единого способа остановить всё

### Решение: Docker Compose

Docker Compose позволяет описать все сервисы в одном YAML-файле:

```yaml
# docker-compose.yml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DATABASE_URL=postgres://postgres:secret@postgres:5432/mydb
      - REDIS_URL=redis://redis:6379
    depends_on:
      - postgres
      - redis

  postgres:
    image: postgres:16
    environment:
      - POSTGRES_PASSWORD=secret
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: redis:7

volumes:
  pgdata:
```

**Запуск всего приложения одной командой:**
```bash
docker-compose up -d
```

**Остановка:**
```bash
docker-compose down
```

---

## 2. Установка Docker Compose

### Docker Desktop (Windows/macOS)

Docker Compose v2 уже включён в Docker Desktop. Проверьте:
```bash
docker compose version
# Docker Compose version v2.23.0
```

**Примечание:** В v2 команда `docker-compose` (с дефисом) заменена на `docker compose` (пробел). Оба варианта работают в Docker Desktop.

### Linux

```bash
# Установка через apt (Ubuntu/Debian)
sudo apt-get update
sudo apt-get install docker-compose-plugin

# Проверка
docker compose version
```

---

## 3. Структура docker-compose.yml

### Базовая структура

```yaml
# Версия спецификации (опционально в Compose v2+)
version: "3.8"

# Определение сервисов (контейнеров)
services:
  service1:
    # конфигурация сервиса 1
  service2:
    # конфигурация сервиса 2

# Определение сетей (опционально)
networks:
  mynetwork:
    driver: bridge

# Определение томов (опционально)
volumes:
  myvolume:
```

### Минимальный пример

```yaml
services:
  web:
    image: nginx:1.25
    ports:
      - "80:80"
```

Эквивалент команды:
```bash
docker run -d -p 80:80 nginx:1.25
```

---

## 4. Конфигурация сервисов

### image — использование готового образа

```yaml
services:
  postgres:
    image: postgres:16-alpine
```

### build — сборка образа из Dockerfile

```yaml
services:
  app:
    build: .                    # Dockerfile в текущей директории

  app-custom:
    build:
      context: ./backend        # Директория с Dockerfile
      dockerfile: Dockerfile.prod
      args:
        - APP_VERSION=1.0.0
```

### ports — проброс портов

```yaml
services:
  web:
    ports:
      - "8080:80"           # host:container
      - "443:443"
      - "127.0.0.1:3000:3000"  # Только localhost
```

### environment — переменные окружения

```yaml
services:
  app:
    environment:
      - DATABASE_URL=postgres://user:pass@db:5432/mydb
      - DEBUG=true
      - API_KEY                  # Берёт значение из окружения хоста

  app-map:
    environment:
      DATABASE_URL: postgres://user:pass@db:5432/mydb
      DEBUG: "true"
```

### env_file — переменные из файла

```yaml
services:
  app:
    env_file:
      - .env
      - .env.local
```

Файл `.env`:
```bash
DATABASE_URL=postgres://user:pass@db:5432/mydb
DEBUG=true
API_KEY=secret123
```

### volumes — монтирование томов

```yaml
services:
  postgres:
    volumes:
      # Named volume — данные сохраняются между перезапусками
      - pgdata:/var/lib/postgresql/data

      # Bind mount — синхронизация с хостом
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql

      # Read-only bind mount
      - ./config:/app/config:ro

volumes:
  pgdata:              # Объявление named volume
```

**Типы volumes:**

| Тип | Синтаксис | Использование |
|-----|-----------|---------------|
| Named volume | `volumename:/path` | Персистентные данные (БД) |
| Bind mount | `./host/path:/container/path` | Разработка, конфигурация |
| Anonymous | `/container/path` | Временные данные |

### depends_on — зависимости между сервисами

```yaml
services:
  app:
    depends_on:
      - postgres
      - redis

  postgres:
    image: postgres:16

  redis:
    image: redis:7
```

**Важно:** `depends_on` гарантирует только порядок запуска контейнеров, но не готовность сервисов внутри них!

```yaml
# Для проверки готовности используйте healthcheck
services:
  app:
    depends_on:
      postgres:
        condition: service_healthy

  postgres:
    image: postgres:16
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5
```

### networks — сетевая конфигурация

```yaml
services:
  frontend:
    networks:
      - frontend-net

  backend:
    networks:
      - frontend-net
      - backend-net

  database:
    networks:
      - backend-net

networks:
  frontend-net:
  backend-net:
```

В этом примере:
- `frontend` может общаться с `backend`
- `backend` может общаться с `frontend` и `database`
- `frontend` НЕ может общаться напрямую с `database`

### restart — политика перезапуска

```yaml
services:
  app:
    restart: unless-stopped

  worker:
    restart: always

  one-time-task:
    restart: "no"
```

| Политика | Описание |
|----------|----------|
| `no` | Не перезапускать (по умолчанию) |
| `always` | Всегда перезапускать |
| `on-failure` | Только при ошибке (exit code != 0) |
| `unless-stopped` | Всегда, кроме ручной остановки |

### healthcheck — проверка здоровья

```yaml
services:
  app:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
```

### command и entrypoint — переопределение запуска

```yaml
services:
  app:
    image: myapp:1.0
    command: ["--debug", "--port=9090"]

  worker:
    image: myapp:1.0
    entrypoint: ["java", "-jar", "worker.jar"]
    command: ["--queue=high-priority"]
```

### deploy — ограничения ресурсов

```yaml
services:
  app:
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 512M
        reservations:
          cpus: "0.25"
          memory: 256M
```

**Примечание:** Для использования в обычном Docker Compose (не Swarm) добавьте:
```bash
docker-compose --compatibility up -d
```

---

## 5. Сети в Docker Compose

### Сеть по умолчанию

Docker Compose автоматически создаёт сеть для всех сервисов:

```yaml
services:
  app:
    image: myapp:1.0

  postgres:
    image: postgres:16
```

При `docker-compose up`:
- Создаётся сеть `<project>_default` (например, `myapp_default`)
- Оба контейнера подключаются к этой сети
- Контейнеры доступны друг другу по имени сервиса

```java
// В приложении можно использовать имя сервиса как hostname
String jdbcUrl = "jdbc:postgresql://postgres:5432/mydb";
```

### DNS в Docker-сетях

Docker предоставляет встроенный DNS для контейнеров в одной сети:

```
┌────────────────────────────────────────────┐
│              Docker Network                 │
│                                             │
│  ┌─────────┐          ┌─────────┐          │
│  │   app   │──────────│ postgres │         │
│  └─────────┘          └─────────┘          │
│       │                    │                │
│       │    DNS: postgres   │                │
│       │────────────────────│                │
│       │    resolves to     │                │
│       │    172.18.0.3      │                │
│                                             │
└────────────────────────────────────────────┘
```

### Изоляция сетей

```yaml
services:
  # Публичная часть
  nginx:
    image: nginx
    ports:
      - "80:80"
    networks:
      - public

  # Приложение — связывает публичную и приватную сети
  app:
    build: .
    networks:
      - public
      - private

  # База данных — только приватная сеть
  postgres:
    image: postgres:16
    networks:
      - private

networks:
  public:
  private:
    internal: true    # Без доступа в интернет
```

---

## 6. Volumes: персистентность данных

### Проблема: данные теряются при удалении контейнера

```bash
docker-compose down     # Контейнер удалён
docker-compose up -d    # Новый контейнер — данные потеряны!
```

### Решение: Named Volumes

```yaml
services:
  postgres:
    image: postgres:16
    volumes:
      - pgdata:/var/lib/postgresql/data    # Named volume

volumes:
  pgdata:                                   # Объявление volume
```

Теперь:
```bash
docker-compose down     # Контейнер удалён, volume остаётся
docker-compose up -d    # Данные на месте!

docker-compose down -v  # Удалить И volumes (осторожно!)
```

### Bind Mounts для разработки

```yaml
services:
  app:
    build: .
    volumes:
      - ./src:/app/src                     # Изменения сразу видны в контейнере
      - ./config/app.yml:/app/config.yml:ro
```

### Управление volumes

```bash
# Список volumes
docker volume ls

# Информация о volume
docker volume inspect myapp_pgdata

# Удаление неиспользуемых volumes
docker volume prune

# Бэкап данных из volume
docker run --rm -v myapp_pgdata:/data -v $(pwd):/backup \
    alpine tar cvf /backup/pgdata-backup.tar /data
```

---

## 7. Docker Compose CLI

### Основные команды

```bash
# Запуск всех сервисов в фоне
docker compose up -d

# Запуск с пересборкой образов
docker compose up -d --build

# Запуск конкретных сервисов
docker compose up -d app postgres

# Остановка всех сервисов
docker compose stop

# Остановка и удаление контейнеров
docker compose down

# Остановка, удаление контейнеров И volumes
docker compose down -v

# Перезапуск
docker compose restart
docker compose restart app
```

### Просмотр состояния

```bash
# Статус сервисов
docker compose ps

# Логи всех сервисов
docker compose logs

# Логи конкретного сервиса (follow)
docker compose logs -f app

# Последние 100 строк логов
docker compose logs --tail=100 app
```

### Масштабирование

```bash
# Запустить 3 экземпляра сервиса
docker compose up -d --scale worker=3

# Проверить
docker compose ps
# NAME                IMAGE       STATUS
# myapp-worker-1      myapp:1.0   Up
# myapp-worker-2      myapp:1.0   Up
# myapp-worker-3      myapp:1.0   Up
```

**Важно:** При масштабировании нельзя использовать фиксированные порты:
```yaml
services:
  worker:
    # ports:
    #   - "8080:8080"    # Ошибка при scale > 1
    expose:
      - "8080"           # OK — порт внутри сети
```

### Выполнение команд

```bash
# Выполнить команду в работающем контейнере
docker compose exec app sh
docker compose exec postgres psql -U postgres

# Выполнить одноразовую команду (новый контейнер)
docker compose run --rm app npm test
docker compose run --rm app ./manage.py migrate
```

### Сборка образов

```bash
# Собрать все образы
docker compose build

# Собрать без кэша
docker compose build --no-cache

# Собрать конкретный сервис
docker compose build app
```

---

## 8. Полный пример: Blockchain Network

### Структура проекта

```
blockchain-study/
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml
├── src/
│   └── main/java/...
├── build.gradle.kts
└── gradlew
```

### docker-compose.yml

```yaml
# docker-compose.yml
# Blockchain Network с 3 нодами

services:
  # ==========================================================================
  # Node 1 — первая нода (miner)
  # ==========================================================================
  node1:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    container_name: blockchain-node1
    ports:
      - "8001:8080"
    environment:
      - NODE_ID=node-1
      - DIFFICULTY=4
      - SPRING_PROFILES_ACTIVE=miner
    networks:
      - blockchain-net
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/api/node/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 30s

  # ==========================================================================
  # Node 2 — вторая нода
  # ==========================================================================
  node2:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    container_name: blockchain-node2
    ports:
      - "8002:8080"
    environment:
      - NODE_ID=node-2
      - DIFFICULTY=4
      - PEERS=http://node1:8080
    networks:
      - blockchain-net
    depends_on:
      node1:
        condition: service_healthy

  # ==========================================================================
  # Node 3 — третья нода
  # ==========================================================================
  node3:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    container_name: blockchain-node3
    ports:
      - "8003:8080"
    environment:
      - NODE_ID=node-3
      - DIFFICULTY=4
      - PEERS=http://node1:8080,http://node2:8080
    networks:
      - blockchain-net
    depends_on:
      node1:
        condition: service_healthy
      node2:
        condition: service_started

networks:
  blockchain-net:
    name: blockchain-network
    driver: bridge
```

### Команды для работы

```bash
# Переход в директорию docker
cd docker

# Сборка и запуск
docker compose up -d --build

# Проверка статуса
docker compose ps

# Просмотр логов всех нод
docker compose logs -f

# Просмотр логов конкретной ноды
docker compose logs -f node1

# Тестирование API
curl http://localhost:8001/api/node/health
curl http://localhost:8001/api/blocks
curl http://localhost:8002/api/blocks/height

# Майнинг блока на node1
curl -X POST http://localhost:8001/api/mining/mine

# Остановка
docker compose down
```

---

## 9. Переменные окружения и .env файлы

### Приоритет переменных

Docker Compose использует переменные в следующем приоритете (от высшего к низшему):
1. Переменные окружения оболочки
2. Файл `.env` в директории с docker-compose.yml
3. Значения по умолчанию в `environment`

### Файл .env

```bash
# .env (в той же директории, что и docker-compose.yml)
POSTGRES_PASSWORD=mysecretpassword
APP_VERSION=1.2.3
NODE_COUNT=3
```

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16
    environment:
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}

  app:
    image: myapp:${APP_VERSION}
```

### Подстановка со значением по умолчанию

```yaml
services:
  app:
    image: myapp:${APP_VERSION:-latest}           # default: latest
    environment:
      - LOG_LEVEL=${LOG_LEVEL:-INFO}
      - DEBUG=${DEBUG:-false}
```

### Разные окружения

```bash
# .env.dev
DATABASE_URL=postgres://localhost:5432/dev
DEBUG=true

# .env.prod
DATABASE_URL=postgres://prod-server:5432/prod
DEBUG=false
```

```bash
# Запуск с конкретным файлом окружения
docker compose --env-file .env.dev up -d
docker compose --env-file .env.prod up -d
```

---

## 10. Типичные ошибки

### Ошибка: порт уже занят

```bash
Error: Bind for 0.0.0.0:8080 failed: port is already allocated
```

**Решение:**
```bash
# Найти что занимает порт
lsof -i :8080

# Использовать другой порт в docker-compose.yml
ports:
  - "8081:8080"
```

### Ошибка: сервис не может подключиться к другому сервису

**Симптом:** `Connection refused` или `Name not resolved`

**Причины и решения:**
1. Сервис ещё не запустился → используйте `depends_on` с `condition: service_healthy`
2. Неправильное имя хоста → используйте имя сервиса (не container_name)
3. Разные сети → проверьте конфигурацию `networks`

```yaml
services:
  app:
    depends_on:
      db:
        condition: service_healthy    # Ждать пока БД будет готова
    environment:
      - DATABASE_HOST=db              # Имя сервиса, не "postgres" или IP
```

### Ошибка: изменения в Dockerfile не применяются

**Причина:** Docker Compose кэширует образы.

**Решение:**
```bash
# Пересобрать образы
docker compose up -d --build

# Или принудительно без кэша
docker compose build --no-cache
docker compose up -d
```

### Ошибка: данные теряются после docker-compose down

**Причина:** `docker-compose down -v` удаляет volumes.

**Решение:** Не используйте `-v` если нужно сохранить данные.
```bash
docker compose down     # Сохраняет volumes
docker compose down -v  # УДАЛЯЕТ volumes!
```

---

## 11. Вопросы для самопроверки

1. **Какую основную проблему решает Docker Compose?**
   <details>
   <summary>Ответ</summary>
   Упрощает запуск многоконтейнерных приложений. Позволяет описать все сервисы, сети и volumes в одном YAML-файле и управлять ими одной командой.
   </details>

2. **Как сервисы в Docker Compose обращаются друг к другу по сети?**
   <details>
   <summary>Ответ</summary>
   По имени сервиса. Docker Compose создаёт сеть и настраивает DNS, где имя сервиса резолвится в IP контейнера. Например: `postgres://db:5432` где `db` — имя сервиса.
   </details>

3. **В чём разница между `image` и `build` в конфигурации сервиса?**
   <details>
   <summary>Ответ</summary>
   `image` использует готовый образ из registry. `build` собирает образ из Dockerfile. Можно использовать оба — тогда собранный образ получит указанное имя.
   </details>

4. **Что гарантирует `depends_on` и что НЕ гарантирует?**
   <details>
   <summary>Ответ</summary>
   Гарантирует порядок запуска контейнеров. НЕ гарантирует, что сервис внутри контейнера готов к работе. Для этого нужно использовать `depends_on` с `condition: service_healthy`.
   </details>

5. **Как сохранить данные PostgreSQL между перезапусками docker-compose?**
   <details>
   <summary>Ответ</summary>
   Использовать named volume:
   ```yaml
   services:
     postgres:
       volumes:
         - pgdata:/var/lib/postgresql/data
   volumes:
     pgdata:
   ```
   </details>

6. **Какая команда пересобирает образы перед запуском?**
   <details>
   <summary>Ответ</summary>
   `docker compose up -d --build`
   </details>

7. **Как запустить 5 экземпляров сервиса `worker`?**
   <details>
   <summary>Ответ</summary>
   `docker compose up -d --scale worker=5`
   </details>

8. **Как выполнить команду в работающем контейнере через docker-compose?**
   <details>
   <summary>Ответ</summary>
   `docker compose exec <service> <command>`, например: `docker compose exec postgres psql -U postgres`
   </details>

9. **Что произойдёт с volumes при `docker-compose down`? А при `docker-compose down -v`?**
   <details>
   <summary>Ответ</summary>
   `docker compose down` — volumes сохраняются, контейнеры и сети удаляются.
   `docker compose down -v` — удаляются и volumes (данные теряются!).
   </details>

10. **Как использовать разные настройки для dev и prod окружений?**
    <details>
    <summary>Ответ</summary>
    Создать разные .env файлы (.env.dev, .env.prod) и запускать с флагом --env-file:
    `docker compose --env-file .env.prod up -d`
    </details>

---

## Итоги урока

Вы изучили:
- ✅ Назначение Docker Compose для многоконтейнерных приложений
- ✅ Структуру и синтаксис docker-compose.yml
- ✅ Конфигурацию сервисов: image, build, ports, environment, volumes
- ✅ Настройку сетей и изоляции сервисов
- ✅ Работу с volumes для персистентности данных
- ✅ Команды docker-compose CLI
- ✅ Использование переменных окружения и .env файлов

**Следующий урок:** Практика — контейнеризация blockchain-приложения.
