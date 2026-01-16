# Урок 12: Stateful Applications — PersistentVolumes, PVC, StatefulSets

## Цели урока

После этого урока вы будете:
- Понимать разницу между stateless и stateful приложениями
- Знать, как работают PersistentVolume и PersistentVolumeClaim
- Уметь настраивать StorageClass
- Понимать StatefulSet и его отличия от Deployment
- Знать, когда использовать какой подход

---

## Stateless vs Stateful

### Stateless приложения

```
┌─────────────────────────────────────────────────────────────┐
│                 STATELESS ПРИЛОЖЕНИЕ                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Характеристики:                                           │
│   • Не хранит данные локально                              │
│   • Любой Pod может обработать любой запрос                │
│   • Pod взаимозаменяемы                                    │
│   • Состояние хранится во внешних сервисах (БД, Redis)     │
│                                                             │
│   Примеры:                                                  │
│   • REST API серверы                                       │
│   • Web frontend                                           │
│   • Микросервисы без локального состояния                  │
│                                                             │
│   В K8s: Deployment                                         │
│   ┌─────────┐  ┌─────────┐  ┌─────────┐                    │
│   │ Pod A   │  │ Pod B   │  │ Pod C   │   ← Идентичны      │
│   │ abc123  │  │ def456  │  │ ghi789  │   ← Случайные имена│
│   └─────────┘  └─────────┘  └─────────┘                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Stateful приложения

```
┌─────────────────────────────────────────────────────────────┐
│                  STATEFUL ПРИЛОЖЕНИЕ                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Характеристики:                                           │
│   • Хранит данные на диске                                 │
│   • Pod НЕ взаимозаменяемы                                 │
│   • Нужна стабильная сетевая идентичность                  │
│   • Порядок запуска/остановки важен                        │
│                                                             │
│   Примеры:                                                  │
│   • Базы данных (PostgreSQL, MySQL, MongoDB)               │
│   • Очереди сообщений (Kafka, RabbitMQ)                    │
│   • Кэши (Redis с persistence)                             │
│   • Blockchain ноды (хранят chain локально)                │
│                                                             │
│   В K8s: StatefulSet                                        │
│   ┌─────────┐  ┌─────────┐  ┌─────────┐                    │
│   │ Pod-0   │  │ Pod-1   │  │ Pod-2   │   ← Уникальны      │
│   │ db-0    │  │ db-1    │  │ db-2    │   ← Стабильные имена│
│   │ Vol-0   │  │ Vol-1   │  │ Vol-2   │   ← Свой storage   │
│   └─────────┘  └─────────┘  └─────────┘                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Volumes в Kubernetes

### Проблема: данные в контейнере эфемерны

```
┌─────────────────────────────────────────────────────────────┐
│              БЕЗ PERSISTENT STORAGE                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Pod запущен:                                              │
│   ┌─────────────────────────────────────────────┐           │
│   │  Container                                  │           │
│   │  /data/blockchain.db  ← 10GB данных        │           │
│   └─────────────────────────────────────────────┘           │
│                                                             │
│   Pod перезапущен (crash, update, reschedule):              │
│   ┌─────────────────────────────────────────────┐           │
│   │  Container                                  │           │
│   │  /data/blockchain.db  ← ПУСТО! Данные lost │           │
│   └─────────────────────────────────────────────┘           │
│                                                             │
│   Файловая система контейнера = временная                   │
│   При перезапуске всё сбрасывается к образу                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Типы Volumes

```
┌─────────────────────────────────────────────────────────────┐
│                    ТИПЫ VOLUMES                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. emptyDir                                                │
│     • Пустая директория при создании Pod                   │
│     • Разделяется между контейнерами в Pod                 │
│     • Удаляется при удалении Pod                           │
│     • Для: временные файлы, кэш, shared data              │
│                                                             │
│  2. hostPath                                                │
│     • Монтирует путь с Node                                │
│     • Данные остаются на Node                              │
│     • Опасно! Pod привязан к конкретному Node             │
│     • Для: доступ к /var/log, /dev, DaemonSets            │
│                                                             │
│  3. PersistentVolume (PV)                                   │
│     • Независимый от Pod storage                           │
│     • Жизненный цикл отдельно от Pod                       │
│     • Для: базы данных, stateful приложения               │
│                                                             │
│  4. ConfigMap / Secret                                      │
│     • Монтирование конфигурации как файлов                │
│     • Read-only                                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Пример emptyDir

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: shared-data
spec:
  containers:
    - name: writer
      image: busybox
      command: ["sh", "-c", "echo 'Hello' > /data/file.txt && sleep 3600"]
      volumeMounts:
        - name: shared
          mountPath: /data

    - name: reader
      image: busybox
      command: ["sh", "-c", "cat /data/file.txt && sleep 3600"]
      volumeMounts:
        - name: shared
          mountPath: /data

  volumes:
    - name: shared
      emptyDir: {}
      # emptyDir:
      #   sizeLimit: 500Mi   # Ограничение размера
      #   medium: Memory     # RAM-диск (быстро, но ограничено)
