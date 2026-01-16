# Урок 9: Configuration — ConfigMaps, Secrets, Environment Variables

## Цели урока

После этого урока вы будете:
- Понимать, зачем отделять конфигурацию от кода
- Уметь создавать и использовать ConfigMaps
- Знать, как безопасно хранить секреты в Secrets
- Понимать способы передачи конфигурации в Pod
- Знать best practices для управления конфигурацией

---

## Зачем отделять конфигурацию от кода?

### Проблема: конфигурация внутри образа

```
┌─────────────────────────────────────────────────────────────┐
│           КОНФИГУРАЦИЯ В ОБРАЗЕ — ПЛОХО                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  # application.properties (внутри образа)                   │
│  blockchain.difficulty=5                                    │
│  database.url=jdbc:postgresql://prod-db:5432/blockchain     │
│  database.password=super_secret_password                    │
│                                                             │
│  Проблемы:                                                  │
│  ✗ Разные настройки для dev/staging/prod → разные образы   │
│  ✗ Секреты видны всем, кто имеет доступ к образу           │
│  ✗ Изменение конфигурации требует пересборки               │
│  ✗ Один образ нельзя использовать в разных окружениях      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Решение: 12-Factor App принцип

```
┌─────────────────────────────────────────────────────────────┐
│                    12-FACTOR APP                             │
│            "Store config in the environment"                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ОДИН образ → РАЗНЫЕ окружения через конфигурацию          │
│                                                             │
│  ┌─────────────────┐                                        │
│  │  Docker Image   │                                        │
│  │  blockchain:1.0 │                                        │
│  └────────┬────────┘                                        │
│           │                                                 │
│     ┌─────┴─────┬─────────────┐                             │
│     │           │             │                             │
│     ▼           ▼             ▼                             │
│  ┌──────┐   ┌───────┐   ┌──────────┐                        │
│  │ Dev  │   │Staging│   │Production│                        │
│  │      │   │       │   │          │                        │
│  │diff=2│   │diff=4 │   │ diff=6   │                        │
│  │mock  │   │test-db│   │ prod-db  │                        │
│  └──────┘   └───────┘   └──────────┘                        │
│                                                             │
│  Конфигурация передаётся через:                             │
│  • Environment variables                                    │
│  • Mounted files (ConfigMaps, Secrets)                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## ConfigMap — несекретная конфигурация

### Что такое ConfigMap

**ConfigMap** — объект K8s для хранения несекретных конфигурационных данных в формате key-value.

```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: blockchain-config
  namespace: blockchain
data:
  # Простые key-value
  BLOCKCHAIN_DIFFICULTY: "5"
  BLOCKCHAIN_GENESIS_REWARD: "10.0"
  BLOCKCHAIN_BLOCK_REWARD: "1.0"
  BLOCKCHAIN_TRANSACTION_FEE: "0.1"

  # Многострочные значения (файлы конфигурации)
  application.properties: |
    server.port=8080
    spring.application.name=blockchain-node
    logging.level.root=INFO

  # JSON конфигурация
  peers.json: |
    {
      "seeds": [
        "miner-node:8080",
        "wallet-node:8080"
      ]
    }
```

### Создание ConfigMap

```bash
# 1. Из YAML файла (рекомендуется)
kubectl apply -f configmap.yaml

# 2. Императивно из literal значений
kubectl create configmap my-config \
  --from-literal=DIFFICULTY=5 \
  --from-literal=REWARD=1.0

# 3. Из файла
kubectl create configmap app-config \
  --from-file=application.properties

# 4. Из директории (все файлы)
kubectl create configmap configs \
  --from-file=./config-dir/

# 5. Из .env файла
kubectl create configmap env-config \
  --from-env-file=.env
```

### Просмотр ConfigMap

```bash
# Список ConfigMaps
kubectl get configmaps -n blockchain
kubectl get cm -n blockchain  # Сокращённо

# Детали
kubectl describe configmap blockchain-config -n blockchain

# Содержимое в YAML
kubectl get configmap blockchain-config -n blockchain -o yaml
```

---

## Использование ConfigMap в Pod

### Способ 1: Environment Variables (отдельные ключи)

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: blockchain-node
spec:
  containers:
    - name: node
      image: blockchain-node:latest
      env:
        # Один ключ из ConfigMap
        - name: DIFFICULTY           # Имя переменной в контейнере
          valueFrom:
            configMapKeyRef:
              name: blockchain-config  # Имя ConfigMap
              key: BLOCKCHAIN_DIFFICULTY  # Ключ в ConfigMap

        # Ещё один ключ
        - name: GENESIS_REWARD
          valueFrom:
            configMapKeyRef:
              name: blockchain-config
              key: BLOCKCHAIN_GENESIS_REWARD
