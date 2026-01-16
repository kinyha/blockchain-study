# Урок 11: Стабильность — Resources, Probes, Graceful Shutdown

## Цели урока

После этого урока вы будете:
- Понимать, как K8s управляет ресурсами (CPU, Memory)
- Знать разницу между requests и limits
- Уметь настраивать Health Probes (liveness, readiness, startup)
- Понимать Graceful Shutdown и preStop hooks
- Уметь обеспечить стабильную работу приложений в продакшене

---

## Зачем управлять ресурсами?

### Проблема: "шумный сосед"

```
┌─────────────────────────────────────────────────────────────┐
│                   БЕЗ RESOURCE LIMITS                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Node (4 CPU, 8GB RAM)                                     │
│   ┌───────────────────────────────────────────────────────┐ │
│   │                                                       │ │
│   │  Pod A (miner)     Pod B (wallet)    Pod C (api)     │ │
│   │  ████████████████  ░░░░              ░░░░            │ │
│   │  Съел 3.5 CPU!     Голодает          Голодает        │ │
│   │                                                       │ │
│   └───────────────────────────────────────────────────────┘ │
│                                                             │
│   Результат:                                                │
│   • Pod A тормозит всех соседей                            │
│   • Pod B и C почти не получают CPU                        │
│   • Нестабильная работа, непредсказуемая latency           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Решение: requests и limits

```
┌─────────────────────────────────────────────────────────────┐
│                   С RESOURCE LIMITS                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Node (4 CPU, 8GB RAM)                                     │
│   ┌───────────────────────────────────────────────────────┐ │
│   │                                                       │ │
│   │  Pod A (miner)     Pod B (wallet)    Pod C (api)     │ │
│   │  limit: 1 CPU      limit: 1 CPU      limit: 1 CPU    │ │
│   │  ████              ████              ████            │ │
│   │  Ограничен!        Получает своё     Получает своё   │ │
│   │                                                       │ │
│   └───────────────────────────────────────────────────────┘ │
│                                                             │
│   Результат:                                                │
│   • Каждый Pod получает гарантированные ресурсы            │
│   • Предсказуемая производительность                       │
│   • Справедливое распределение                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Requests vs Limits

### Определения

```
┌─────────────────────────────────────────────────────────────┐
│              REQUESTS vs LIMITS                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  REQUESTS (запросы):                                        │
│  • Гарантированный минимум ресурсов                         │
│  • Используется Scheduler для планирования                  │
│  • Pod НЕ будет запущен, если Node не может дать requests  │
│                                                             │
│  LIMITS (лимиты):                                           │
│  • Максимум, который Pod может использовать                 │
│  • Превышение CPU → throttling (замедление)                │
│  • Превышение Memory → OOMKilled (убийство процесса)       │
│                                                             │
└─────────────────────────────────────────────────────────────┘

requests ≤ использование ≤ limits

       requests          limits
          │                │
          ▼                ▼
    ──────┬────────────────┬──────
          │████████████████│
          │  гарантировано │ максимум
          │                │
```

### Пример конфигурации

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: blockchain-node
spec:
  containers:
    - name: node
      image: blockchain-node:latest
      resources:
        requests:
          memory: "256Mi"    # Гарантировано 256MB RAM
          cpu: "250m"        # Гарантировано 0.25 CPU (250 millicores)
        limits:
          memory: "512Mi"    # Максимум 512MB RAM
          cpu: "1000m"       # Максимум 1 CPU (1000 millicores)
```

### Единицы измерения

```
┌─────────────────────────────────────────────────────────────┐
│                   ЕДИНИЦЫ РЕСУРСОВ                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  CPU (процессор):                                           │
│  • 1 = 1 ядро (или 1 vCPU в облаке)                        │
│  • 1000m = 1 (millicores)                                   │
│  • 500m = 0.5 CPU                                           │
│  • 100m = 0.1 CPU                                           │
│                                                             │
│  Memory (память):                                           │
│  • Ki, Mi, Gi (кибибайты, мебибайты, гибибайты) — 1024     │
│  • K, M, G (килобайты, мегабайты, гигабайты) — 1000        │
│  • 256Mi = 268,435,456 bytes                               │
│  • 1Gi = 1,073,741,824 bytes                               │
│                                                             │
│  Рекомендация: используйте Mi/Gi (бинарные)                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Что происходит при превышении

