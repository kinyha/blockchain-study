# Урок 7: Workloads — Deployments, ReplicaSets, Labels & Selectors

## Цели урока

После этого урока вы будете:
- Понимать иерархию Deployment → ReplicaSet → Pod
- Уметь создавать и управлять Deployments
- Понимать механизм Labels и Selectors
- Знать стратегии обновления (Rolling Update, Recreate)
- Уметь откатывать неудачные деплои

---

## Почему не просто Pod?

В прошлом уроке мы создавали Pod напрямую. Но в продакшене так не делают:

```
┌─────────────────────────────────────────────────────────────┐
│               ПРОБЛЕМЫ ГОЛОГО POD                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Pod упал                    → Никто не перезапустит        │
│  Нужно 3 копии               → Создавать вручную            │
│  Обновить версию             → Удалить старые, создать новые│
│  Откатить неудачный деплой   → Помнить предыдущую версию    │
│  Масштабировать              → Редактировать вручную        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Решение**: использовать контроллеры, которые управляют подами за нас.

---

## Иерархия Workloads

```
┌─────────────────────────────────────────────────────────────┐
│                    DEPLOYMENT                                │
│              (декларативные обновления)                      │
│                         │                                    │
│                         │ создаёт и управляет                │
│                         ▼                                    │
│               ┌─────────────────┐                            │
│               │   REPLICASET    │                            │
│               │ (поддерживает   │                            │
│               │  N копий Pod)   │                            │
│               └────────┬────────┘                            │
│                        │                                     │
│          ┌─────────────┼─────────────┐                       │
│          │             │             │                       │
│          ▼             ▼             ▼                       │
│     ┌────────┐    ┌────────┐    ┌────────┐                   │
│     │  POD   │    │  POD   │    │  POD   │                   │
│     └────────┘    └────────┘    └────────┘                   │
│                                                              │
└──────────────────────────────────────────────────────────────┘

Deployment: "Хочу 3 копии nginx:1.21, с rolling update"
     │
     └──► ReplicaSet: "Поддерживаю 3 пода с nginx:1.21"
              │
              └──► Pod 1, Pod 2, Pod 3
```

---

## Labels и Selectors — основа всего

### Что такое Labels

**Labels** — это key-value пары, прикреплённые к объектам K8s.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: blockchain-node-1
  labels:                          # Labels
    app: blockchain                # app=blockchain
    component: miner               # component=miner
    version: v1.0.0               # version=v1.0.0
    environment: production        # environment=production
spec:
  containers:
    - name: node
      image: blockchain-node:v1.0.0
```

**Зачем нужны Labels:**
- Группировка и организация ресурсов
- Выборка (Selectors) для Services, Deployments
- Фильтрация в kubectl
- Не влияют на поведение (в отличие от annotations)

### Как работают Selectors

**Selector** — запрос, который выбирает объекты по Labels.

```
┌─────────────────────────────────────────────────────────────┐
│                    SELECTOR В ДЕЙСТВИИ                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Service (selector: app=blockchain)                         │
│           │                                                 │
│           │  Выбирает все Pod с label app=blockchain        │
│           │                                                 │
│           ▼                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ Pod          │  │ Pod          │  │ Pod          │       │
│  │ app=blockchain│ │ app=blockchain│ │ app=nginx    │ ✗     │
│  │ role=miner   │  │ role=wallet  │  │              │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│        ✓                 ✓                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Типы Selectors

```yaml
# 1. Equality-based (точное совпадение)
selector:
  matchLabels:
    app: blockchain
    component: miner
# Эквивалентно: app=blockchain AND component=miner

# 2. Set-based (множества)
selector:
  matchExpressions:
    - key: environment
      operator: In
      values: [production, staging]
    - key: component
      operator: NotIn
      values: [test]
    - key: release
      operator: Exists
    - key: deprecated
      operator: DoesNotExist