```

### Способ 2: Environment Variables (все ключи сразу)

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: blockchain-node
spec:
  containers:
    - name: node
      image: blockchain-node:latest
      envFrom:
        # Все ключи из ConfigMap станут env переменными
        - configMapRef:
            name: blockchain-config

        # Можно добавить prefix
        - configMapRef:
            name: another-config
          prefix: APP_  # APP_KEY1, APP_KEY2, ...
```

```
Результат в контейнере:
BLOCKCHAIN_DIFFICULTY=5
BLOCKCHAIN_GENESIS_REWARD=10.0
BLOCKCHAIN_BLOCK_REWARD=1.0
...
```

### Способ 3: Volume (монтирование как файлы)

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
        - name: config-volume
          mountPath: /app/config     # Куда монтировать
          readOnly: true

  volumes:
    - name: config-volume
      configMap:
        name: blockchain-config
```

```
Результат в контейнере:
/app/config/
├── BLOCKCHAIN_DIFFICULTY        # Файл с содержимым "5"
├── BLOCKCHAIN_GENESIS_REWARD    # Файл с содержимым "10.0"
├── application.properties       # Файл с многострочным содержимым
└── peers.json                   # JSON файл
```

### Способ 4: Volume (отдельные файлы)

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
        - name: config-volume
          mountPath: /app/config/application.properties
          subPath: application.properties  # Только этот файл

  volumes:
    - name: config-volume
      configMap:
        name: blockchain-config
        items:                    # Выбрать конкретные ключи
          - key: application.properties
            path: application.properties
```

---

## Secret — секретные данные

### Чем Secret отличается от ConfigMap

```
┌─────────────────────────────────────────────────────────────┐
│              CONFIGMAP vs SECRET                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ConfigMap                      Secret                      │
│  ─────────                      ──────                      │
│  • Несекретные данные           • Секретные данные          │
│  • Хранится в plain text        • Хранится в base64*        │
│  • Видно в kubectl describe     • Скрыто по умолчанию       │
│  • Нет размера limit            • Макс 1MB                  │
│  • Нет особой защиты            • Доступ через RBAC         │
│                                                             │
│  * base64 — НЕ шифрование! Только кодировка.                │
│    Для настоящей защиты используйте:                        │
│    - Encryption at rest                                     │
│    - External Secrets (Vault, AWS Secrets Manager)          │
│    - Sealed Secrets                                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Типы Secrets

```yaml
# 1. Opaque (generic) — произвольные данные
apiVersion: v1
kind: Secret
metadata:
  name: app-secrets
type: Opaque
data:
  password: cGFzc3dvcmQxMjM=  # base64: password123

# 2. kubernetes.io/basic-auth — логин/пароль
type: kubernetes.io/basic-auth
data:
  username: YWRtaW4=
  password: cGFzc3dvcmQ=

# 3. kubernetes.io/tls — TLS сертификат
type: kubernetes.io/tls
data:
  tls.crt: <base64 cert>
  tls.key: <base64 key>

# 4. kubernetes.io/dockerconfigjson — Docker registry credentials
type: kubernetes.io/dockerconfigjson
data:
  .dockerconfigjson: <base64 docker config>
```

### Создание Secret

```bash
# 1. Из YAML (не рекомендуется — секреты в Git!)
kubectl apply -f secret.yaml

# 2. Императивно (рекомендуется)
kubectl create secret generic db-credentials \
  --from-literal=username=admin \
  --from-literal=password='super$ecret!'

# 3. Из файла
kubectl create secret generic ssh-key \
  --from-file=id_rsa=/path/to/key

# 4. TLS секрет
kubectl create secret tls my-tls \
  --cert=path/to/cert.pem \
  --key=path/to/key.pem

# 5. Docker registry credentials
kubectl create secret docker-registry regcred \
  --docker-server=registry.example.com \
  --docker-username=user \
  --docker-password=pass
```

### stringData vs data

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: my-secret
type: Opaque

# data — значения в base64
data:
  password: cGFzc3dvcmQxMjM=  # echo -n "password123" | base64

# stringData — значения в plain text (K8s закодирует)
stringData:
  api-key: "my-plain-text-api-key"
  config.yaml: |
    database:
      host: localhost
      port: 5432
```

