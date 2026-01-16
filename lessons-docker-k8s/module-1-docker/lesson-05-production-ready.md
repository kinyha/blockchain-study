# Урок 5: Production Ready — Java в Docker

## Цели урока
После этого урока вы будете:
- Понимать особенности работы JVM в контейнерах
- Правильно настраивать память и CPU для Java в Docker
- Знать best practices безопасности контейнеров
- Реализовывать graceful shutdown
- Настраивать логирование и мониторинг

---

## 1. JVM в контейнерах: особенности

### Проблема: JVM не знает о лимитах контейнера

До Java 10 JVM определял доступные ресурсы по хост-системе, игнорируя лимиты контейнера:

```
Хост-система: 32GB RAM, 16 CPU
Контейнер: limits 512MB RAM, 0.5 CPU

Старая JVM видит: 32GB RAM, 16 CPU  ← Проблема!
                  Выделяет heap 8GB
                  Создаёт 16 GC threads

Результат: OOM Kill контейнера
```

### Решение: Container-aware JVM

Начиная с Java 10 (и backport в Java 8u191), JVM поддерживает контейнеры:

```bash
# Включено по умолчанию в современных JVM
-XX:+UseContainerSupport

# JVM теперь видит реальные лимиты контейнера
```

**Проверка:**
```bash
docker run --rm -m 512m eclipse-temurin:21 java \
    -XshowSettings:system \
    -version 2>&1 | grep -E "Memory|CPU"

# Operating System Metrics:
#     Memory Limit: 512.00M
#     Available CPUs: 1
```

---

## 2. Настройка памяти

### Структура памяти JVM

```
┌─────────────────────────────────────────────────────────────────┐
│                    Контейнер (512MB limit)                       │
├─────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                      JVM Process                          │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │                    Heap                             │  │  │
│  │  │  -Xms (initial)      -Xmx (maximum)                │  │  │
│  │  │  Young Gen + Old Gen                                │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              Non-Heap (Metaspace)                   │  │  │
│  │  │  Class metadata, JIT compiled code                  │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              Off-Heap / Native                      │  │  │
│  │  │  Direct buffers, JNI, native libs                   │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  OS overhead (libraries, kernel buffers) ~50-100MB              │
└─────────────────────────────────────────────────────────────────┘
```

### Рекомендуемые настройки

```dockerfile
# Для контейнера с лимитом памяти
ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+ExitOnOutOfMemoryError"
```

**Объяснение:**

| Параметр | Значение | Описание |
|----------|----------|----------|
| `UseContainerSupport` | включено | JVM видит лимиты контейнера |
| `MaxRAMPercentage` | 75% | Максимум heap от доступной RAM |
| `InitialRAMPercentage` | 50% | Начальный размер heap |
| `UseG1GC` | G1 collector | Оптимален для большинства приложений |
| `ExitOnOutOfMemoryError` | выход | Контейнер рестартует при OOM |

### Почему не 100%?

```
Контейнер: 512MB
├── Heap (75%): 384MB         ← MaxRAMPercentage
├── Metaspace: ~50MB          ← Классы
├── Thread stacks: ~20MB      ← 1MB на thread × 20 threads
├── Direct buffers: ~10MB     ← NIO
├── Native libs: ~20MB        ← JNI, native code
└── OS overhead: ~28MB        ← Остаток
    ─────────────────
    Total: 512MB
```

**Если установить 100%:** JVM выделит весь heap, не останется места для non-heap → OOM Kill.

### Фиксированные значения vs процентные

```dockerfile
# Процентные (рекомендуется) — адаптируется к лимитам контейнера
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Фиксированные — требуют изменения при смене лимитов
ENV JAVA_OPTS="-Xmx384m -Xms256m"
```

**Когда использовать фиксированные:**
- Точный контроль над потреблением памяти
- Известные и стабильные лимиты контейнера
- Специфичные требования приложения

---

## 3. Настройка CPU

### Как JVM использует CPU

