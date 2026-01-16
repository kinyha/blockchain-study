# Blockchain Study

Учебный блокчейн на Java + курс по Docker и Kubernetes.

## Что это

1. **Blockchain на Java** — UTXO модель, PoW майнинг, REST API
2. **Docker** — контейнеризация Java-приложений
3. **Kubernetes** — оркестрация в Kind кластере
4. **Helm** — пакетный менеджер для K8s

## Быстрый старт (Kubernetes)

```bash
# 1. Сборка Docker образа
docker build -t blockchain-node:latest -f docker/Dockerfile .

# 2. Создание Kind кластера
kind create cluster --config k8s/kind-config.yaml

# 3. Установка Ingress
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# 4. Загрузка образа в Kind
kind load docker-image blockchain-node:latest --name blockchain-cluster

# 5. Установка через Helm
kubectl create namespace blockchain
helm install blockchain ./helm/blockchain-network -n blockchain

# 6. Добавить в hosts (PowerShell от админа)
Add-Content -Path "C:\Windows\System32\drivers\etc\hosts" -Value "`n127.0.0.1 miner.localhost wallet.localhost blockchain.localhost orchestrator.localhost dashboard.localhost"
```

Открыть: http://dashboard.localhost

## Архитектура

```
┌────────────────────────────────────────────────────────┐
│                 Kubernetes Cluster                      │
│                                                         │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌───────────┐ │
│  │  Miner  │  │ Wallet  │  │FullNode │  │Orchestrator│ │
│  │  Node   │  │ Node x2 │  │  Node   │  │           │ │
│  └────┬────┘  └────┬────┘  └────┬────┘  └─────┬─────┘ │
│       └────────────┴───────────┴──────────────┘       │
│                           │                            │
│                    ┌──────┴──────┐                    │
│                    │  Dashboard  │                    │
│                    │  (nginx)    │                    │
│                    └──────┬──────┘                    │
│                           │                            │
│                    ┌──────┴──────┐                    │
│                    │   Ingress   │                    │
│                    └─────────────┘                    │
└────────────────────────────────────────────────────────┘
                           │
                    http://*.localhost
```

## Endpoints

| URL | Описание |
|-----|----------|
| http://dashboard.localhost | Web UI |
| http://miner.localhost/api/blocks/height | Высота blockchain |
| http://miner.localhost/api/wallet/balance | Баланс кошелька |
| http://orchestrator.localhost/api/orchestrator/network | Статус сети |

## Структура проекта

```
block-chain-study/
├── src/main/java/com/study/blockchain/
│   ├── core/           # Block, Blockchain, HashUtil
│   ├── service/        # Business logic (Spring)
│   ├── api/            # REST controllers
│   └── config/         # Spring configuration
├── docker/
│   ├── Dockerfile      # Multi-stage build
│   └── docker-compose.yml
├── k8s/                # Kubernetes manifests
├── helm/blockchain-network/  # Helm chart
├── dashboard/          # HTML dashboard
├── lessons/            # Уроки по blockchain (10 уроков)
└── lessons-docker-k8s/ # Уроки по Docker/K8s (15 уроков)
```

## Учебные материалы

### Blockchain (lessons/)

| # | Тема |
|---|------|
| 1 | Block — структура блока |
| 2 | Hashing — SHA-256, связь блоков |
| 3 | Blockchain — цепочка, genesis |
| 4 | Validation — проверка цепочки |
| 5 | Transactions — inputs, outputs |
| 6 | Wallets — ECDSA ключи |
| 7 | Signatures — подпись транзакций |
| 8 | Proof-of-Work — майнинг |
| 9 | UTXO Pool — модель баланса |
| 10 | Network — P2P синхронизация |

### Docker & Kubernetes (lessons-docker-k8s/)

| # | Тема |
|---|------|
| 1-5 | Docker: образы, Dockerfile, Compose |
| 6-10 | K8s: Pods, Deployments, Services, Ingress |
| 11-15 | Advanced: Probes, StatefulSets, Helm |

## Команды

### Kubernetes

```bash
# Статус pods
kubectl get pods -n blockchain

# Логи
kubectl logs -f deployment/blockchain-blockchain-network-miner -n blockchain

# Перезапуск после изменений
kubectl rollout restart deployment -n blockchain
```

### Helm

```bash
# Установка
helm install blockchain ./helm/blockchain-network -n blockchain

# Обновление
helm upgrade blockchain ./helm/blockchain-network -n blockchain

# Удаление
helm uninstall blockchain -n blockchain
```

### Тестирование

```bash
# Запустить майнинг
curl -X POST http://miner.localhost/api/mining/start

# Проверить высоту
curl http://miner.localhost/api/blocks/height

# Статус сети
curl http://orchestrator.localhost/api/orchestrator/network
```

## Документация

- [Архитектура системы](docs/ARCHITECTURE.md) — подробное описание всех компонентов
- [План курса](docs/plans/2026-01-14-docker-k8s-course-design.md) — дизайн-документ

## Требования

- Java 21+
- Docker Desktop
- kubectl
- Helm 3
- Kind

## License

MIT