```

---

## PersistentVolume и PersistentVolumeClaim

### Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│           PV / PVC / StorageClass                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Администратор кластера        │  Разработчик              │
│   ─────────────────────────     │  ───────────              │
│                                 │                           │
│   StorageClass                  │                           │
│   "Какие типы storage           │                           │
│    доступны в кластере"         │                           │
│         │                       │                           │
│         ▼                       │                           │
│   PersistentVolume (PV)         │  PersistentVolumeClaim   │
│   "Реальный storage"            │  (PVC)                    │
│   • 100GB SSD                   │  "Мне нужен storage"      │
│   • NFS share                   │  • 10GB                   │
│   • AWS EBS                     │  • ReadWriteOnce          │
│         │                       │         │                 │
│         └───────────────────────┴─────────┘                 │
│                      BINDING                                │
│                         │                                   │
│                         ▼                                   │
│                       Pod                                   │
│                    volumeMounts                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### PersistentVolume (PV)

```yaml
# Создаётся администратором (или динамически через StorageClass)
apiVersion: v1
kind: PersistentVolume
metadata:
  name: blockchain-pv
spec:
  capacity:
    storage: 10Gi                    # Размер
  accessModes:
    - ReadWriteOnce                  # Режим доступа
  persistentVolumeReclaimPolicy: Retain  # Что делать при удалении PVC
  storageClassName: standard         # Класс storage
  # Тип storage (один из):
  hostPath:                          # Локальный путь на Node
    path: /data/blockchain
  # nfs:
  #   server: nfs-server.local
  #   path: /exports/blockchain
  # awsElasticBlockStore:
  #   volumeID: vol-xxxxx
  #   fsType: ext4
```

### Access Modes

```
┌─────────────────────────────────────────────────────────────┐
│                   ACCESS MODES                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ReadWriteOnce (RWO):                                       │
│  • Один Node может монтировать для чтения/записи           │
│  • Несколько Pod на ОДНОМ Node — ОК                        │
│  • Типично для: блочные устройства (EBS, GCE PD)           │
│                                                             │
│  ReadOnlyMany (ROX):                                        │
│  • Много Node могут монтировать только для чтения          │
│  • Для: shared config, статические данные                  │
│                                                             │
│  ReadWriteMany (RWX):                                       │
│  • Много Node могут монтировать для чтения/записи          │
│  • Редко! Требует shared storage (NFS, CephFS, EFS)        │
│  • Для: shared uploads, logs                               │
│                                                             │
│  ReadWriteOncePod (RWOP) — K8s 1.22+:                       │
│  • Только один Pod может монтировать                       │
│  • Строже чем RWO                                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### PersistentVolumeClaim (PVC)

```yaml
# Создаётся разработчиком — "запрос" на storage
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: blockchain-data
  namespace: blockchain
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi           # Нужно минимум 5GB
  storageClassName: standard  # Из какого класса
  # selector:                 # Опционально: выбрать конкретный PV
  #   matchLabels:
  #     app: blockchain
```

### Использование PVC в Pod

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: blockchain-node
spec:
  containers:
    - name: node
      image: blockchain-node:latest
      volumeMounts:
        - name: data
          mountPath: /app/data      # Куда монтировать
  volumes:
    - name: data
      persistentVolumeClaim:
        claimName: blockchain-data  # Имя PVC