# Операторы:
# In         — значение в списке
# NotIn      — значение НЕ в списке
# Exists     — label существует (значение не важно)
# DoesNotExist — label НЕ существует
```

### Использование в kubectl

```bash
# Фильтрация по labels
kubectl get pods -l app=blockchain
kubectl get pods -l app=blockchain,component=miner
kubectl get pods -l 'environment in (prod, staging)'
kubectl get pods -l 'app!=nginx'

# Показать labels
kubectl get pods --show-labels

# Добавить label
kubectl label pod nginx-abc123 environment=production

# Удалить label
kubectl label pod nginx-abc123 environment-

# Изменить label
kubectl label pod nginx-abc123 version=v2.0.0 --overwrite
```

---

## ReplicaSet

### Что такое ReplicaSet

**ReplicaSet** гарантирует, что заданное количество Pod всегда запущено.

```yaml
# replicaset.yaml
apiVersion: apps/v1
kind: ReplicaSet
metadata:
  name: blockchain-node-rs
  labels:
    app: blockchain
spec:
  replicas: 3                      # Желаемое количество
  selector:                        # Какие поды считать своими
    matchLabels:
      app: blockchain
      managed-by: blockchain-node-rs
  template:                        # Шаблон для создания Pod
    metadata:
      labels:
        app: blockchain
        managed-by: blockchain-node-rs
    spec:
      containers:
        - name: node
          image: blockchain-node:v1.0.0
          ports:
            - containerPort: 8080
```

### Как работает ReplicaSet

```
┌─────────────────────────────────────────────────────────────┐
│                 CONTROL LOOP REPLICASET                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   while true:                                               │
│       current = count(pods matching selector)               │
│       desired = spec.replicas                               │
│                                                             │
│       if current < desired:                                 │
│           create_pod(template)     # Недостаточно подов     │
│                                                             │
│       if current > desired:                                 │
│           delete_pod(oldest)       # Слишком много подов    │
│                                                             │
│       sleep(reconcile_interval)                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘

Пример:
• replicas: 3
• Сейчас 2 Pod → создаст 1
• Сейчас 4 Pod → удалит 1
• Pod упал → создаст новый
```

### Почему не использовать ReplicaSet напрямую

```
┌─────────────────────────────────────────────────────────────┐
│              REPLICASET НАПРЯМУЮ — ПЛОХО                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  При обновлении образа:                                     │
│                                                             │
│  ReplicaSet (image: v1.0.0)                                 │
│       │                                                     │
│       │  Изменили на image: v2.0.0                          │
│       │                                                     │
│       ▼                                                     │
│  Старые Pod НЕ обновятся!                                   │
│  ReplicaSet не пересоздаёт Pod при изменении template       │
│                                                             │
│  Нужно вручную:                                             │
│  1. Удалить старый ReplicaSet                               │
│  2. Создать новый                                           │
│  3. Или удалить поды руками                                 │
│                                                             │
│  → Даунтайм, ручная работа, нет истории                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Решение**: использовать Deployment, который управляет ReplicaSets.

---

## Deployment

### Что такое Deployment

**Deployment** — контроллер для декларативного управления ReplicaSets и Pods.

```yaml
# k8s/miner-deployment.yaml (из нашего проекта)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: miner-node
  namespace: blockchain
  labels:
    app.kubernetes.io/name: miner-node
    app.kubernetes.io/component: miner
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: miner-node
  template:
    metadata:
      labels:
        app.kubernetes.io/name: miner-node
        app.kubernetes.io/component: miner
    spec:
      containers:
        - name: blockchain-node
          image: blockchain-node:latest
          ports:
            - name: http
              containerPort: 8080
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "1000m"
```

### Структура Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app              # Имя Deployment
  namespace: default        # Namespace
  labels:                   # Labels самого Deployment
    app: my-app
spec:
  replicas: 3               # Количество Pod

  selector:                 # Как найти "свои" Pod
    matchLabels:            # ДОЛЖЕН совпадать с template.metadata.labels
      app: my-app

  template:                 # Шаблон Pod (вложенный PodSpec)
    metadata:
      labels:               # Labels для создаваемых Pod
        app: my-app         # Должен совпадать с selector!
    spec:
      containers:           # Контейнеры в Pod
        - name: app
          image: my-app:v1.0.0
          ports:
            - containerPort: 8080
