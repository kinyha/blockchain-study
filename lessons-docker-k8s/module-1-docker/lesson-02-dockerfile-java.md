# Урок 2: Dockerfile для Java

## Цели урока
После этого урока вы будете:
- Понимать структуру и синтаксис Dockerfile
- Знать все основные инструкции Dockerfile
- Уметь создавать эффективные образы для Java-приложений
- Применять multi-stage builds для оптимизации размера
- Понимать как работает кэширование слоёв

---

## 1. Что такое Dockerfile

**Dockerfile** — это текстовый файл с инструкциями для автоматической сборки Docker-образа.

```
Dockerfile ──▶ docker build ──▶ Docker Image ──▶ docker run ──▶ Container
```

**Почему не собирать образ вручную?**

Можно было бы:
1. Запустить базовый контейнер
2. Установить зависимости вручную
3. Скопировать файлы
4. Сохранить как образ (`docker commit`)

Но это:
- Не воспроизводимо (забыли шаг — другой результат)
- Не версионируется (нельзя отследить изменения в Git)
- Не автоматизируется (нельзя встроить в CI/CD)

**Dockerfile решает все эти проблемы** — это декларативное описание образа.

---

## 2. Структура Dockerfile

### Базовый синтаксис

```dockerfile
# Комментарий
ИНСТРУКЦИЯ аргументы
```

- Инструкции обычно пишут ЗАГЛАВНЫМИ (конвенция, не требование)
- Каждая инструкция создаёт новый слой в образе
- Порядок инструкций важен для кэширования

### Минимальный Dockerfile для Java

```dockerfile
# Базовый образ с JRE
FROM eclipse-temurin:21-jre

# Рабочая директория
WORKDIR /app

# Копирование JAR-файла
COPY target/myapp.jar app.jar

# Команда запуска
CMD ["java", "-jar", "app.jar"]
```

**Сборка и запуск:**
```bash
# Сборка образа
docker build -t myapp:1.0 .

# Запуск контейнера
docker run -p 8080:8080 myapp:1.0
```

---

## 3. Основные инструкции Dockerfile

### FROM — базовый образ

```dockerfile
FROM image:tag
```

Каждый Dockerfile начинается с `FROM`. Указывает базовый образ, на основе которого строится ваш.

```dockerfile
# Официальные образы
FROM eclipse-temurin:21-jre-alpine    # Java 21, минимальный размер
FROM eclipse-temurin:21-jdk           # Java 21 с компилятором
FROM ubuntu:22.04                      # Ubuntu
FROM alpine:3.19                       # Минимальный Linux (~5MB)

# Специальный образ "с нуля"
FROM scratch                           # Пустой образ (для статических бинарников)
```

**Выбор базового образа для Java:**

| Образ | Размер | Когда использовать |
|-------|--------|-------------------|
| `eclipse-temurin:21-jdk` | ~450MB | Для сборки (нужен javac) |
| `eclipse-temurin:21-jre` | ~270MB | Для запуска (только JRE) |
| `eclipse-temurin:21-jre-alpine` | ~190MB | Production (минимальный размер) |
| `amazoncorretto:21-alpine` | ~190MB | AWS-оптимизированный |
| `gcr.io/distroless/java21` | ~220MB | Максимальная безопасность |

### WORKDIR — рабочая директория

```dockerfile
WORKDIR /path
```

Устанавливает рабочую директорию для последующих инструкций (`RUN`, `CMD`, `COPY`, `ADD`).

```dockerfile
WORKDIR /app

# Теперь все пути относительно /app
COPY myapp.jar .           # Копирует в /app/myapp.jar
RUN ls -la                 # Выполняется в /app
CMD ["java", "-jar", "app.jar"]  # Запускается в /app
```

**Важно:** Если директория не существует, `WORKDIR` создаст её автоматически.

### COPY — копирование файлов

```dockerfile
COPY источник назначение
COPY --chown=user:group источник назначение
```

Копирует файлы из контекста сборки (директория с Dockerfile) в образ.