```

### Жизненный цикл PV

```
┌─────────────────────────────────────────────────────────────┐
│              ЖИЗНЕННЫЙ ЦИКЛ PV                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Available ──────► Bound ──────► Released ──────► ?        │
│       │               │              │              │       │
│       │               │              │              │       │
│   PV создан      PVC привязан    PVC удалён    Зависит от   │
│   ждёт PVC       данные есть     PV свободен   reclaim policy│
│                                                             │
│   Reclaim Policies:                                         │
│   • Retain:  PV остаётся, данные сохранены, ручная очистка │
│   • Delete:  PV и данные удаляются автоматически           │
│   • Recycle: (deprecated) очистка и повторное использование│
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## StorageClass

### Динамическое создание PV

```yaml
# StorageClass позволяет создавать PV автоматически
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast-ssd
provisioner: kubernetes.io/aws-ebs   # Провайдер
parameters:
  type: gp3                          # Тип диска
  iops: "3000"
  throughput: "125"
reclaimPolicy: Delete                # При удалении PVC
volumeBindingMode: WaitForFirstConsumer  # Когда создавать PV
allowVolumeExpansion: true           # Можно расширять
```

### Volume Binding Modes

```
┌─────────────────────────────────────────────────────────────┐
│              VOLUME BINDING MODES                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Immediate (по умолчанию):                                  │
│  • PV создаётся сразу при создании PVC                     │
│  • Может создаться в "неправильной" зоне                   │
│  • Pod может не запуститься, если PV в другой зоне         │
│                                                             │
│  WaitForFirstConsumer:                                      │
│  • PV создаётся когда Pod пытается использовать PVC        │
│  • PV создаётся в той же зоне, где Pod                     │
│  • Рекомендуется для zonal storage                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### StorageClass в Kind

```yaml
# Kind использует local-path provisioner
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: standard
  annotations:
    storageclass.kubernetes.io/is-default-class: "true"
provisioner: rancher.io/local-path
volumeBindingMode: WaitForFirstConsumer
reclaimPolicy: Delete
```

```bash
# Проверить StorageClass в кластере
kubectl get storageclass
# NAME                 PROVISIONER             RECLAIMPOLICY
# standard (default)   rancher.io/local-path   Delete
```

---

## StatefulSet

### Отличия от Deployment

```
┌─────────────────────────────────────────────────────────────┐
│           DEPLOYMENT vs STATEFULSET                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Deployment:                    StatefulSet:                │
│  ───────────                    ────────────                │
│  • Случайные имена Pod          • Порядковые имена          │
│    (nginx-abc123)                 (nginx-0, nginx-1)        │
│                                                             │
│  • Все Pod идентичны            • Каждый Pod уникален       │
│                                                             │
│  • Общий storage (если есть)    • Свой PVC для каждого Pod │
│                                                             │
│  • Параллельное создание        • Последовательное          │
│                                   (0 → 1 → 2)               │
│                                                             │
│  • Любой порядок удаления       • Обратный порядок          │
│                                   (2 → 1 → 0)               │
│                                                             │
│  • Нет stable network ID        • Stable DNS для каждого:   │
│                                   pod-0.service-name        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Структура StatefulSet

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: database
spec:
  serviceName: postgres          # Обязательно! Headless Service
  replicas: 3
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:15
          ports:
            - containerPort: 5432
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data

  # VolumeClaimTemplate — создаёт PVC для каждого Pod
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: standard
        resources:
          requests:
            storage: 10Gi
```

### Stable Network Identity

```yaml
# Headless Service для StatefulSet
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: database
spec:
  clusterIP: None              # Headless!
  selector:
    app: postgres
  ports:
    - port: 5432
```

```
┌─────────────────────────────────────────────────────────────┐
│           STABLE DNS NAMES                                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   StatefulSet: postgres, Service: postgres                  │
│                                                             │
│   Pod Name      │ DNS Name                                  │
│   ──────────────┼─────────────────────────────────────────  │
│   postgres-0    │ postgres-0.postgres.database.svc.cluster.local │
│   postgres-1    │ postgres-1.postgres.database.svc.cluster.local │
│   postgres-2    │ postgres-2.postgres.database.svc.cluster.local │
│                                                             │
│   Приложение может обращаться к конкретному экземпляру:    │
│   • Primary:  postgres-0.postgres                           │
│   • Replica:  postgres-1.postgres, postgres-2.postgres      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### VolumeClaimTemplates