```
┌─────────────────────────────────────────────────────────────┐
│              ПРЕВЫШЕНИЕ РЕСУРСОВ                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  CPU превышает limit:                                       │
│  → Throttling (процесс замедляется)                        │
│  → Pod продолжает работать                                 │
│  → Latency увеличивается                                   │
│                                                             │
│  Memory превышает limit:                                    │
│  → OOMKilled (Out Of Memory)                               │
│  → Контейнер убивается и перезапускается                   │
│  → Видно в kubectl describe pod: OOMKilled                 │
│                                                             │
│  Memory превышает доступное на Node:                        │
│  → Eviction (выселение Pod)                                │
│  → kubelet удаляет Pod для защиты Node                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Quality of Service (QoS) классы

K8s автоматически присваивает QoS класс на основе requests/limits:

```
┌─────────────────────────────────────────────────────────────┐
│                    QoS КЛАССЫ                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Guaranteed (гарантированный):                           │
│     requests = limits для ВСЕХ контейнеров                 │
│     → Последний кандидат на eviction                       │
│     → Максимальная стабильность                            │
│                                                             │
│  2. Burstable (эластичный):                                 │
│     requests < limits ИЛИ только requests                  │
│     → Средний приоритет при eviction                       │
│     → Может использовать больше, если есть                 │
│                                                             │
│  3. BestEffort (по возможности):                            │
│     НЕТ requests и limits                                  │
│     → Первый кандидат на eviction                          │
│     → Не рекомендуется для production                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘

При нехватке ресурсов на Node:
BestEffort → Burstable → Guaranteed
(убиваются первыми)  (убиваются последними)
```

### Примеры QoS классов

```yaml
# Guaranteed: requests = limits
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "512Mi"   # = requests
    cpu: "500m"       # = requests

# Burstable: requests < limits
resources:
  requests:
    memory: "256Mi"
    cpu: "250m"
  limits:
    memory: "512Mi"   # > requests
    cpu: "1000m"      # > requests

# BestEffort: нет requests и limits
# (просто не указываем resources)
```

```bash
# Проверить QoS класс
kubectl get pod my-pod -o jsonpath='{.status.qosClass}'
# Guaranteed / Burstable / BestEffort
```

---

## Resource Quotas и LimitRanges

### LimitRange — defaults для namespace

```yaml
# Устанавливает defaults для всех Pod в namespace
apiVersion: v1
kind: LimitRange
metadata:
  name: default-limits
  namespace: blockchain
spec:
  limits:
    - type: Container
      default:           # Defaults для limits
        memory: "512Mi"
        cpu: "500m"
      defaultRequest:    # Defaults для requests
        memory: "256Mi"
        cpu: "100m"
      min:               # Минимум
        memory: "64Mi"
        cpu: "50m"
      max:               # Максимум
        memory: "2Gi"
        cpu: "2"
```

### ResourceQuota — лимиты на namespace

```yaml
# Общие лимиты на весь namespace
apiVersion: v1
kind: ResourceQuota
metadata:
  name: blockchain-quota
  namespace: blockchain
spec:
  hard:
    requests.cpu: "4"        # Сумма requests.cpu всех Pod
    requests.memory: "8Gi"   # Сумма requests.memory
    limits.cpu: "8"          # Сумма limits.cpu
    limits.memory: "16Gi"    # Сумма limits.memory
    pods: "20"               # Максимум Pod в namespace
    services: "10"           # Максимум Services
```

---

## Health Probes

### Зачем нужны Probes

```
┌─────────────────────────────────────────────────────────────┐
│                БЕЗ HEALTH PROBES                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Контейнер запущен (STATUS: Running)                       │
│   НО приложение внутри:                                     │
│   • Ещё инициализируется (Spring Boot startup)             │
│   • Зависло (deadlock)                                      │
│   • Потеряло соединение с БД                               │
│   • Переполнило память и тормозит                          │
│                                                             │
│   K8s не знает об этом!                                     │
│   → Трафик идёт на нерабочий Pod                           │
│   → Пользователи получают ошибки                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Три типа Probes

