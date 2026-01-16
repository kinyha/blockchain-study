# Архитектура Blockchain Demo System

## Обзор

Это учебный проект, демонстрирующий:
1. **Blockchain** — реализация на Java (UTXO модель, PoW майнинг, P2P)
2. **Docker** — контейнеризация Java-приложений
3. **Kubernetes** — оркестрация контейнеров
4. **Helm** — пакетный менеджер для K8s

---

## Что мы построили

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Kubernetes Cluster                               │
│                                                                          │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                 │
│   │   Miner     │    │   Wallet    │    │  FullNode   │                 │
│   │   Node      │    │   Node x2   │    │    Node     │                 │
│   │             │    │             │    │             │                 │
│   │ - майнит    │    │ - хранит    │    │ - майнит    │                 │
│   │   блоки     │    │   кошелёк   │    │ - кошелёк   │                 │
│   │ - получает  │    │ - отправ-   │    │ - всё       │                 │
│   │   награду   │    │   ляет tx   │    │   вместе    │                 │
│   └──────┬──────┘    └──────┬──────┘    └──────┬──────┘                 │
│          │                  │                  │                         │
│          └────────────┬─────┴─────────────────┘                         │
│                       │                                                  │
│                       ▼                                                  │
│              ┌─────────────────┐                                         │
│              │  Orchestrator   │ ◄─── Управляет сетью                   │
│              │                 │      Собирает статистику               │
│              │  - регистрация  │      Запускает сценарии               │
│              │    нод          │                                         │
│              │  - статус сети  │                                         │
│              │  - сценарии     │                                         │
│              └────────┬────────┘                                         │
│                       │                                                  │
│                       ▼                                                  │
│              ┌─────────────────┐                                         │
│              │    Dashboard    │ ◄─── HTML UI                           │
│              │    (nginx)      │      Показывает состояние сети        │
│              └─────────────────┘                                         │
│                                                                          │
│                       │                                                  │
│                       ▼                                                  │
│              ┌─────────────────┐                                         │
│              │  Ingress        │ ◄─── Входная точка                     │
│              │  (nginx)        │      *.localhost → сервисы             │
│              └─────────────────┘                                         │
└─────────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
                   Браузер
            http://dashboard.localhost
```

---

## Компоненты системы

### 1. Blockchain Nodes (Java + Spring Boot)

**Путь:** `src/main/java/com/study/blockchain/`

Это основное приложение. Один и тот же JAR-файл может работать в разных режимах:

| Профиль | Что делает | Активация |
|---------|------------|-----------|
| `miner` | Только майнинг | `SPRING_PROFILES_ACTIVE=miner` |
| `wallet` | Только транзакции | `SPRING_PROFILES_ACTIVE=wallet` |
| `full` | Всё вместе | `SPRING_PROFILES_ACTIVE=full` |
| `orchestrator` | Управление сетью | `SPRING_PROFILES_ACTIVE=orchestrator` |

**Ключевые классы:**

```
service/
├── BlockchainService.java    # Главный сервис, координирует всё
├── MiningService.java        # Майнинг блоков (PoW)
├── TransactionService.java   # Создание транзакций
├── MempoolService.java       # Очередь неподтверждённых tx
├── WalletService.java        # Балансы, UTXO
├── SyncService.java          # Синхронизация с другими нодами
└── OrchestratorService.java  # Управление сетью (только для orchestrator)