JVM создаёт threads пропорционально количеству CPU:
- GC threads
- Compiler threads (JIT)
- Application threads

```bash
# Проверить сколько CPU видит JVM
docker run --rm --cpus=2 eclipse-temurin:21 java \
    -XshowSettings:system -version 2>&1 | grep CPU

# Available CPUs: 2
```

### Ограничение CPU для контейнера

```yaml
# docker-compose.yml
services:
  app:
    deploy:
      resources:
        limits:
          cpus: "2.0"       # Максимум 2 CPU
          memory: 512M
        reservations:
          cpus: "0.5"       # Гарантированные 0.5 CPU
          memory: 256M
```

```bash
# docker run
docker run --cpus=2 --memory=512m myapp:1.0
```

### Настройка JVM под ограниченные CPU

```dockerfile
ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:ActiveProcessorCount=2 \
    -XX:ParallelGCThreads=2 \
    -XX:ConcGCThreads=1"
```

| Параметр | Описание |
|----------|----------|
| `ActiveProcessorCount` | Принудительно задать количество CPU |
| `ParallelGCThreads` | Threads для parallel GC фаз |
| `ConcGCThreads` | Threads для concurrent GC фаз |

---

## 4. Безопасность контейнеров

### Non-root пользователь

**Проблема:** Root в контейнере = потенциальный root на хосте при escape.

```dockerfile
# Плохо — приложение работает от root
FROM eclipse-temurin:21-jre
COPY app.jar /app/
CMD ["java", "-jar", "/app/app.jar"]
# USER = root

# Хорошо — непривилегированный пользователь
FROM eclipse-temurin:21-jre-alpine

# Создаём пользователя с конкретным UID/GID
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D -H -s /sbin/nologin appuser

WORKDIR /app
COPY --chown=appuser:appgroup app.jar .

USER appuser
CMD ["java", "-jar", "app.jar"]
```

### Read-only filesystem

```yaml
# docker-compose.yml
services:
  app:
    read_only: true
    tmpfs:
      - /tmp
    volumes:
      - logs:/app/logs    # Только сюда можно писать
```

### Минимизация attack surface

```dockerfile
# Использовать minimal base image
FROM eclipse-temurin:21-jre-alpine    # Alpine без лишних пакетов

# Или distroless (ещё меньше)
FROM gcr.io/distroless/java21-debian12

# Удалять ненужные пакеты и файлы
RUN apk del --purge curl wget && \
    rm -rf /var/cache/apk/*
```

### Security scanning

```bash
# Сканирование образа на уязвимости
docker scout cves myapp:1.0

# Или с Trivy
trivy image myapp:1.0
```

### Cheatsheet безопасности

| Практика | Реализация |
|----------|------------|
| Non-root user | `USER 1000` в Dockerfile |
| Minimal base | Alpine, Distroless |
| Read-only FS | `read_only: true` |
| No capabilities | `cap_drop: [ALL]` |
| Security scanning | Docker Scout, Trivy |
| Secrets management | Docker secrets, env vars (не в образе!) |

---

## 5. Graceful Shutdown

### Проблема: потеря запросов при остановке

```
1. docker stop app (или K8s rolling update)
2. Docker отправляет SIGTERM
3. JVM получает сигнал
4. Приложение должно:
   - Прекратить принимать новые запросы
   - Дождаться завершения текущих
   - Освободить ресурсы
   - Завершиться
5. Через 10 секунд (default) Docker отправляет SIGKILL
```

### Spring Boot: graceful shutdown

```yaml
# application.yml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

```dockerfile
# Увеличить timeout в Docker
STOPSIGNAL SIGTERM
# При docker stop --time=30 или в docker-compose:
```

```yaml
# docker-compose.yml
services:
  app:
    stop_grace_period: 30s
```

### JVM Shutdown Hooks

```java
@Component
public class GracefulShutdown {

    @PreDestroy
    public void onShutdown() {
        log.info("Shutting down gracefully...");
        // Закрыть соединения
        // Завершить фоновые задачи
        // Сохранить состояние
    }
}