```
┌─────────────────────────────────────────────────────────────┐
│                    ТИПЫ PROBES                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. STARTUP PROBE (K8s 1.16+)                               │
│     Вопрос: "Приложение запустилось?"                       │
│     Когда: При старте контейнера                           │
│     Если fail: Контейнер перезапускается                   │
│     Отключает: liveness и readiness до успеха              │
│                                                             │
│  2. LIVENESS PROBE                                          │
│     Вопрос: "Приложение живо?"                              │
│     Когда: Постоянно, после startup                        │
│     Если fail: Контейнер перезапускается                   │
│     Для: Deadlocks, зависания                              │
│                                                             │
│  3. READINESS PROBE                                         │
│     Вопрос: "Приложение готово принимать трафик?"           │
│     Когда: Постоянно                                       │
│     Если fail: Pod убирается из Service endpoints          │
│     Контейнер НЕ перезапускается!                          │
│     Для: Временная недоступность, прогрев кэша            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Жизненный цикл с Probes

```
┌─────────────────────────────────────────────────────────────┐
│              ЖИЗНЕННЫЙ ЦИКЛ POD С PROBES                     │
├─────────────────────────────────────────────────────────────┐
│                                                             │
│   Контейнер создан                                          │
│        │                                                    │
│        ▼                                                    │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ STARTUP PROBE (если настроен)                       │   │
│   │ • Даёт время на инициализацию                       │   │
│   │ • liveness/readiness отключены                      │   │
│   │ • Fail → restart контейнера                         │   │
│   └──────────────────────┬──────────────────────────────┘   │
│                          │ Success                          │
│                          ▼                                  │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ LIVENESS + READINESS PROBES (параллельно)          │   │
│   │                                                     │   │
│   │ Liveness fail → restart контейнера                 │   │
│   │ Readiness fail → убрать из Service                 │   │
│   │ Readiness success → добавить в Service             │   │
│   └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Методы проверки

```yaml
# 1. HTTP GET — самый распространённый
livenessProbe:
  httpGet:
    path: /api/node/health
    port: 8080
    httpHeaders:              # Опционально
      - name: X-Custom-Header
        value: Probe
  initialDelaySeconds: 30     # Ждать перед первой проверкой
  periodSeconds: 10           # Интервал между проверками
  timeoutSeconds: 5           # Таймаут на ответ
  failureThreshold: 3         # Сколько fail до действия
  successThreshold: 1         # Сколько success для recovery

# 2. TCP Socket — проверка порта
livenessProbe:
  tcpSocket:
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 10

# 3. Exec — выполнить команду
livenessProbe:
  exec:
    command:
      - cat
      - /tmp/healthy
  initialDelaySeconds: 5
  periodSeconds: 5

# 4. gRPC (K8s 1.24+)
livenessProbe:
  grpc:
    port: 50051
  initialDelaySeconds: 10
```

### Пример для blockchain-node

```yaml
# k8s/miner-deployment.yaml (фрагмент)
spec:
  containers:
    - name: blockchain-node
      image: blockchain-node:latest
      ports:
        - name: http
          containerPort: 8080

      # Startup: даём время на запуск Spring Boot
      startupProbe:
        httpGet:
          path: /api/node/health
          port: http
        initialDelaySeconds: 10
        periodSeconds: 5
        failureThreshold: 30    # 30 * 5 = 150 сек на старт
        timeoutSeconds: 3

      # Liveness: приложение живо?
      livenessProbe:
        httpGet:
          path: /api/node/health
          port: http
        initialDelaySeconds: 0   # Сразу после startup
        periodSeconds: 10
        timeoutSeconds: 5
        failureThreshold: 3      # 3 fail → restart

      # Readiness: готов принимать трафик?
      readinessProbe:
        httpGet:
          path: /api/node/health
          port: http
        initialDelaySeconds: 0
        periodSeconds: 5
        timeoutSeconds: 3
        failureThreshold: 3
        successThreshold: 1
```

### Best Practices для Probes

```
┌─────────────────────────────────────────────────────────────┐
│              BEST PRACTICES                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Используйте разные endpoints:                           │
│     • Liveness: /health/live — базовая проверка            │
│     • Readiness: /health/ready — с проверкой зависимостей  │
│                                                             │
│  2. Liveness должен быть ПРОСТЫМ:                           │
│     ✗ Не проверять БД, внешние сервисы                     │
│     ✓ Только "приложение не зависло"                       │
│     Иначе: restart при проблемах с БД → каскадный сбой    │
│                                                             │
│  3. Readiness может быть СЛОЖНЫМ:                           │
│     ✓ Проверять подключение к БД                           │
│     ✓ Проверять готовность кэша                            │
│     ✓ Проверять синхронизацию с peers                      │
│                                                             │
│  4. Используйте Startup Probe для медленного старта:        │
│     • Spring Boot может стартовать 30-60 сек               │
│     • Без startup probe → liveness kill до старта          │
│                                                             │
│  5. Не делайте probes слишком агрессивными:                 │
│     • periodSeconds: 10 (не 1!)                            │
│     • failureThreshold: 3 (не 1!)                          │
│     • Иначе: flapping, лишние restarts                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Graceful Shutdown

### Проблема: потеря запросов при остановке

```
┌─────────────────────────────────────────────────────────────┐
│           БЕЗ GRACEFUL SHUTDOWN                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   kubectl delete pod my-pod                                 │
│        │                                                    │
│        ▼                                                    │
│   1. K8s отправляет SIGTERM                                 │
│   2. Приложение сразу умирает                               │
│   3. Запросы в обработке → ПОТЕРЯНЫ                        │
│   4. Новые запросы всё ещё идут (Service не обновлён)      │
│   5. Пользователи получают 502/503                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Решение: terminationGracePeriodSeconds + preStop