**Важно**: `stringData` удобнее для написания, но в etcd всё равно хранится в base64.

### Просмотр Secret

```bash
# Список
kubectl get secrets -n blockchain

# Детали (значения скрыты)
kubectl describe secret db-credentials

# Получить значение (декодировать base64)
kubectl get secret db-credentials -o jsonpath='{.data.password}' | base64 -d
```

---

## Использование Secret в Pod

### Способ 1: Environment Variables

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: app
spec:
  containers:
    - name: app
      image: my-app:latest
      env:
        # Один ключ из Secret
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password

      # Или все ключи сразу
      envFrom:
        - secretRef:
            name: db-credentials
```

### Способ 2: Volume (монтирование как файлы)

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: app
spec:
  containers:
    - name: app
      image: my-app:latest
      volumeMounts:
        - name: secrets-volume
          mountPath: /etc/secrets
          readOnly: true

  volumes:
    - name: secrets-volume
      secret:
        secretName: db-credentials
        defaultMode: 0400  # Права доступа (только чтение для owner)
```

### Способ 3: imagePullSecrets (для private registry)

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: app
spec:
  containers:
    - name: app
      image: registry.example.com/my-app:latest

  imagePullSecrets:
    - name: regcred  # Secret типа docker-registry
```

---

## Практика: конфигурация blockchain

### Наш ConfigMap

```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: blockchain-config
  namespace: blockchain
data:
  # Настройки blockchain
  BLOCKCHAIN_DIFFICULTY: "5"
  BLOCKCHAIN_GENESIS_REWARD: "10.0"
  BLOCKCHAIN_BLOCK_REWARD: "1.0"
  BLOCKCHAIN_TRANSACTION_FEE: "0.1"
  BLOCKCHAIN_MIN_AMOUNT: "0.1"

  # JVM настройки
  JAVA_OPTS: "-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: blockchain-peers
  namespace: blockchain
data:
  PEER_SEEDS: "miner-node.blockchain.svc.cluster.local:8080,wallet-node.blockchain.svc.cluster.local:8080"
```

### Использование в Deployment

```yaml
# k8s/miner-deployment.yaml (фрагмент)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: miner-node
  namespace: blockchain
spec:
  template:
    spec:
      containers:
        - name: blockchain-node
          image: blockchain-node:latest

          # Все переменные из ConfigMap
          envFrom:
            - configMapRef:
                name: blockchain-config
            - configMapRef:
                name: blockchain-peers

          # Дополнительные переменные
          env:
            # Значение из metadata Pod
            - name: NODE_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name

            # Статическое значение
            - name: NODE_ROLE
              value: "miner"

            # Из Secret (если бы был)
            - name: API_KEY
              valueFrom:
                secretKeyRef:
                  name: blockchain-secrets
                  key: api-key
                  optional: true  # Не падать если Secret нет
```

### Применение

```bash
# Создаём namespace
kubectl create namespace blockchain

# Применяем ConfigMaps
kubectl apply -f k8s/configmap.yaml

# Создаём Secret (императивно, не в Git!)
kubectl create secret generic blockchain-secrets \
  --from-literal=api-key=demo-api-key \
  -n blockchain

# Применяем Deployment
kubectl apply -f k8s/miner-deployment.yaml

# Проверяем переменные в Pod
kubectl exec -it miner-node-xxxxx -n blockchain -- env | grep BLOCKCHAIN
# BLOCKCHAIN_DIFFICULTY=5
# BLOCKCHAIN_GENESIS_REWARD=10.0
# ...
```

---

## Обновление конфигурации

### ConfigMap/Secret изменён — что с Pod?

```
┌─────────────────────────────────────────────────────────────┐
│           ОБНОВЛЕНИЕ КОНФИГУРАЦИИ                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Environment Variables:                                     │
│  • НЕ обновляются автоматически                             │
│  • Нужен restart Pod                                        │
│  • kubectl rollout restart deployment/my-app                │
│                                                             │
│  Volume mounts:                                             │
│  • Обновляются автоматически (через ~1 минуту)              │
│  • Но приложение должно перечитать файл!                    │
│  • Используйте inotify или periodic reload                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Стратегии обновления

```bash
# 1. Перезапуск Deployment (для env variables)
kubectl rollout restart deployment/miner-node -n blockchain

# 2. Изменить annotation (триггерит rollout)
kubectl patch deployment miner-node -n blockchain \
  -p '{"spec":{"template":{"metadata":{"annotations":{"configmap-version":"v2"}}}}}'

# 3. Использовать Reloader (сторонний инструмент)
# https://github.com/stakater/Reloader
# Автоматически перезапускает Pod при изменении ConfigMap/Secret
```