```

### Важное правило: selector = template.labels

```yaml
# ❌ НЕПРАВИЛЬНО — selector не совпадает с labels
spec:
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        application: my-app    # Другой key!

# ✓ ПРАВИЛЬНО — selector совпадает с labels
spec:
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        app: my-app           # Совпадает!
        version: v1           # Можно добавлять дополнительные
```

---

## Стратегии обновления

### Rolling Update (по умолчанию)

```
┌─────────────────────────────────────────────────────────────┐
│                    ROLLING UPDATE                            │
│              Постепенное обновление без даунтайма            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Начало: 3 Pod v1.0.0                                       │
│  ┌─────┐  ┌─────┐  ┌─────┐                                  │
│  │v1.0 │  │v1.0 │  │v1.0 │                                  │
│  └─────┘  └─────┘  └─────┘                                  │
│                                                             │
│  Шаг 1: Создаём 1 новый, удаляем 1 старый                   │
│  ┌─────┐  ┌─────┐  ┌─────┐                                  │
│  │v2.0 │  │v1.0 │  │v1.0 │                                  │
│  └─────┘  └─────┘  └─────┘                                  │
│                                                             │
│  Шаг 2:                                                     │
│  ┌─────┐  ┌─────┐  ┌─────┐                                  │
│  │v2.0 │  │v2.0 │  │v1.0 │                                  │
│  └─────┘  └─────┘  └─────┘                                  │
│                                                             │
│  Шаг 3: Готово!                                             │
│  ┌─────┐  ┌─────┐  ┌─────┐                                  │
│  │v2.0 │  │v2.0 │  │v2.0 │                                  │
│  └─────┘  └─────┘  └─────┘                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1        # Сколько ЛИШНИХ Pod можно создать
      maxUnavailable: 0  # Сколько Pod может быть недоступно

# maxSurge: 1, maxUnavailable: 0
# При replicas: 3 → во время обновления будет 3-4 Pod
# Гарантия: всегда минимум 3 Pod доступно

# maxSurge: 0, maxUnavailable: 1
# При replicas: 3 → во время обновления будет 2-3 Pod
# Экономит ресурсы, но возможна меньшая доступность

# maxSurge: 25%, maxUnavailable: 25%
# Процентные значения от replicas
```

### Recreate

```
┌─────────────────────────────────────────────────────────────┐
│                       RECREATE                               │
│           Удалить все старые, потом создать новые            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Начало: 3 Pod v1.0.0                                       │
│  ┌─────┐  ┌─────┐  ┌─────┐                                  │
│  │v1.0 │  │v1.0 │  │v1.0 │                                  │
│  └─────┘  └─────┘  └─────┘                                  │
│                                                             │
│  Шаг 1: Удаляем ВСЕ старые                                  │
│                                                             │
│  (пусто — ДАУНТАЙМ!)                                        │
│                                                             │
│  Шаг 2: Создаём ВСЕ новые                                   │
│  ┌─────┐  ┌─────┐  ┌─────┐                                  │
│  │v2.0 │  │v2.0 │  │v2.0 │                                  │
│  └─────┘  └─────┘  └─────┘                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘

Когда использовать:
• Нельзя запускать 2 версии одновременно
• База данных с миграциями
• Приложение не поддерживает несколько версий
```

```yaml
spec:
  strategy:
    type: Recreate
```

---

## Управление Deployment

### Создание и просмотр

```bash
# Создать Deployment
kubectl apply -f deployment.yaml

# Просмотр
kubectl get deployments
kubectl get deploy              # Сокращённо

# NAME         READY   UP-TO-DATE   AVAILABLE   AGE
# miner-node   1/1     1            1           5m

# READY:      текущие/желаемые готовые реплики
# UP-TO-DATE: сколько обновлено до последней версии
# AVAILABLE:  сколько доступно для трафика

# Детали
kubectl describe deployment miner-node

# Связанные ReplicaSets
kubectl get replicasets
kubectl get rs