```
┌─────────────────────────────────────────────────────────────┐
│           GRACEFUL SHUTDOWN FLOW                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   kubectl delete pod                                        │
│        │                                                    │
│        ▼                                                    │
│   1. Pod переходит в Terminating                            │
│   2. Pod убирается из Service endpoints (параллельно)       │
│   3. preStop hook выполняется (если есть)                   │
│   4. SIGTERM отправляется в контейнер                       │
│   5. Приложение завершает текущие запросы                   │
│   6. Ждём terminationGracePeriodSeconds (default: 30s)      │
│   7. Если не завершился → SIGKILL (принудительно)          │
│        │                                                    │
│        ▼                                                    │
│   Pod удалён                                                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Конфигурация Graceful Shutdown

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: blockchain-node
spec:
  terminationGracePeriodSeconds: 60  # Время на завершение (default: 30)

  containers:
    - name: node
      image: blockchain-node:latest

      lifecycle:
        preStop:
          exec:
            command:
              - /bin/sh
              - -c
              - |
                # Даём время kube-proxy обновить правила
                sleep 5
                # Можно добавить graceful shutdown команду
                # curl -X POST http://localhost:8080/shutdown
```

### Spring Boot Graceful Shutdown

```properties
# application.properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

```yaml
# Deployment
spec:
  terminationGracePeriodSeconds: 45  # > spring timeout

  containers:
    - name: app
      lifecycle:
        preStop:
          exec:
            command: ["sleep", "5"]  # Ждём обновления Service
```

### Почему нужен preStop sleep?

```
┌─────────────────────────────────────────────────────────────┐
│              RACE CONDITION БЕЗ PRESTOP SLEEP                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Time  │ Pod                    │ kube-proxy/endpoints     │
│   ──────┼────────────────────────┼──────────────────────────│
│   0ms   │ SIGTERM получен        │ Начинает удалять из Svc │
│   10ms  │ Приложение завершается │ Ещё обновляет правила   │
│   20ms  │ Pod мёртв              │ Запросы ещё идут сюда!  │
│   50ms  │ -                      │ Правила обновлены       │
│                                                             │
│   Результат: запросы на мёртвый Pod = 502 ошибки           │
│                                                             │
│   С preStop sleep 5:                                        │
│   0ms   │ preStop: sleep 5       │ Начинает удалять из Svc │
│   5000ms│ SIGTERM получен        │ Правила обновлены ✓     │
│   5010ms│ Приложение завершается │ Новые запросы не идут   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Pod Disruption Budgets (PDB)

### Зачем нужен PDB

```
┌─────────────────────────────────────────────────────────────┐
│              БЕЗ POD DISRUPTION BUDGET                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Deployment: 3 реплики                                     │
│                                                             │
│   Drain node для обслуживания:                              │
│   kubectl drain node-1 --ignore-daemonsets                  │
│                                                             │
│   Результат: все 3 Pod на node-1 убиты одновременно!       │
│   → Даунтайм пока новые Pod не запустятся                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### PDB ограничивает одновременные disruptions

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: blockchain-pdb
  namespace: blockchain
spec:
  # Вариант 1: минимум доступных
  minAvailable: 2
  # ИЛИ
  # Вариант 2: максимум недоступных
  # maxUnavailable: 1

  selector:
    matchLabels:
      app.kubernetes.io/name: wallet-node
```

```bash
# Теперь при drain:
kubectl drain node-1 --ignore-daemonsets

# K8s убьёт Pod по одному, ожидая пока новый станет Ready
# Гарантируя minAvailable: 2 всегда
```

---

## Практика: настройка стабильности

### Полный пример Deployment