api/
├── BlockController.java       # GET/POST /api/blocks
├── TransactionController.java # POST /api/transactions
├── WalletController.java      # GET /api/wallet/balance
├── MiningController.java      # POST /api/mining/start|stop
├── NodeController.java        # GET /api/node/health
└── OrchestratorController.java # GET /api/orchestrator/network
```

### 2. Orchestrator

**Зачем нужен:** Централизованное управление распределённой сетью.

**Что делает:**
- Хранит реестр всех нод
- Опрашивает ноды для сбора статистики
- Предоставляет API для dashboard
- Запускает сценарии (start mining, sync all, etc.)

**API:**
```
GET  /api/orchestrator/network        # Статус всей сети
GET  /api/orchestrator/nodes          # Список зарегистрированных нод
POST /api/orchestrator/nodes/register # Регистрация новой ноды
POST /api/orchestrator/scenario/start-miners    # Запустить майнинг
POST /api/orchestrator/scenario/stop-miners     # Остановить майнинг
POST /api/orchestrator/scenario/sync-all        # Синхронизировать все ноды
POST /api/orchestrator/scenario/transaction-burst?count=10  # Создать транзакции
```

**Важно:** Orchestrator НЕ хранит blockchain. Он только управляет другими нодами через REST API.

### 3. Dashboard

**Путь:** `dashboard/index.html`

Простая HTML страница с JavaScript, которая:
1. Каждые 3 секунды делает запрос к orchestrator
2. Отображает статистику сети
3. Позволяет запускать сценарии через кнопки

**Как работает polling:**
```javascript
const POLL_INTERVAL = 3000;
async function poll() {
    const data = await fetch(ORCHESTRATOR_URL + '/api/orchestrator/network');
    updateUI(data);
}
setInterval(poll, POLL_INTERVAL);
```

---

## Docker

### Dockerfile (multi-stage build)

**Путь:** `docker/Dockerfile`

```dockerfile
# Stage 1: Сборка
FROM eclipse-temurin:21-jdk AS builder
COPY . .
RUN ./gradlew bootJar

