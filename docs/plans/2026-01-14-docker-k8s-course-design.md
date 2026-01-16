# Blockchain Docker/Kubernetes Learning Course

## Цель
Превратить blockchain проект в учебный курс по Docker и Kubernetes для Java middle+ разработчика.

## Ключевые решения
- **K8s уровень**: Практический (Deployments, Services, ConfigMaps, Ingress, Helm)
- **Коммуникация нод**: Чистый REST
- **Фреймворк**: Spring Boot
- **Симуляция**: Разные роли нод (miner, wallet, full) + orchestrator
- **UI**: Простой HTML dashboard (polling)
- **JavaFX**: Сохранить в ветке `legacy/javafx`, удалить из main

## Бизнес-правила
```
Mining:
  - Difficulty: ~5-6 (подбор под ~10 сек/блок)
  - Genesis reward: 10 coins
  - Block reward: 1 coin (без halving)

Transactions:
  - Fee: 0.1 coins (фиксированная, идёт майнеру)
  - Min amount: 0.1 coins
  - Reject if: balance < amount + fee

Sync:
  - Longest valid chain wins
  - Orphan block txs → return to mempool
```

---

## Архитектура K8s

```
┌─────────────────────────────────────────────────────────┐
│                  Kubernetes Cluster                      │
├─────────────────────────────────────────────────────────┤
│  miner-deployment (1-N pods)     → miner-service        │
│  wallet-deployment (1-N pods)    → wallet-service       │
│  orchestrator-deployment (1 pod) → orchestrator-service │
│  dashboard-deployment (nginx)    → Ingress              │
└─────────────────────────────────────────────────────────┘
```

---

## Структура Spring Boot приложения

```
src/main/java/com/study/blockchain/
├── core/                    # Без изменений
├── transaction/             # Без изменений
├── wallet/                  # Без изменений
├── mining/                  # Без изменений
├── utxo/                    # Без изменений
├── network/                 # Рефакторинг: REST client вместо сокетов
│   ├── NodeRegistry.java
│   └── PeerClient.java
├── api/                     # НОВОЕ: REST контроллеры
│   ├── BlockController.java
│   ├── TransactionController.java
│   ├── WalletController.java
│   ├── NodeController.java
│   ├── MiningController.java
│   └── dto/
├── service/                 # НОВОЕ: бизнес-логика (с нуля, TDD)
│   ├── BlockchainService.java
│   ├── MiningService.java
│   ├── TransactionService.java
│   ├── WalletService.java
│   ├── SyncService.java
│   └── MempoolService.java
└── config/
    └── NodeConfiguration.java
```

**Профили Spring:**
- `miner` - майнит блоки
- `wallet` - только транзакции
- `full` - всё

---

## REST API

**Blocks**: `GET/POST /api/blocks`, `/api/blocks/latest`, `/api/blocks/{index}`, `/api/blocks/height`

**Transactions**: `POST /api/transactions`, `GET /api/transactions/pending`

**Wallet**: `GET /api/wallet/address`, `/api/wallet/balance`, `/api/wallet/utxos`

**Node**: `GET/POST /api/node/peers`, `POST /api/node/sync`, `GET /api/node/health`

**Mining**: `GET /api/mining/status`, `POST /api/mining/start`, `POST /api/mining/stop`

**Orchestrator**: `GET /api/orchestrator/network`, `/api/orchestrator/nodes`, `POST /api/orchestrator/scenario/*`

---

## Структура курса (15 уроков)

### Модуль 1: Docker & Java (уроки 1-5)
1. Основы Docker - контейнеры vs VM, образы, registry
2. Dockerfile для Java - Multi-stage, Distroless/Alpine, слои
3. Docker Compose - сеть нод, volumes
4. Практика - контейнеризация blockchain-node
5. Production Ready - JVM Memory (`-XX:MaxRAMPercentage`), Security, non-root

### Модуль 2: Kubernetes основы (уроки 6-10)
6. Архитектура K8s - pods, nodes, control plane
7. Workloads - Deployments, ReplicaSets, Labels & Selectors
8. Networking - Services, DNS, Ingress
9. Configuration - ConfigMaps, Secrets, env variables
10. Практика - деплой в Kind с Ingress