```yaml
# k8s/miner-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: miner-node
  namespace: blockchain
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: miner-node
  template:
    metadata:
      labels:
        app.kubernetes.io/name: miner-node
    spec:
      terminationGracePeriodSeconds: 60

      containers:
        - name: blockchain-node
          image: blockchain-node:latest

          ports:
            - name: http
              containerPort: 8080

          # ═══════════════════════════════════════
          # RESOURCES
          # ═══════════════════════════════════════
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "1000m"

          # ═══════════════════════════════════════
          # PROBES
          # ═══════════════════════════════════════
          startupProbe:
            httpGet:
              path: /api/node/health
              port: http
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 30
            timeoutSeconds: 3

          livenessProbe:
            httpGet:
              path: /api/node/health
              port: http
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3

          readinessProbe:
            httpGet:
              path: /api/node/health
              port: http
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3

          # ═══════════════════════════════════════
          # LIFECYCLE
          # ═══════════════════════════════════════
          lifecycle:
            preStop:
              exec:
                command: ["sleep", "5"]

          # ═══════════════════════════════════════
          # SECURITY
          # ═══════════════════════════════════════
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: false
            capabilities:
              drop:
                - ALL
```

### Проверка настроек

```bash
# Применить
kubectl apply -f k8s/miner-deployment.yaml

# Проверить QoS класс
kubectl get pod -n blockchain -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.qosClass}{"\n"}{end}'

# Проверить probes
kubectl describe pod miner-node-xxxxx -n blockchain | grep -A 5 "Liveness\|Readiness\|Startup"

# Симулировать fail liveness (если есть endpoint)
kubectl exec miner-node-xxxxx -n blockchain -- curl -X POST http://localhost:8080/test/hang

# Следить за restarts
kubectl get pods -n blockchain -w
```

---

## Частые ошибки

### 1. OOMKilled

```bash
kubectl describe pod my-pod
# Last State: Terminated
# Reason: OOMKilled

# Причина: приложение использует больше памяти чем limits
# Решение: увеличить limits.memory или оптимизировать приложение
```

### 2. CrashLoopBackOff из-за liveness probe

```bash
# Pod постоянно перезапускается
# Причина: liveness probe fail до готовности приложения
# Решение: добавить startupProbe или увеличить initialDelaySeconds
```

### 3. Pod Pending из-за resources

```bash
kubectl describe pod my-pod
# Events:
# Warning FailedScheduling 0/3 nodes available: 3 Insufficient cpu

# Причина: requests превышают доступные ресурсы
# Решение: уменьшить requests или добавить ноды
```

---

## Вопросы для самопроверки

1. **Чем requests отличается от limits?**

   <details>
   <summary>Ответ</summary>

   - **requests**: гарантированный минимум, используется для планирования
   - **limits**: максимум, превышение CPU → throttling, превышение memory → OOMKilled
   </details>

2. **Какие QoS классы существуют?**

   <details>
   <summary>Ответ</summary>

   - **Guaranteed**: requests = limits (последний на eviction)
   - **Burstable**: requests < limits (средний приоритет)
   - **BestEffort**: нет requests/limits (первый на eviction)
   </details>

3. **Чем liveness отличается от readiness probe?**

   <details>
   <summary>Ответ</summary>

   - **Liveness**: "приложение живо?" — fail → restart контейнера
   - **Readiness**: "готов к трафику?" — fail → убрать из Service (без restart)
   </details>

4. **Зачем нужен startupProbe?**

   <details>
   <summary>Ответ</summary>

   Для приложений с медленным стартом. Отключает liveness/readiness до успешного запуска, предотвращая преждевременные restarts.
   </details>

5. **Зачем preStop hook с sleep?**

   <details>
   <summary>Ответ</summary>

   Даёт время kube-proxy обновить правила и убрать Pod из Service endpoints до получения SIGTERM. Предотвращает 502 ошибки при rolling update.
   </details>

---

## Итоги урока

| Концепт | Назначение |
|---------|------------|
| **requests** | Гарантированный минимум ресурсов |
| **limits** | Максимум ресурсов |
| **QoS** | Приоритет при eviction |
| **startupProbe** | Проверка запуска |
| **livenessProbe** | Проверка "живости" |
| **readinessProbe** | Проверка готовности к трафику |
| **preStop** | Hook перед SIGTERM |
| **PDB** | Защита от массового disruption |

В следующем уроке изучим **Stateful Applications** — PersistentVolumes и StatefulSets.