```dockerfile
# Копировать один файл
COPY target/app.jar /app/app.jar

# Копировать с переименованием
COPY target/myapp-1.0.0.jar app.jar

# Копировать несколько файлов
COPY file1.txt file2.txt /app/

# Копировать директорию
COPY config/ /app/config/

# Копировать с маской
COPY *.jar /app/

# Копировать с изменением владельца
COPY --chown=appuser:appgroup app.jar /app/
```

### ADD — расширенное копирование

```dockerfile
ADD источник назначение
```

Похож на `COPY`, но с дополнительными возможностями:
- Автоматически распаковывает архивы (tar, gzip, bzip2)
- Может скачивать файлы по URL

```dockerfile
# Распаковка архива
ADD app.tar.gz /app/       # Автоматически распакует

# Скачивание (не рекомендуется - лучше RUN curl)
ADD https://example.com/file.txt /app/
```

**Рекомендация:** Используйте `COPY` везде, кроме случаев когда нужна распаковка архивов.

### RUN — выполнение команд при сборке

```dockerfile
RUN команда
RUN ["executable", "param1", "param2"]
```

Выполняет команды на этапе сборки образа. Результат сохраняется в новом слое.

```dockerfile
# Shell-форма (выполняется через /bin/sh -c)
RUN apt-get update && apt-get install -y curl

# Exec-форма (выполняется напрямую)
RUN ["apt-get", "update"]

# Многострочная команда (для читаемости)
RUN apt-get update && \
    apt-get install -y \
        curl \
        wget \
        vim && \
    rm -rf /var/lib/apt/lists/*
```

**Важно:** Каждый `RUN` создаёт новый слой. Объединяйте связанные команды через `&&`.

```dockerfile
# Плохо — 3 слоя, apt cache остаётся
RUN apt-get update
RUN apt-get install -y curl
RUN rm -rf /var/lib/apt/lists/*

# Хорошо — 1 слой, чистый образ
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*
```

### ENV — переменные окружения

```dockerfile
ENV KEY=value
ENV KEY1=value1 KEY2=value2
```

Устанавливает переменные окружения, доступные при сборке и в запущенном контейнере.

```dockerfile
# Установка переменных
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV APP_HOME=/app
ENV APP_VERSION=1.0.0

# Использование переменных
WORKDIR ${APP_HOME}
RUN echo "Version: ${APP_VERSION}"
```

**Переопределение при запуске:**
```bash
docker run -e JAVA_OPTS="-Xmx1g" myapp:1.0
```

### ARG — аргументы сборки

```dockerfile
ARG NAME=default_value
```

Переменные, доступные только во время сборки (не в запущенном контейнере).

```dockerfile
# Определение с default значением
ARG JAR_FILE=app.jar
ARG APP_VERSION=1.0.0

# Использование
COPY target/${JAR_FILE} app.jar
LABEL version=${APP_VERSION}
```

**Передача при сборке:**
```bash
docker build --build-arg JAR_FILE=myapp-2.0.jar --build-arg APP_VERSION=2.0.0 -t myapp:2.0 .
```

**Разница ENV и ARG:**

| Характеристика | ARG | ENV |
|---------------|-----|-----|
| Доступен при сборке | ✅ | ✅ |
| Доступен в контейнере | ❌ | ✅ |
| Можно переопределить при `build` | ✅ | ❌ |
| Можно переопределить при `run` | ❌ | ✅ |

### EXPOSE — документация портов

```dockerfile
EXPOSE port
EXPOSE port/protocol
```

**Важно:** `EXPOSE` не открывает порт! Это только документация — указание, какие порты использует приложение.

```dockerfile
EXPOSE 8080
EXPOSE 8080/tcp
EXPOSE 8443/tcp
```

Реальный проброс порта происходит при `docker run -p`:
```bash
docker run -p 8080:8080 myapp:1.0
```

### CMD — команда по умолчанию