# NAME                    DESIRED   CURRENT   READY   AGE
# miner-node-7d9f8b6c5f   1         1         1       5m
```

### Масштабирование

```bash
# Императивно (для быстрого теста)
kubectl scale deployment miner-node --replicas=3

# Декларативно (правильный способ)
# Изменить replicas в YAML и применить
kubectl apply -f deployment.yaml

# Автоскейлинг (Horizontal Pod Autoscaler)
kubectl autoscale deployment miner-node \
  --min=1 --max=5 --cpu-percent=80
```

### Обновление

```bash
# Способ 1: Изменить YAML и применить
# Отредактировать image в deployment.yaml
kubectl apply -f deployment.yaml

# Способ 2: Императивно (для быстрого теста)
kubectl set image deployment/miner-node \
  blockchain-node=blockchain-node:v2.0.0

# Следить за процессом
kubectl rollout status deployment/miner-node
# Waiting for deployment "miner-node" rollout to finish:
# 1 out of 3 new replicas have been updated...
# deployment "miner-node" successfully rolled out

# История ревизий
kubectl rollout history deployment/miner-node
# REVISION  CHANGE-CAUSE
# 1         <none>
# 2         <none>
```

### Откат (Rollback)

```bash
# Откатить на предыдущую версию
kubectl rollout undo deployment/miner-node

# Откатить на конкретную ревизию
kubectl rollout undo deployment/miner-node --to-revision=1

# Пауза/возобновление rollout
kubectl rollout pause deployment/miner-node
kubectl rollout resume deployment/miner-node
```

---

## Как работает обновление под капотом

```
┌─────────────────────────────────────────────────────────────┐
│            DEPLOYMENT УПРАВЛЯЕТ REPLICASETS                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  До обновления:                                             │
│                                                             │
│  Deployment (miner-node, image: v1.0.0)                     │
│       │                                                     │
│       └──► ReplicaSet-A (replicas: 3, image: v1.0.0)        │
│                  │                                          │
│                  ├──► Pod 1 (v1.0.0)                        │
│                  ├──► Pod 2 (v1.0.0)                        │
│                  └──► Pod 3 (v1.0.0)                        │
│                                                             │
│  ═══════════════════════════════════════════════════════    │
│                                                             │
│  После: kubectl set image ... image: v2.0.0                 │
│                                                             │
│  Deployment (miner-node, image: v2.0.0)                     │
│       │                                                     │
│       ├──► ReplicaSet-A (replicas: 0, image: v1.0.0)        │
│       │          (сохранён для rollback!)                   │
│       │                                                     │
│       └──► ReplicaSet-B (replicas: 3, image: v2.0.0)        │
│                  │                                          │
│                  ├──► Pod 1 (v2.0.0)                        │
│                  ├──► Pod 2 (v2.0.0)                        │
│                  └──► Pod 3 (v2.0.0)                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘

При rollback:
• ReplicaSet-B масштабируется до 0
• ReplicaSet-A масштабируется до 3
• Поды пересоздаются из старого ReplicaSet
```

```bash
# Посмотреть все ReplicaSets для Deployment
kubectl get rs -l app.kubernetes.io/name=miner-node

# NAME                    DESIRED   CURRENT   READY   AGE
# miner-node-7d9f8b6c5f   3         3         3       10m   # Текущий
# miner-node-5b8c7d9e4a   0         0         0       15m   # Предыдущий
```

---

## Рекомендуемые Labels (Kubernetes conventions)

```yaml
metadata:
  labels:
    # Рекомендуемые K8s labels (app.kubernetes.io/*)
    app.kubernetes.io/name: miner-node       # Имя компонента
    app.kubernetes.io/instance: miner-1      # Экземпляр
    app.kubernetes.io/version: "1.0.0"       # Версия
    app.kubernetes.io/component: miner       # Компонент в архитектуре
    app.kubernetes.io/part-of: blockchain    # Часть какого приложения
    app.kubernetes.io/managed-by: helm       # Чем управляется

    # Кастомные labels проекта
    blockchain.io/role: miner
    blockchain.io/network: mainnet