```
┌─────────────────────────────────────────────────────────────┐
│           VOLUMECLAIMTEMPLATES                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   StatefulSet создаёт PVC автоматически:                    │
│                                                             │
│   StatefulSet: postgres                                     │
│   volumeClaimTemplates:                                     │
│     - name: data                                            │
│                                                             │
│   Результат:                                                │
│   ┌─────────────────────────────────────────────────────┐   │
│   │  PVC: data-postgres-0  ──► PV-0  ──► postgres-0    │   │
│   │  PVC: data-postgres-1  ──► PV-1  ──► postgres-1    │   │
│   │  PVC: data-postgres-2  ──► PV-2  ──► postgres-2    │   │
│   └─────────────────────────────────────────────────────┘   │
│                                                             │
│   При удалении Pod:                                         │
│   • Pod удаляется                                          │
│   • PVC ОСТАЁТСЯ (данные сохранены!)                       │
│   • При пересоздании Pod получит тот же PVC                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Ordering Guarantees

```yaml
spec:
  podManagementPolicy: OrderedReady  # По умолчанию
  # или
  podManagementPolicy: Parallel      # Параллельное создание

# OrderedReady:
# Создание: postgres-0 (Ready) → postgres-1 (Ready) → postgres-2
# Удаление: postgres-2 → postgres-1 → postgres-0
# Обновление: postgres-2 → postgres-1 → postgres-0

# Parallel:
# Все Pod создаются/удаляются одновременно
# Для: когда порядок не важен
```

---

## Практика: StatefulSet для blockchain

### Когда blockchain ноде нужен StatefulSet?

```
┌─────────────────────────────────────────────────────────────┐
│           BLOCKCHAIN: STATELESS ИЛИ STATEFUL?                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Наш blockchain-node:                                      │
│                                                             │
│   Stateless вариант (Deployment):                           │
│   • Chain хранится в памяти                                │
│   • При рестарте синхронизируется с peers                  │
│   • Проще, но медленный старт при большом chain            │
│                                                             │
│   Stateful вариант (StatefulSet):                           │
│   • Chain хранится на диске                                │
│   • Быстрый старт (читает с диска)                         │
│   • Нужен persistent volume                                │
│   • Стабильные имена для peer discovery                    │
│                                                             │
│   Выбор зависит от размера chain и требований              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Пример StatefulSet для blockchain

```yaml
# k8s/blockchain-statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: blockchain-node
  namespace: blockchain
spec:
  serviceName: blockchain-nodes  # Headless Service
  replicas: 3
  selector:
    matchLabels:
      app: blockchain-node
  template:
    metadata:
      labels:
        app: blockchain-node
    spec:
      containers:
        - name: node
          image: blockchain-node:latest
          ports:
            - name: http
              containerPort: 8080
          env:
            - name: NODE_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: DATA_DIR
              value: /data/blockchain
          volumeMounts:
            - name: data
              mountPath: /data
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "1000m"

  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: standard
        resources:
          requests:
            storage: 1Gi
---
# Headless Service
apiVersion: v1
kind: Service
metadata:
  name: blockchain-nodes
  namespace: blockchain
spec:
  clusterIP: None
  selector:
    app: blockchain-node
  ports:
    - port: 8080
      name: http
```

### Проверка

```bash
# Применить
kubectl apply -f k8s/blockchain-statefulset.yaml

# Проверить Pod (порядковые имена)
kubectl get pods -n blockchain
# NAME                 READY   STATUS    RESTARTS   AGE
# blockchain-node-0    1/1     Running   0          2m
# blockchain-node-1    1/1     Running   0          1m
# blockchain-node-2    1/1     Running   0          30s

# Проверить PVC (автоматически созданы)
kubectl get pvc -n blockchain
# NAME                      STATUS   VOLUME    CAPACITY   ACCESS MODES
# data-blockchain-node-0    Bound    pvc-xxx   1Gi        RWO
# data-blockchain-node-1    Bound    pvc-yyy   1Gi        RWO
# data-blockchain-node-2    Bound    pvc-zzz   1Gi        RWO

# DNS resolution
kubectl run dns-test --image=busybox -n blockchain --rm -it -- nslookup blockchain-node-0.blockchain-nodes
# Address: 10.244.0.x

# Удалить Pod — данные сохранятся
kubectl delete pod blockchain-node-0 -n blockchain
kubectl get pvc -n blockchain  # PVC всё ещё есть!
```