```dockerfile
CMD ["executable", "param1", "param2"]    # Exec-форма (рекомендуется)
CMD command param1 param2                  # Shell-форма
CMD ["param1", "param2"]                   # Параметры для ENTRYPOINT
```

Определяет команду, которая выполнится при запуске контейнера.

```dockerfile
CMD ["java", "-jar", "app.jar"]
```

**Особенности:**
- Может быть только один `CMD` (последний перезаписывает предыдущие)
- Легко переопределяется при `docker run`

```bash
# Использует CMD из Dockerfile
docker run myapp:1.0

# Переопределяет CMD
docker run myapp:1.0 java -jar app.jar --debug
```

### ENTRYPOINT — точка входа

```dockerfile
ENTRYPOINT ["executable", "param1"]    # Exec-форма
ENTRYPOINT command param1              # Shell-форма
```

Определяет исполняемый файл контейнера. В отличие от `CMD`, не так просто переопределяется.

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**CMD + ENTRYPOINT вместе:**

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--server.port=8080"]
```

Результат: `java -jar app.jar --server.port=8080`

```bash
# Добавляет к ENTRYPOINT
docker run myapp:1.0 --debug
# Результат: java -jar app.jar --debug

# Переопределить ENTRYPOINT (редко нужно)
docker run --entrypoint /bin/sh myapp:1.0
```

**Когда что использовать:**
- `CMD` — для команд, которые пользователь может захотеть изменить
- `ENTRYPOINT` — для фиксированного исполняемого файла
- `CMD + ENTRYPOINT` — ENTRYPOINT как команда, CMD как параметры по умолчанию

### LABEL — метаданные

```dockerfile
LABEL key="value"
LABEL key1="value1" key2="value2"
```

Добавляет метаданные к образу.

```dockerfile
LABEL maintainer="team@example.com"
LABEL version="1.0.0"
LABEL description="Blockchain node application"
LABEL org.opencontainers.image.source="https://github.com/example/app"
```

**Просмотр labels:**
```bash
docker inspect myapp:1.0 --format='{{json .Config.Labels}}'
```

### USER — пользователь

```dockerfile
USER username
USER uid:gid
```

Устанавливает пользователя для последующих инструкций и для запуска контейнера.

```dockerfile
# Создать пользователя и переключиться на него
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

USER appuser

# Теперь все команды выполняются от appuser
COPY --chown=appuser:appgroup app.jar /app/
CMD ["java", "-jar", "/app/app.jar"]
```

**Важно для безопасности:** Не запускайте приложения от root!

### HEALTHCHECK — проверка здоровья

```dockerfile
HEALTHCHECK [OPTIONS] CMD command
HEALTHCHECK NONE
```

Определяет как Docker будет проверять работоспособность контейнера.

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
```

**Опции:**
- `--interval` — интервал между проверками (default 30s)
- `--timeout` — таймаут проверки (default 30s)
- `--start-period` — время на запуск до начала проверок (default 0s)
- `--retries` — количество неудач до статуса "unhealthy" (default 3)

**Статусы контейнера:**
- `starting` — ещё не было успешной проверки
- `healthy` — последние проверки успешны
- `unhealthy` — последние N проверок неуспешны

---

## 4. Multi-stage Builds

### Проблема: большой размер образа

Для сборки Java-приложения нужен JDK (компилятор, Maven/Gradle). Но для запуска достаточно JRE.

```dockerfile
# Плохо — образ содержит JDK, Maven, исходники, зависимости сборки
FROM maven:3.9-eclipse-temurin-21

WORKDIR /app
COPY . .
RUN mvn package

CMD ["java", "-jar", "target/app.jar"]
# Размер: ~800MB+
```

### Решение: Multi-stage build

Multi-stage build позволяет использовать несколько `FROM` в одном Dockerfile и копировать артефакты между стадиями.