---

## Downward API — метаданные Pod

### Что такое Downward API

**Downward API** позволяет передать в контейнер информацию о самом Pod.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-pod
  labels:
    app: my-app
    version: v1
spec:
  containers:
    - name: app
      image: my-app:latest
      env:
        # Имя Pod
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name

        # Namespace Pod
        - name: POD_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace

        # IP Pod
        - name: POD_IP
          valueFrom:
            fieldRef:
              fieldPath: status.podIP

        # Имя Node
        - name: NODE_NAME
          valueFrom:
            fieldRef:
              fieldPath: spec.nodeName

        # Labels как строка
        - name: POD_LABELS
          valueFrom:
            fieldRef:
              fieldPath: metadata.labels

        # Лимит памяти контейнера
        - name: MEMORY_LIMIT
          valueFrom:
            resourceFieldRef:
              containerName: app
              resource: limits.memory
```

### Доступные поля

```
┌─────────────────────────────────────────────────────────────┐
│                   DOWNWARD API FIELDS                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  fieldRef (metadata/status):                                │
│  • metadata.name           — имя Pod                        │
│  • metadata.namespace      — namespace                      │
│  • metadata.uid            — UID Pod                        │
│  • metadata.labels         — все labels                     │
│  • metadata.annotations    — все annotations                │
│  • spec.nodeName           — имя Node                       │
│  • spec.serviceAccountName — ServiceAccount                 │
│  • status.podIP            — IP Pod                         │
│  • status.hostIP           — IP Node                        │
│                                                             │
│  resourceFieldRef (ресурсы контейнера):                     │
│  • requests.cpu                                             │
│  • requests.memory                                          │
│  • limits.cpu                                               │
│  • limits.memory                                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Best Practices

### 1. Не храните секреты в Git

```bash
# ❌ ПЛОХО: secret.yaml в репозитории
data:
  password: cGFzc3dvcmQ=  # Любой может декодировать!

# ✓ ХОРОШО: создавать императивно или использовать
# - Sealed Secrets
# - External Secrets Operator
# - HashiCorp Vault
# - Cloud provider secrets (AWS Secrets Manager, GCP Secret Manager)
```

### 2. Используйте Kustomize для окружений

```
k8s/
├── base/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── kustomization.yaml
├── overlays/
│   ├── dev/
│   │   ├── configmap.yaml    # DIFFICULTY=2
│   │   └── kustomization.yaml
│   ├── staging/
│   │   ├── configmap.yaml    # DIFFICULTY=4
│   │   └── kustomization.yaml
│   └── prod/
│       ├── configmap.yaml    # DIFFICULTY=6
│       └── kustomization.yaml
```

```bash
# Применить для dev
kubectl apply -k k8s/overlays/dev/

# Применить для prod
kubectl apply -k k8s/overlays/prod/
```

### 3. Immutable ConfigMaps/Secrets

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config-v1  # Версия в имени
immutable: true        # Нельзя изменить (K8s 1.21+)
data:
  key: value

# Преимущества:
# • Защита от случайных изменений
# • Лучшая производительность (не нужно watch)
# • Явная версионность
```

### 4. Validation через admission controllers

```yaml
# Пример с Kyverno policy
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata:
  name: require-configmap
spec:
  validationFailureAction: enforce
  rules:
    - name: check-configmap-exists
      match:
        resources:
          kinds:
            - Deployment
      validate:
        message: "Deployment must reference blockchain-config ConfigMap"
        pattern:
          spec:
            template:
              spec:
                containers:
                  - envFrom:
                      - configMapRef:
                          name: blockchain-config
```

---

## Частые ошибки

### 1. ConfigMap/Secret не найден

```bash
kubectl get pods
# NAME                     READY   STATUS                       RESTARTS
# my-app-xxxxx             0/1     CreateContainerConfigError   0

kubectl describe pod my-app-xxxxx
# Warning  Failed  kubelet  Error: configmap "missing-config" not found
```

**Решение**: Создать ConfigMap перед Deployment или использовать `optional: true`.

### 2. Неверный base64 в Secret

```bash
# ❌ НЕПРАВИЛЬНО (с переводом строки)
echo "password" | base64
# cGFzc3dvcmQK

# ✓ ПРАВИЛЬНО (без перевода строки)
echo -n "password" | base64
# cGFzc3dvcmQ=