---

## Расширение Volume

```yaml
# StorageClass должен поддерживать expansion
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: expandable
provisioner: kubernetes.io/aws-ebs
allowVolumeExpansion: true    # ← Важно!
```

```bash
# Увеличить PVC
kubectl patch pvc data-blockchain-node-0 -n blockchain \
  -p '{"spec":{"resources":{"requests":{"storage":"5Gi"}}}}'

# Проверить
kubectl get pvc data-blockchain-node-0 -n blockchain
# Может потребоваться рестарт Pod для применения
```

---

## Частые ошибки

### 1. PVC в Pending

```bash
kubectl get pvc -n blockchain
# NAME   STATUS    VOLUME   CAPACITY   ACCESS MODES
# data   Pending                                     # ← Проблема!

kubectl describe pvc data -n blockchain
# Events:
# Warning  ProvisioningFailed  no persistent volumes available

# Причины:
# • Нет подходящего PV
# • StorageClass не существует или не может создать PV
# • Не хватает ресурсов storage
```

### 2. Pod stuck в ContainerCreating

```bash
kubectl describe pod blockchain-node-0 -n blockchain
# Events:
# Warning  FailedMount  Unable to attach or mount volumes

# Причины:
# • PV в другой зоне (используйте WaitForFirstConsumer)
# • Volume уже примонтирован к другому Pod (RWO)
# • Проблемы с storage backend
```

### 3. StatefulSet не удаляется

```bash
# Pod'ы удаляются в обратном порядке
# Если Pod-2 завис, Pod-1 и Pod-0 ждут

# Принудительное удаление
kubectl delete statefulset blockchain-node -n blockchain --cascade=orphan
kubectl delete pod blockchain-node-2 -n blockchain --force --grace-period=0
```

---

## Вопросы для самопроверки

1. **Когда использовать StatefulSet вместо Deployment?**

   <details>
   <summary>Ответ</summary>

   StatefulSet когда нужны:
   - Стабильные сетевые идентификаторы
   - Персистентное хранилище для каждого Pod
   - Упорядоченное создание/удаление
   - Уникальность Pod (не взаимозаменяемы)
   </details>

2. **Чем PV отличается от PVC?**

   <details>
   <summary>Ответ</summary>

   - **PV**: реальный storage ресурс (создаётся админом или динамически)
   - **PVC**: запрос на storage от разработчика
   - PVC "привязывается" к подходящему PV
   </details>

3. **Что такое StorageClass?**

   <details>
   <summary>Ответ</summary>

   StorageClass описывает "классы" доступного storage и позволяет динамически создавать PV при создании PVC. Содержит provisioner, параметры и политики.
   </details>

4. **Что происходит с PVC при удалении StatefulSet Pod?**

   <details>
   <summary>Ответ</summary>

   PVC сохраняется! При пересоздании Pod с тем же именем (ordinal) он получит тот же PVC с данными. Для удаления PVC нужно удалить их отдельно.
   </details>

5. **Что такое Headless Service и зачем он нужен StatefulSet?**

   <details>
   <summary>Ответ</summary>

   Headless Service (clusterIP: None) нужен для создания стабильных DNS записей для каждого Pod: `pod-name.service-name.namespace.svc.cluster.local`. Это позволяет обращаться к конкретному экземпляру.
   </details>

---

## Итоги урока

| Концепт | Назначение |
|---------|------------|
| **PersistentVolume (PV)** | Реальный storage ресурс |
| **PersistentVolumeClaim (PVC)** | Запрос на storage |
| **StorageClass** | Шаблон для динамического создания PV |
| **StatefulSet** | Контроллер для stateful приложений |
| **volumeClaimTemplates** | Автоматическое создание PVC в StatefulSet |
| **Headless Service** | Stable DNS для Pod в StatefulSet |

| Access Mode | Описание |
|-------------|----------|
| **RWO** | Один Node чтение/запись |
| **ROX** | Много Node только чтение |
| **RWX** | Много Node чтение/запись |

В следующем уроке изучим **Helm** — пакетный менеджер для Kubernetes.