```

---

## Практика: blockchain Deployment

### Шаг 1: Разбор нашего Deployment

```yaml
# k8s/miner-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: miner-node
  namespace: blockchain
  labels:
    app.kubernetes.io/name: miner-node
    app.kubernetes.io/component: miner
    app.kubernetes.io/part-of: blockchain-network
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: miner-node     # Ищет Pod с этим label
  template:
    metadata:
      labels:
        app.kubernetes.io/name: miner-node   # Label для Pod
        app.kubernetes.io/component: miner
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000

      containers:
        - name: blockchain-node
          image: blockchain-node:latest
          imagePullPolicy: IfNotPresent

          ports:
            - name: http
              containerPort: 8080

          env:
            - name: NODE_ROLE
              value: "miner"
            - name: SPRING_PROFILES_ACTIVE
              value: "miner"

          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "1000m"

          livenessProbe:
            httpGet:
              path: /api/node/health
              port: http
            initialDelaySeconds: 30
            periodSeconds: 10

          readinessProbe:
            httpGet:
              path: /api/node/health
              port: http
            initialDelaySeconds: 10
            periodSeconds: 5
```

### Шаг 2: Применение

```bash
# Создаём namespace
kubectl create namespace blockchain

# Применяем Deployment
kubectl apply -f k8s/miner-deployment.yaml

# Проверяем
kubectl get deployments -n blockchain
kubectl get pods -n blockchain
kubectl describe deployment miner-node -n blockchain
```

### Шаг 3: Эксперименты

```bash
# Масштабирование
kubectl scale deployment miner-node -n blockchain --replicas=3
kubectl get pods -n blockchain -w  # Следить за созданием

# Удалить Pod (ReplicaSet создаст новый)
kubectl delete pod miner-node-xxxxx -n blockchain
kubectl get pods -n blockchain  # Новый Pod уже создаётся

# Обновление образа
kubectl set image deployment/miner-node \
  -n blockchain \
  blockchain-node=blockchain-node:v2.0.0

kubectl rollout status deployment/miner-node -n blockchain

# Откат
kubectl rollout undo deployment/miner-node -n blockchain
```

---

## Другие типы Workloads

### DaemonSet — Pod на каждом узле

```yaml
# Запускает ровно 1 Pod на каждом Node
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: log-collector
spec:
  selector:
    matchLabels:
      app: log-collector
  template:
    metadata:
      labels:
        app: log-collector
    spec:
      containers:
        - name: fluentd
          image: fluentd:latest
```

**Использование**: логирование, мониторинг, сетевые агенты.

### StatefulSet — для stateful приложений

```yaml
# Для приложений с состоянием (БД, очереди)
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
spec:
  serviceName: postgres
  replicas: 3
  selector:
    matchLabels:
      app: postgres
  template:
    # ...
```

**Особенности**:
- Стабильные имена Pod: postgres-0, postgres-1, postgres-2
- Стабильные сетевые идентификаторы
- Упорядоченное создание/удаление
- Персистентные volumes

### Job — однократное выполнение

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: database-migration
spec:
  template:
    spec:
      containers:
        - name: migration
          image: my-app:latest
          command: ["./migrate.sh"]
      restartPolicy: Never
  backoffLimit: 3  # Сколько раз пытаться при ошибке
```

### CronJob — периодическое выполнение

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: backup
spec:
  schedule: "0 2 * * *"  # Каждый день в 2:00
  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: backup
              image: backup-tool:latest
          restartPolicy: Never
```

---

## Частые ошибки

### 1. Selector не совпадает с template labels

```yaml
# ❌ ОШИБКА
spec:
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        application: my-app  # Другой key!

# Результат: Deployment не найдёт свои Pod
# Error: selector does not match template labels
```

### 2. Забыли указать namespace

```bash
# Deployment в namespace blockchain
kubectl apply -f deployment.yaml

# А смотрим в default
kubectl get pods  # Пусто!

# Правильно
kubectl get pods -n blockchain
```

### 3. Image не существует или неверный tag

```bash
kubectl get pods
# NAME                          READY   STATUS             RESTARTS
# miner-node-xxxxx              0/1     ImagePullBackOff   0