# Или использовать stringData
stringData:
  password: "password"  # K8s сам закодирует
```

### 3. Забыли права на файлы Secret

```yaml
# По умолчанию mode 0644 — могут читать все
volumes:
  - name: secrets
    secret:
      secretName: my-secret
      defaultMode: 0400  # Только owner может читать
```

### 4. Конфигурация не обновляется

```bash
# Для env variables — нужен restart
kubectl rollout restart deployment/my-app

# Для volume mounts — подождите ~1 минуту
# или проверьте что приложение перечитывает файл
```

---

## Вопросы для самопроверки

### Теоретические

1. **Чем Secret отличается от ConfigMap?**

   <details>
   <summary>Ответ</summary>

   - Secret для секретных данных, ConfigMap для обычной конфигурации
   - Secret хранится в base64 (не шифрование!)
   - Secret скрыт в kubectl describe
   - Secret имеет лимит 1MB
   - Secret защищается RBAC
   </details>

2. **Почему base64 в Secret — это не безопасность?**

   <details>
   <summary>Ответ</summary>

   base64 — это кодировка, не шифрование. Любой может декодировать:
   ```bash
   echo "cGFzc3dvcmQ=" | base64 -d
   # password
   ```
   Для реальной защиты нужны: Encryption at rest, Vault, Sealed Secrets.
   </details>

3. **Как передать ConfigMap в Pod: env vs volume?**

   <details>
   <summary>Ответ</summary>

   - **env/envFrom**: удобно для простых значений, НЕ обновляется автоматически
   - **volume**: для файлов конфигурации, обновляется автоматически (~1 мин)

   Выбор зависит от того, как приложение читает конфигурацию.
   </details>

4. **Что такое Downward API?**

   <details>
   <summary>Ответ</summary>

   Механизм для передачи в контейнер информации о самом Pod: имя, namespace, IP, labels, annotations, ресурсы. Полезно для service discovery и логирования.
   </details>

5. **Как обновить конфигурацию работающего Pod?**

   <details>
   <summary>Ответ</summary>

   - Для env variables: `kubectl rollout restart deployment/name`
   - Для volume mounts: обновляется автоматически, но приложение должно перечитать файл
   - Использовать Reloader для автоматического restart
   </details>

### Практические

6. **Как создать Secret из командной строки?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl create secret generic my-secret \
     --from-literal=username=admin \
     --from-literal=password='P@ssw0rd!'
   ```
   </details>

7. **Как посмотреть содержимое Secret?**

   <details>
   <summary>Ответ</summary>

   ```bash
   # В base64
   kubectl get secret my-secret -o yaml

   # Декодировать конкретный ключ
   kubectl get secret my-secret -o jsonpath='{.data.password}' | base64 -d
   ```
   </details>

8. **Как использовать все ключи ConfigMap как env переменные?**

   <details>
   <summary>Ответ</summary>

   ```yaml
   spec:
     containers:
       - name: app
         envFrom:
           - configMapRef:
               name: my-configmap
   ```
   </details>

9. **Как получить имя Pod внутри контейнера?**

   <details>
   <summary>Ответ</summary>

   ```yaml
   env:
     - name: POD_NAME
       valueFrom:
         fieldRef:
           fieldPath: metadata.name
   ```
   </details>

10. **Почему Secret нельзя хранить в Git?**

    <details>
    <summary>Ответ</summary>

    - base64 легко декодировать
    - История Git хранит все изменения навсегда
    - Даже удалённый Secret остаётся в истории
    - Любой с доступом к репо видит секреты

    Альтернативы: Sealed Secrets, External Secrets, Vault.
    </details>

---

## Итоги урока

| Объект | Назначение | Хранение |
|--------|------------|----------|
| **ConfigMap** | Несекретная конфигурация | Plain text |
| **Secret** | Секретные данные | Base64 (не шифрование!) |

| Способ использования | Обновление | Когда использовать |
|---------------------|------------|-------------------|
| **env/envFrom** | Требует restart | Простые значения |
| **volume** | Автоматическое (~1 мин) | Файлы конфигурации |

| Команда | Действие |
|---------|----------|
| `kubectl create configmap` | Создать ConfigMap |
| `kubectl create secret generic` | Создать Secret |
| `kubectl get cm/secret -o yaml` | Посмотреть содержимое |
| `kubectl rollout restart` | Перезапустить для применения |

В следующем уроке — **Практика: полный деплой blockchain в Kind** со всеми изученными компонентами.