```dockerfile
# ===== Стадия 1: Сборка =====
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Копируем файлы зависимостей отдельно (для кэширования)
COPY pom.xml .
RUN mvn dependency:go-offline

# Копируем исходники и собираем
COPY src ./src
RUN mvn package -DskipTests

# ===== Стадия 2: Runtime =====
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Копируем ТОЛЬКО JAR из стадии сборки
COPY --from=builder /app/target/*.jar app.jar

# Финальный образ содержит только JRE и JAR
CMD ["java", "-jar", "app.jar"]
# Размер: ~200MB
```

### Как это работает

```
┌─────────────────────────────────────────────────────────────────┐
│                         Стадия builder                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ maven:3.9-eclipse-temurin-21 (800MB)                    │   │
│  │   + исходники                                            │   │
│  │   + зависимости Maven                                    │   │
│  │   + скомпилированные классы                              │   │
│  │   + app.jar ◄─────────────────────────────┐             │   │
│  └───────────────────────────────────────────│─────────────┘   │
│                                              │                  │
│                    COPY --from=builder ──────┘                  │
│                                              │                  │
│                                              ▼                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ eclipse-temurin:21-jre-alpine (190MB)                   │   │
│  │   + app.jar                                              │   │
│  │   = Финальный образ (~200MB)                            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                         Стадия runtime                          │
└─────────────────────────────────────────────────────────────────┘
```

### Multi-stage для Gradle

```dockerfile
# Стадия 1: Сборка
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Копируем Gradle wrapper
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Скачиваем зависимости (кэшируется)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Копируем исходники и собираем
COPY src src
RUN ./gradlew bootJar --no-daemon

# Стадия 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

### Именованные стадии

Можно ссылаться на стадии по имени:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
# ... сборка ...

FROM eclipse-temurin:21-jre-alpine AS development
COPY --from=builder /app/target/app.jar app.jar
CMD ["java", "-jar", "app.jar", "--spring.profiles.active=dev"]

FROM eclipse-temurin:21-jre-alpine AS production
COPY --from=builder /app/target/app.jar app.jar
USER 1000
CMD ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
```

**Сборка конкретной стадии:**
```bash
docker build --target development -t myapp:dev .
docker build --target production -t myapp:prod .
```

---

## 5. Оптимизация кэширования слоёв

### Как работает кэш

Docker кэширует каждый слой (результат каждой инструкции). При повторной сборке:
1. Docker проверяет, изменилась ли инструкция или её входные данные
2. Если нет — использует кэшированный слой
3. Если да — пересобирает этот слой и все последующие

```
Инструкция 1 ──▶ Слой 1 (кэш)
Инструкция 2 ──▶ Слой 2 (кэш)
Инструкция 3 ──▶ Слой 3 (изменение!) ──▶ Пересборка
Инструкция 4 ──▶ Слой 4 ◄── Тоже пересобирается
Инструкция 5 ──▶ Слой 5 ◄── И этот тоже
```

### Правило: редко меняющееся — наверх

```dockerfile
# Плохо — любое изменение кода ломает кэш зависимостей
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .                              # Изменился любой файл = пересборка
RUN ./gradlew build                   # Каждый раз скачивает зависимости

# Хорошо — зависимости кэшируются отдельно от кода
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY gradlew .
RUN ./gradlew dependencies --no-daemon  # Кэшируется пока не изменится build.gradle
COPY src src                             # Только исходники
RUN ./gradlew build --no-daemon          # Быстрая сборка (зависимости уже есть)
```

### Порядок инструкций по частоте изменений

```dockerfile
FROM eclipse-temurin:21-jre-alpine    # 1. Базовый образ (меняется редко)

LABEL maintainer="dev@example.com"     # 2. Метаданные (редко)

RUN apk add --no-cache curl            # 3. Системные зависимости (редко)

WORKDIR /app                           # 4. Конфигурация (редко)
ENV JAVA_OPTS="-Xmx512m"

COPY --from=builder /app/app.jar .     # 5. Код приложения (часто)

EXPOSE 8080                            # 6. Документация
CMD ["java", "-jar", "app.jar"]
```

### .dockerignore

Файл `.dockerignore` исключает файлы из контекста сборки — они не будут отправлены Docker daemon и не повлияют на кэш.