kubectl describe pod miner-node-xxxxx
# Events:
#   Warning  Failed  kubelet  Failed to pull image "blockchain-node:v999"
```

### 4. Недостаточно ресурсов

```bash
kubectl get pods
# NAME                          READY   STATUS    RESTARTS
# miner-node-xxxxx              0/1     Pending   0

kubectl describe pod miner-node-xxxxx
# Events:
#   Warning  FailedScheduling  0/1 nodes available:
#            1 Insufficient memory.
```

---

## Вопросы для самопроверки

### Теоретические

1. **Чем Deployment отличается от ReplicaSet?**

   <details>
   <summary>Ответ</summary>

   ReplicaSet только поддерживает заданное количество Pod. Deployment добавляет:
   - Декларативные обновления (rolling update)
   - Историю ревизий и откат
   - Стратегии обновления
   - Управление несколькими ReplicaSets
   </details>

2. **Что такое Labels и Selectors?**

   <details>
   <summary>Ответ</summary>

   - **Labels** — key-value метаданные, прикреплённые к объектам
   - **Selectors** — запросы для выборки объектов по Labels
   - Используются для связи Deployment→Pod, Service→Pod
   </details>

3. **Чем Rolling Update отличается от Recreate?**

   <details>
   <summary>Ответ</summary>

   - **Rolling Update**: постепенная замена Pod, без даунтайма
   - **Recreate**: удаление всех старых Pod, потом создание новых, есть даунтайм

   Rolling Update — по умолчанию. Recreate — когда нельзя запускать 2 версии одновременно.
   </details>

4. **Что произойдёт, если удалить Pod, управляемый Deployment?**

   <details>
   <summary>Ответ</summary>

   ReplicaSet (созданный Deployment) автоматически создаст новый Pod взамен удалённого, чтобы поддержать желаемое количество реплик.
   </details>

5. **Зачем Deployment хранит старые ReplicaSets?**

   <details>
   <summary>Ответ</summary>

   Для возможности отката (rollback). Старые ReplicaSets содержат предыдущие версии Pod template. При откате Deployment просто масштабирует старый ReplicaSet обратно.
   </details>

### Практические

6. **Как посмотреть историю обновлений Deployment?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl rollout history deployment/my-deployment
   ```
   </details>

7. **Как откатить Deployment на предыдущую версию?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl rollout undo deployment/my-deployment

   # На конкретную ревизию
   kubectl rollout undo deployment/my-deployment --to-revision=2
   ```
   </details>

8. **Как масштабировать Deployment до 5 реплик?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl scale deployment/my-deployment --replicas=5

   # Или изменить replicas в YAML и применить
   kubectl apply -f deployment.yaml
   ```
   </details>

9. **Как получить все Pod с label app=blockchain?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl get pods -l app=blockchain
   ```
   </details>

10. **Почему selector в Deployment должен совпадать с template.metadata.labels?**

    <details>
    <summary>Ответ</summary>

    Deployment использует selector для поиска "своих" Pod. Если selector не совпадает с labels в template, Deployment не найдёт созданные им Pod и будет бесконечно создавать новые.
    </details>

---

## Итоги урока

| Концепт | Описание |
|---------|----------|
| **Labels** | Key-value метаданные для организации |
| **Selectors** | Запросы для выборки по Labels |
| **ReplicaSet** | Поддерживает N копий Pod |
| **Deployment** | Управляет ReplicaSets, обновления, откаты |
| **Rolling Update** | Постепенное обновление без даунтайма |
| **Recreate** | Удалить всё, потом создать заново |

| Команда | Действие |
|---------|----------|
| `kubectl get deploy` | Список Deployments |
| `kubectl rollout status` | Статус обновления |
| `kubectl rollout history` | История ревизий |
| `kubectl rollout undo` | Откат |
| `kubectl scale` | Масштабирование |

В следующем уроке изучим **Networking** — Services, DNS, и Ingress для доступа к приложениям.