# Stage 2: Runtime (минимальный образ)
FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/build/libs/*.jar app.jar
USER appuser  # Не root!
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Зачем multi-stage:**
- Stage 1: 800MB (JDK + Gradle + исходники)
- Stage 2: 200MB (только JRE + JAR)

**Сборка образа:**
```bash
docker build -t blockchain-node:latest -f docker/Dockerfile .
```

### docker-compose.yml

**Путь:** `docker/docker-compose.yml`

Для локального запуска без Kubernetes:
```bash
cd docker
docker-compose up -d
```

---

## Kubernetes

### Манифесты

**Путь:** `k8s/`

```
k8s/
├── namespace.yaml          # Namespace: blockchain
├── configmap.yaml          # Настройки (difficulty, rewards)
├── miner-deployment.yaml   # Deployment для miner
├── wallet-deployment.yaml  # Deployment для wallet
├── full-node-deployment.yaml
├── services.yaml           # ClusterIP сервисы
├── ingress.yaml            # Ingress для внешнего доступа
└── kind-config.yaml        # Конфигурация Kind кластера
```

**Ключевые концепции:**

1. **Deployment** — описывает как запускать pods:
   ```yaml
   spec:
     replicas: 2  # Сколько копий
     template:
       spec:
         containers:
           - name: blockchain-node
             image: blockchain-node:latest
             env:
               - name: SPRING_PROFILES_ACTIVE
                 value: "wallet"
   ```

2. **Service** — внутренний DNS для pods:
   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: miner-service
   spec:
     selector:
       app: miner
     ports:
       - port: 8080
   # Другие pods могут обращаться по http://miner-service:8080
   ```

3. **Ingress** — маршрутизация внешнего трафика:
   ```yaml
   rules:
     - host: miner.localhost
       http:
         paths:
           - path: /
             backend:
               service:
                 name: miner-service
   ```

---

## Helm

### Зачем Helm

Kubernetes манифесты — это статичные YAML файлы. Helm добавляет:
- **Шаблонизацию** — переменные, циклы, условия
- **Переиспользование** — один chart для разных окружений
- **Версионирование** — откат к предыдущей версии
- **Зависимости** — charts могут зависеть друг от друга

### Структура Helm Chart

**Путь:** `helm/blockchain-network/`

```
helm/blockchain-network/
├── Chart.yaml              # Метаданные chart
├── values.yaml             # Значения по умолчанию
├── templates/
│   ├── _helpers.tpl        # Вспомогательные функции
│   ├── configmap.yaml      # ConfigMap (шаблон)
│   ├── miner-deployment.yaml
│   ├── wallet-deployment.yaml
│   ├── fullnode-deployment.yaml
│   ├── orchestrator-deployment.yaml
│   ├── dashboard-deployment.yaml
│   ├── dashboard-configmap.yaml
│   ├── services.yaml
│   ├── ingress.yaml
│   └── NOTES.txt           # Сообщение после установки
└── .helmignore
```

### values.yaml

Центральный файл конфигурации:

```yaml
# Настройки blockchain
blockchain:
  difficulty: 5          # Сложность майнинга
  genesisReward: "10.0"  # Награда за genesis блок
  blockReward: "1.0"     # Награда за обычный блок
  transactionFee: "0.1"  # Комиссия за транзакцию

# Настройки нод
nodes:
  miner:
    enabled: true
    replicaCount: 1
    resources:
      requests:
        memory: "256Mi"
        cpu: "250m"
      limits:
        memory: "512Mi"
        cpu: "1000m"

  wallet:
    enabled: true
    replicaCount: 2

  orchestrator:
    enabled: true
    port: 8090

# Dashboard
dashboard:
  enabled: true
  orchestratorUrl: "http://orchestrator.localhost"
```

### Команды Helm

```bash
# Установка
helm install blockchain ./helm/blockchain-network -n blockchain

# Обновление (после изменения values.yaml)
helm upgrade blockchain ./helm/blockchain-network -n blockchain

# Удаление
helm uninstall blockchain -n blockchain

# Просмотр сгенерированных манифестов (без установки)
helm template blockchain ./helm/blockchain-network

# Проверка синтаксиса
helm lint ./helm/blockchain-network
```

---

## Kind (Kubernetes IN Docker)

Kind — это инструмент для запуска Kubernetes кластера внутри Docker контейнеров.

### Почему Kind

- **Легковесный** — не нужна VM, всё в Docker
- **Быстрый** — кластер поднимается за минуту
- **Локальный** — работает на ноутбуке
- **Совместимый** — настоящий Kubernetes API

### Конфигурация

**Путь:** `k8s/kind-config.yaml`

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 80
        hostPort: 80      # localhost:80 → Ingress
      - containerPort: 443
        hostPort: 443
```

### Команды Kind

```bash
# Создание кластера
kind create cluster --config k8s/kind-config.yaml

# Загрузка локального образа в кластер
kind load docker-image blockchain-node:latest --name blockchain-cluster

# Удаление кластера
kind delete cluster --name blockchain-cluster

# Список кластеров
kind get clusters
```

---

## Полный цикл развёртывания

### 1. Сборка образа

```bash
docker build -t blockchain-node:latest -f docker/Dockerfile .
```

### 2. Создание Kind кластера

```bash
kind create cluster --config k8s/kind-config.yaml
```

### 3. Установка Ingress Controller

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=120s
```

### 4. Загрузка образа в Kind

```bash
kind load docker-image blockchain-node:latest --name blockchain-cluster
```

### 5. Установка Helm chart

```bash
kubectl create namespace blockchain
helm install blockchain ./helm/blockchain-network -n blockchain
```

### 6. Настройка hosts

Windows (PowerShell от администратора):
```powershell
Add-Content -Path "C:\Windows\System32\drivers\etc\hosts" -Value "`n127.0.0.1 miner.localhost wallet.localhost blockchain.localhost orchestrator.localhost dashboard.localhost"
```

Linux/Mac:
```bash
echo "127.0.0.1 miner.localhost wallet.localhost blockchain.localhost orchestrator.localhost dashboard.localhost" | sudo tee -a /etc/hosts
```

### 7. Регистрация нод в Orchestrator

```bash
curl -X POST http://orchestrator.localhost/api/orchestrator/nodes/register \
  -H "Content-Type: application/json" \
  -d '{"nodeId":"miner-0","url":"http://blockchain-blockchain-network-miner:8080","role":"miner"}'

curl -X POST http://orchestrator.localhost/api/orchestrator/nodes/register \
  -H "Content-Type: application/json" \
  -d '{"nodeId":"wallet-0","url":"http://blockchain-blockchain-network-wallet:8080","role":"wallet"}'

curl -X POST http://orchestrator.localhost/api/orchestrator/nodes/register \
  -H "Content-Type: application/json" \
  -d '{"nodeId":"fullnode-0","url":"http://blockchain-blockchain-network-fullnode:8080","role":"full"}'
```

### 8. Открыть Dashboard

http://dashboard.localhost

---

## Как вносить изменения

### Изменить Java код

1. Внести изменения в `src/`
2. Пересобрать образ:
   ```bash
   docker build -t blockchain-node:latest -f docker/Dockerfile .
   ```
3. Загрузить в Kind:
   ```bash
   kind load docker-image blockchain-node:latest --name blockchain-cluster
   ```
4. Перезапустить pods:
   ```bash
   kubectl rollout restart deployment -n blockchain
   ```

### Изменить конфигурацию Helm

1. Отредактировать `helm/blockchain-network/values.yaml`
2. Применить:
   ```bash
   helm upgrade blockchain ./helm/blockchain-network -n blockchain
   ```

### Изменить Dashboard

1. Отредактировать `dashboard/index.html`
2. Helm chart содержит копию HTML в `templates/dashboard-configmap.yaml`
3. Обновить ConfigMap и перезапустить dashboard pod

---

## Полезные команды

### Kubernetes

```bash
# Статус pods
kubectl get pods -n blockchain

# Логи конкретного pod
kubectl logs -f deployment/blockchain-blockchain-network-miner -n blockchain

# Войти в pod
kubectl exec -it deployment/blockchain-blockchain-network-miner -n blockchain -- /bin/sh

# Описание pod (для отладки)
kubectl describe pod <pod-name> -n blockchain

# Перезапуск deployment
kubectl rollout restart deployment/blockchain-blockchain-network-miner -n blockchain
```

### Тестирование API

```bash
# Health check
curl http://miner.localhost/api/node/health

# Высота blockchain
curl http://miner.localhost/api/blocks/height

# Баланс кошелька
curl http://miner.localhost/api/wallet/balance

# Запустить майнинг
curl -X POST http://miner.localhost/api/mining/start

# Статус сети
curl http://orchestrator.localhost/api/orchestrator/network
```

---

## Диаграмма потока данных

```
Пользователь
    │
    ▼
┌─────────────────┐
│  Dashboard      │  ◄─── http://dashboard.localhost
│  (браузер)      │
└────────┬────────┘
         │ fetch() каждые 3 сек
         ▼
┌─────────────────┐
│  Orchestrator   │  ◄─── http://orchestrator.localhost
│  (Spring Boot)  │
└────────┬────────┘
         │ REST API calls
         ▼
┌─────────────────────────────────────────┐
│  Blockchain Nodes (внутри K8s кластера) │
│                                         │
│  miner-service:8080                     │
│  wallet-service:8080                    │
│  fullnode-service:8080                  │
└─────────────────────────────────────────┘
```

---

## FAQ

### Почему ноды не синхронизируются автоматически?

В текущей реализации ноды независимы. Для синхронизации нужно:
1. Настроить peers между нодами
2. Или использовать orchestrator для broadcast

### Почему orchestrator теряет данные при перезапуске?

Регистрация нод хранится в памяти (ConcurrentHashMap). Для production нужно:
- Хранить в ConfigMap/Secret
- Или использовать базу данных

### Как добавить новую ноду?

1. Увеличить `replicaCount` в values.yaml
2. `helm upgrade blockchain ./helm/blockchain-network -n blockchain`
3. Зарегистрировать в orchestrator

### Как изменить сложность майнинга?

```yaml
# values.yaml
blockchain:
  difficulty: 3  # Было 5, стало 3 (быстрее)
```
```bash
helm upgrade blockchain ./helm/blockchain-network -n blockchain
kubectl rollout restart deployment -n blockchain
```