// Или через Runtime
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    System.out.println("Shutdown hook executing...");
}));
```

### Проверка graceful shutdown

```bash
# Запустить приложение
docker compose up -d

# Отправить запрос (в другом терминале)
curl http://localhost:8080/api/slow-operation &

# Остановить контейнер
docker compose stop

# Проверить логи — запрос должен завершиться
docker compose logs app | tail -20
```

---

## 6. Логирование

### Проблема: логи внутри контейнера

```dockerfile
# Плохо — логи в файл внутри контейнера
CMD ["java", "-jar", "app.jar", "--logging.file.name=/app/logs/app.log"]
# Логи потеряются при удалении контейнера
```

### Решение: логи в stdout/stderr

```yaml
# application.yml
logging:
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
```

```dockerfile
# Приложение пишет в stdout
CMD ["java", "-jar", "app.jar"]

# Docker собирает логи из stdout/stderr
# docker logs app
```

### Structured logging (JSON)

```yaml
# application.yml (Spring Boot)
logging:
  pattern:
    console: '{"time":"%d{ISO8601}","level":"%level","logger":"%logger","message":"%msg"}%n'
```

Или с Logback:
```xml
<!-- logback.xml -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

### Просмотр логов

```bash
# Docker logs
docker logs -f container_name

# Docker Compose logs
docker compose logs -f app

# С фильтрацией (если JSON)
docker logs app 2>&1 | jq 'select(.level=="ERROR")'
```

---

## 7. Health Checks и Probes

### Типы проверок

```
┌─────────────────────────────────────────────────────────────────┐
│                        Health Checks                             │
├────────────────────┬────────────────────┬───────────────────────┤
│     Liveness       │     Readiness      │      Startup          │
├────────────────────┼────────────────────┼───────────────────────┤
│ "Приложение        │ "Приложение готово │ "Приложение           │
│  живо?"            │  принимать         │  запустилось?"        │
│                    │  трафик?"          │                       │
├────────────────────┼────────────────────┼───────────────────────┤
│ Если fail:         │ Если fail:         │ Если fail:            │
│ Restart container  │ Stop sending       │ Restart container     │
│                    │ traffic            │ (после timeout)       │
└────────────────────┴────────────────────┴───────────────────────┘
```

### Spring Boot Actuator

```gradle
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

**Endpoints:**
- `/actuator/health` — общий статус
- `/actuator/health/liveness` — для liveness probe
- `/actuator/health/readiness` — для readiness probe

### Docker HEALTHCHECK

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1
```

### Custom Health Indicator

```java
@Component
public class BlockchainHealthIndicator implements HealthIndicator {

    private final BlockchainService blockchainService;

    @Override
    public Health health() {
        try {
            int height = blockchainService.getChainHeight();
            boolean valid = blockchainService.isChainValid();

            if (!valid) {
                return Health.down()
                    .withDetail("reason", "Chain validation failed")
                    .build();
            }

            return Health.up()
                .withDetail("chainHeight", height)
                .withDetail("chainValid", valid)
                .build();

        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```

---

## 8. Финальный Production Dockerfile