```dockerignore
# .dockerignore

# Git
.git
.gitignore

# Build outputs
build/
target/
*.class

# IDE
.idea/
*.iml
.vscode/

# Logs
*.log

# Local configs
*.local
.env.local

# Tests (если не нужны в образе)
src/test/

# Documentation
*.md
docs/
```

---

## 6. Полный пример: Dockerfile для Spring Boot

### Структура проекта

```
my-app/
├── src/
│   └── main/
│       └── java/
│           └── com/example/App.java
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradle/
│   └── wrapper/
├── Dockerfile
└── .dockerignore
```

### Dockerfile

```dockerfile
# =============================================================================
# Multi-stage Dockerfile для Spring Boot приложения
# =============================================================================

# -----------------------------------------------------------------------------
# Стадия 1: Сборка
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Копируем файлы Gradle (для кэширования зависимостей)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Делаем gradlew исполняемым и скачиваем зависимости
RUN chmod +x gradlew && \
    ./gradlew dependencies --no-daemon

# Копируем исходники
COPY src src

# Собираем приложение
RUN ./gradlew bootJar --no-daemon

# -----------------------------------------------------------------------------
# Стадия 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Метаданные
LABEL maintainer="dev@example.com"
LABEL version="1.0.0"

# Создаём непривилегированного пользователя
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

WORKDIR /app

# Копируем JAR из стадии сборки
COPY --from=builder --chown=appuser:appgroup /app/build/libs/*.jar app.jar

# Переключаемся на непривилегированного пользователя
USER appuser

# Порт приложения
EXPOSE 8080

# JVM настройки для контейнера
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Запуск
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### .dockerignore

```dockerignore
# Build artifacts
build/
.gradle/
out/

# IDE
.idea/
*.iml
.vscode/
*.swp

# Git
.git/
.gitignore

# Documentation
*.md
docs/

# CI/CD
.github/
Jenkinsfile

# Docker
Dockerfile*
docker-compose*.yml
.dockerignore

# Misc
*.log
.env.local
```

### Команды сборки и запуска

```bash
# Сборка
docker build -t my-app:1.0 .

# Проверка размера
docker images my-app:1.0
# REPOSITORY   TAG   IMAGE ID       CREATED         SIZE
# my-app       1.0   abc123def456   10 seconds ago  210MB

# Запуск
docker run -d --name my-app -p 8080:8080 my-app:1.0

# Проверка health
docker ps
# STATUS: Up 30 seconds (healthy)

# Логи
docker logs -f my-app
```

---

## 7. Типичные ошибки

### Ошибка: COPY failed: file not found

```bash
COPY failed: file not found in build context
```

**Причины:**
1. Файл не существует
2. Файл исключён через `.dockerignore`
3. Путь указан неправильно

**Решение:**
```bash
# Проверить что файл существует
ls -la target/app.jar

# Проверить .dockerignore
cat .dockerignore | grep -v "^#"

# Посмотреть контекст сборки
docker build --no-cache . 2>&1 | head -20
```

### Ошибка: кэш не работает

**Симптом:** Каждая сборка скачивает зависимости заново.

**Причина:** COPY копирует файлы, которые меняются часто, до скачивания зависимостей.

```dockerfile
# Плохо
COPY . .
RUN ./gradlew build