### Модуль 3: K8s Advanced & Helm (уроки 11-15)
11. Стабильность - Resources/Limits, Probes, Graceful Shutdown
12. Stateful Apps - PV, PVC, StatefulSets
13. Helm - структура чарта, values.yaml
14. Troubleshooting - kubectl debug, logs, events
15. Финал - Helm-чарт для blockchain-сети

**Формат урока:**
- Теория (10-15 мин)
- Практика (код)
- Вопросы для самопроверки (5-7 вопросов)

---

## Файловая структура

```
block-chain-study/
├── lessons/                      # Существующие blockchain уроки
├── lessons-docker-k8s/           # НОВОЕ
│   ├── module-1-docker/
│   ├── module-2-k8s-basics/
│   └── module-3-k8s-advanced/
├── docker/
│   ├── Dockerfile
│   ├── Dockerfile.dashboard
│   └── docker-compose.yml
├── k8s/                          # Манифесты для уроков
├── helm/blockchain-network/      # Helm chart
├── dashboard/index.html          # Простой UI
└── src/main/java/...
```

---

## План миграции

### Фаза 0: Подготовка
- [ ] Создать ветку `legacy/javafx`
- [ ] В main удалить `ui/` пакет
- [ ] Добавить Spring Boot в `build.gradle.kts`

### Фаза 1: Service Layer (TDD)
Порядок написания тестов и сервисов:
1. [ ] `TransactionServiceTest` → `TransactionService` (fee, min amount, validation)
2. [ ] `MiningServiceTest` → `MiningService` (rewards: genesis 10, block 1)
3. [ ] `MempoolServiceTest` → `MempoolService` (add/remove, orphan return)
4. [ ] `SyncServiceTest` → `SyncService` (longest chain, conflicts)
5. [ ] `WalletServiceTest` → `WalletService` (balance, UTXO selection)
6. [ ] `BlockchainServiceTest` → `BlockchainService` (интеграция)

### Фаза 2: REST API + Docker
- [ ] Создать контроллеры
- [ ] Написать `Dockerfile` (multi-stage)
- [ ] Написать `docker-compose.yml` (3 ноды)
- [ ] Тест синхронизации через REST
- [ ] Писать уроки модуля 1

### Фаза 3: Kubernetes
- [ ] K8s манифесты
- [ ] Kind кластер
- [ ] Деплой и тест
- [ ] Писать уроки модуля 2

### Фаза 4: Orchestrator + Dashboard + Helm
- [ ] Orchestrator сервис
- [ ] Dashboard HTML
- [ ] Helm chart
- [ ] Финальное тестирование
- [ ] Писать уроки модуля 3

---

## Верификация

### После Фазы 1:
```bash
./gradlew test  # Все service тесты проходят
```

### После Фазы 2:
```bash
docker-compose up -d
curl http://localhost:8001/api/node/health
curl http://localhost:8002/api/blocks/height
# Ноды синхронизируются
```

### После Фазы 3:
```bash
kind create cluster
kubectl apply -f k8s/
kubectl get pods  # Все Running
curl http://localhost/api/orchestrator/network  # Через Ingress
```

### После Фазы 4:
```bash
helm install blockchain ./helm/blockchain-network
# Dashboard показывает сеть
# Транзакции создаются и подтверждаются
```

---

## Критические файлы для изменения

**Удалить:**
- `src/main/java/com/study/blockchain/ui/*` (весь пакет)

**Рефакторинг:**
- `src/main/java/com/study/blockchain/network/Node.java` → REST client
- `build.gradle.kts` → Spring Boot dependencies

**Создать:**
- `src/main/java/com/study/blockchain/service/*`
- `src/main/java/com/study/blockchain/api/*`
- `src/test/java/com/study/blockchain/service/*`
- `docker/Dockerfile`
- `docker/docker-compose.yml`
- `k8s/*.yaml`
- `helm/blockchain-network/*`
- `dashboard/index.html`
- `lessons-docker-k8s/**/*.md` (15 уроков)