```dockerfile
# =============================================================================
# Production Dockerfile for Blockchain Node
# =============================================================================

# -----------------------------------------------------------------------------
# Build Stage
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Gradle wrapper и build files (для кэширования)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew && \
    ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon

# -----------------------------------------------------------------------------
# Runtime Stage
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Метаданные
LABEL maintainer="blockchain-study" \
      version="1.0.0" \
      description="Production-ready Blockchain Node"

# Security: создаём непривилегированного пользователя
RUN addgroup -g 1000 blockchain && \
    adduser -u 1000 -G blockchain -D -H -s /sbin/nologin blockchain

# Security: удаляем ненужные пакеты
RUN apk --no-cache add wget && \
    rm -rf /var/cache/apk/*

WORKDIR /app

# Копируем JAR с правильными правами
COPY --from=builder --chown=blockchain:blockchain \
    /build/build/libs/*.jar app.jar

# Переключаемся на непривилегированного пользователя
USER blockchain

# Порт
EXPOSE 8080

# JVM настройки для контейнеров
ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Graceful shutdown
STOPSIGNAL SIGTERM

# Точка входа
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 9. Чеклист Production Ready

### Память и CPU
- [ ] `UseContainerSupport` включён (default в Java 10+)
- [ ] `MaxRAMPercentage` установлен (рекомендуется 75%)
- [ ] CPU limits заданы в docker-compose/K8s
- [ ] Протестировано под нагрузкой

### Безопасность
- [ ] Non-root пользователь
- [ ] Minimal base image (alpine/distroless)
- [ ] Secrets не в образе
- [ ] Security scan пройден

### Надёжность
- [ ] Health check настроен
- [ ] Graceful shutdown реализован
- [ ] `stop_grace_period` достаточен
- [ ] Restart policy определена

### Наблюдаемость
- [ ] Логи идут в stdout/stderr
- [ ] Structured logging (JSON)
- [ ] Actuator endpoints включены
- [ ] Метрики доступны

---

## 10. Вопросы для самопроверки

1. **Почему MaxRAMPercentage рекомендуется ставить 75%, а не 100%?**
   <details>
   <summary>Ответ</summary>
   JVM использует память не только для heap: metaspace, thread stacks, direct buffers, native memory. Если выделить 100% под heap, не останется места для остального → OOM Kill.
   </details>

2. **Что произойдёт, если JVM не поддерживает UseContainerSupport?**
   <details>
   <summary>Ответ</summary>
   JVM определит ресурсы по хост-системе, а не по лимитам контейнера. Может выделить больше памяти чем доступно → контейнер будет убит OOM Killer.
   </details>

3. **Почему важно запускать приложение от non-root пользователя?**
   <details>
   <summary>Ответ</summary>
   При компрометации контейнера атакующий получит права пользователя внутри контейнера. Если это root — потенциально может эскейпить на хост. Non-root минимизирует ущерб.
   </details>

4. **Что такое graceful shutdown и зачем он нужен?**
   <details>
   <summary>Ответ</summary>
   Graceful shutdown — корректное завершение приложения: прекращение приёма новых запросов, завершение текущих, освобождение ресурсов. Без него запросы могут оборваться, данные потеряться.
   </details>

5. **Почему логи должны идти в stdout, а не в файл?**
   <details>
   <summary>Ответ</summary>
   Docker и Kubernetes собирают логи из stdout/stderr. Файлы внутри контейнера теряются при удалении. Stdout позволяет использовать единую систему сбора логов.
   </details>

6. **Чем отличается liveness probe от readiness probe?**
   <details>
   <summary>Ответ</summary>
   Liveness проверяет "приложение живо?" — если fail, контейнер рестартуется. Readiness проверяет "готово принимать трафик?" — если fail, трафик перестаёт направляться, но контейнер не рестартуется.
   </details>

7. **Для чего нужен параметр `-XX:+ExitOnOutOfMemoryError`?**
   <details>
   <summary>Ответ</summary>
   При OutOfMemoryError JVM завершается (вместо попытки продолжить работу в нестабильном состоянии). Контейнер рестартуется orchestrator'ом, приложение начинает работу в чистом состоянии.
   </details>

---

## Итоги модуля Docker

Вы изучили:
- ✅ **Урок 1:** Основы Docker — контейнеры vs VM, архитектура, базовые команды
- ✅ **Урок 2:** Dockerfile — инструкции, multi-stage builds, кэширование
- ✅ **Урок 3:** Docker Compose — многоконтейнерные приложения
- ✅ **Урок 4:** Практика — контейнеризация blockchain-приложения
- ✅ **Урок 5:** Production Ready — JVM tuning, безопасность, graceful shutdown

**Следующий модуль:** Kubernetes — оркестрация контейнеров в кластере.