# Хорошо
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies
COPY src src
RUN ./gradlew build
```

### Ошибка: permission denied в контейнере

```bash
java.io.FileNotFoundException: /app/logs/app.log (Permission denied)
```

**Причина:** Приложение запущено от non-root пользователя, но файлы принадлежат root.

**Решение:**
```dockerfile
COPY --chown=appuser:appgroup app.jar /app/
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs
USER appuser
```

### Ошибка: образ слишком большой

**Чеклист уменьшения размера:**
1. ✅ Используете multi-stage build?
2. ✅ Используете alpine/slim базовый образ?
3. ✅ Чистите кэш пакетного менеджера? (`rm -rf /var/lib/apt/lists/*`)
4. ✅ Объединяете RUN команды?
5. ✅ Настроен .dockerignore?

```bash
# Анализ слоёв образа
docker history myapp:1.0

# Детальный анализ (установите dive)
dive myapp:1.0
```

---

## 8. Вопросы для самопроверки

1. **Что делает инструкция FROM и почему она должна быть первой?**
   <details>
   <summary>Ответ</summary>
   FROM указывает базовый образ, на основе которого строится ваш. Должна быть первой, потому что все последующие инструкции применяются к этому базовому образу.
   </details>

2. **В чём разница между COPY и ADD?**
   <details>
   <summary>Ответ</summary>
   COPY просто копирует файлы. ADD дополнительно умеет распаковывать архивы и скачивать файлы по URL. Рекомендуется использовать COPY, если не нужны эти дополнительные возможности.
   </details>

3. **Чем отличаются CMD и ENTRYPOINT?**
   <details>
   <summary>Ответ</summary>
   CMD легко переопределяется при docker run. ENTRYPOINT определяет "исполняемый файл" контейнера и переопределяется только через --entrypoint. Часто используют вместе: ENTRYPOINT как команда, CMD как параметры по умолчанию.
   </details>

4. **Что такое multi-stage build и какую проблему решает?**
   <details>
   <summary>Ответ</summary>
   Multi-stage build позволяет использовать несколько FROM в одном Dockerfile. Решает проблему большого размера образа: на одной стадии собираем (нужен JDK, Maven), на другой запускаем (нужен только JRE). Копируем только артефакты между стадиями.
   </details>

5. **Почему важен порядок инструкций в Dockerfile?**
   <details>
   <summary>Ответ</summary>
   Docker кэширует слои. При изменении инструкции пересобираются все последующие слои. Поэтому редко меняющиеся инструкции (установка зависимостей) должны быть выше, часто меняющиеся (копирование кода) — ниже.
   </details>

6. **Как правильно указать переменную, которая нужна и при сборке, и при запуске?**
   <details>
   <summary>Ответ</summary>
   Использовать ARG для получения значения при сборке и ENV для сохранения в образе:
   ```dockerfile
   ARG APP_VERSION=1.0.0
   ENV APP_VERSION=${APP_VERSION}
   ```
   </details>

7. **Что делает EXPOSE и открывает ли он порт?**
   <details>
   <summary>Ответ</summary>
   EXPOSE не открывает порт — это только документация, указывающая какие порты использует приложение. Реальный проброс порта делается через docker run -p.
   </details>

8. **Почему не рекомендуется запускать приложение от root?**
   <details>
   <summary>Ответ</summary>
   Безопасность: если злоумышленник получит доступ к контейнеру, он получит root-права. При использовании non-root пользователя потенциальный ущерб ограничен.
   </details>

9. **Для чего нужен .dockerignore?**
   <details>
   <summary>Ответ</summary>
   Исключает файлы из контекста сборки. Это: 1) ускоряет сборку (меньше данных передаётся daemon), 2) предотвращает попадание ненужных файлов в образ, 3) улучшает кэширование (изменения в исключённых файлах не инвалидируют кэш).
   </details>

10. **Как оптимизировать кэширование при сборке Java-приложения с Gradle?**
    <details>
    <summary>Ответ</summary>
    Копировать файлы сборки (build.gradle, settings.gradle, gradle/) отдельно от исходников и скачивать зависимости до COPY src. Тогда изменение исходников не будет приводить к повторному скачиванию зависимостей.
    </details>

---

## Итоги урока

Вы изучили:
- ✅ Структуру и синтаксис Dockerfile
- ✅ Все основные инструкции: FROM, COPY, RUN, ENV, ARG, CMD, ENTRYPOINT
- ✅ Multi-stage builds для оптимизации размера
- ✅ Принципы кэширования слоёв
- ✅ Создание production-ready Dockerfile для Java

**Следующий урок:** Docker Compose — запуск многоконтейнерных приложений.
