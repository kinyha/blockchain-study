# Урок 15: Финал — Helm Chart для Blockchain Network

## Цели урока

После этого урока вы будете:
- Иметь рабочий Helm chart для blockchain-сети
- Понимать структуру production-ready chart
- Уметь кастомизировать деплой через values
- Знать, как упаковывать и распространять charts
- Уметь деплоить blockchain-сеть одной командой

---

## Итоговая архитектура

```
┌─────────────────────────────────────────────────────────────────────┐
│                  BLOCKCHAIN NETWORK HELM CHART                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   helm install blockchain ./helm/blockchain-network                 │
│                              │                                      │
│                              ▼                                      │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │                    KUBERNETES CLUSTER                        │  │
│   │                                                              │  │
│   │   ConfigMap: blockchain-config                               │  │
│   │   ├── BLOCKCHAIN_DIFFICULTY=5                               │  │
│   │   ├── BLOCKCHAIN_GENESIS_REWARD=10.0                        │  │
│   │   └── ...                                                   │  │
│   │                                                              │  │
│   │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │  │
│   │   │   Miner     │  │   Wallet    │  │  Full Node  │          │  │
│   │   │ Deployment  │  │ Deployment  │  │ Deployment  │          │  │
│   │   │  (1 pod)    │  │  (2 pods)   │  │  (1 pod)    │          │  │
│   │   └──────┬──────┘  └──────┬──────┘  └──────┬──────┘          │  │
│   │          │                │                │                  │  │
│   │   ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐          │  │
│   │   │   Service   │  │   Service   │  │   Service   │          │  │
│   │   │   :8080     │  │   :8080     │  │   :8080     │          │  │
│   │   └──────┬──────┘  └──────┬──────┘  └──────┬──────┘          │  │
│   │          │                │                │                  │  │
│   │          └────────────────┼────────────────┘                  │  │
│   │                           │                                   │  │
│   │                    ┌──────▼──────┐                            │  │
│   │                    │   Ingress   │                            │  │
│   │                    │   (nginx)   │                            │  │
│   │                    └──────┬──────┘                            │  │
│   │                           │                                   │  │
│   └───────────────────────────┼──────────────────────────────────┘  │
│                               │                                     │
│                               ▼                                     │
│   miner.localhost  wallet.localhost  blockchain.localhost           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Структура нашего Chart

```
helm/blockchain-network/
├── Chart.yaml              # Метаданные chart
├── values.yaml             # Значения по умолчанию
├── .helmignore             # Файлы для игнорирования
└── templates/
    ├── _helpers.tpl        # Вспомогательные шаблоны
    ├── configmap.yaml      # ConfigMap с конфигурацией
    ├── miner-deployment.yaml
    ├── wallet-deployment.yaml
    ├── fullnode-deployment.yaml
    ├── services.yaml       # Все Services
    ├── ingress.yaml        # Ingress правила
    └── NOTES.txt           # Сообщение после установки
```

---

## Ключевые компоненты

### Chart.yaml

```yaml
apiVersion: v2
name: blockchain-network
description: A Helm chart for deploying blockchain network on Kubernetes
type: application
version: 0.1.0          # Версия CHART
appVersion: "1.0.0"     # Версия приложения
```

### values.yaml — конфигурация

```yaml
# Образ
image:
  repository: blockchain-node
  tag: "latest"
  pullPolicy: IfNotPresent

# Blockchain параметры
blockchain:
  difficulty: 5
  genesisReward: "10.0"
  blockReward: "1.0"
  transactionFee: "0.1"

# Конфигурация нод
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
    # ...

  fullNode:
    enabled: true
    replicaCount: 1
    # ...

# Ingress
ingress:
  enabled: true
  className: nginx
  hosts:
    - host: miner.localhost
      paths:
        - path: /
          pathType: Prefix
          service: miner
```

### _helpers.tpl — переиспользуемые шаблоны

```yaml
{{/*
Полное имя для ресурсов
*/}}
{{- define "blockchain-network.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "blockchain-network.labels" -}}
helm.sh/chart: {{ include "blockchain-network.chart" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Peer seeds для blockchain нод
*/}}
{{- define "blockchain-network.peerSeeds" -}}
{{- $fullname := include "blockchain-network.fullname" . -}}
{{- printf "%s-miner:8080,%s-wallet:8080,%s-fullnode:8080" $fullname $fullname $fullname -}}
{{- end }}
```

---

## Деплой blockchain-сети

### Шаг 1: Подготовка

```bash
# Убедитесь что Kind кластер запущен
kind get clusters
# blockchain-cluster

# Образ загружен
kind load docker-image blockchain-node:latest --name blockchain-cluster

# Ingress Controller установлен
kubectl get pods -n ingress-nginx
```

### Шаг 2: Проверка chart

```bash
# Lint — проверка синтаксиса
helm lint ./helm/blockchain-network
# ==> Linting ./helm/blockchain-network
# [INFO] Chart.yaml: icon is recommended
# 1 chart(s) linted, 0 chart(s) failed

# Template — посмотреть что будет создано
helm template blockchain ./helm/blockchain-network
```

### Шаг 3: Установка

```bash
# Установить с defaults
helm install blockchain ./helm/blockchain-network \
  --namespace blockchain \
  --create-namespace

# Или с кастомными values
helm install blockchain ./helm/blockchain-network \
  --namespace blockchain \
  --create-namespace \
  --set nodes.wallet.replicaCount=3 \
  --set blockchain.difficulty=6
```

### Шаг 4: Проверка

```bash
# Статус релиза
helm status blockchain -n blockchain

# Список релизов
helm list -n blockchain

# Проверить Pod'ы
kubectl get pods -n blockchain

# Проверить Services
kubectl get services -n blockchain

# Проверить Ingress
kubectl get ingress -n blockchain
```

### Шаг 5: Тестирование

```bash
# Добавить в /etc/hosts (если ещё не добавлено)
# 127.0.0.1 miner.localhost wallet.localhost blockchain.localhost

# Health check
curl http://miner.localhost/api/node/health

# Blockchain height
curl http://blockchain.localhost/api/blocks/height

# Mine a block
curl -X POST http://miner.localhost/api/mining/mine
```

---

## Кастомизация через values

### Файлы для разных окружений

```yaml
# values-dev.yaml
blockchain:
  difficulty: 2

nodes:
  miner:
    replicaCount: 1
  wallet:
    replicaCount: 1
  fullNode:
    replicaCount: 1

ingress:
  hosts:
    - host: blockchain.dev.local
      paths:
        - path: /
          pathType: Prefix
          service: fullNode
```

```yaml
# values-prod.yaml
blockchain:
  difficulty: 6

nodes:
  miner:
    replicaCount: 3
    resources:
      requests:
        memory: "512Mi"
        cpu: "500m"
      limits:
        memory: "1Gi"
        cpu: "2000m"
  wallet:
    replicaCount: 5
  fullNode:
    replicaCount: 3

ingress:
  hosts:
    - host: blockchain.example.com
      paths:
        - path: /
          pathType: Prefix
          service: fullNode
  tls:
    - secretName: blockchain-tls
      hosts:
        - blockchain.example.com
```

### Установка с файлом values

```bash
# Dev environment
helm install blockchain-dev ./helm/blockchain-network \
  -f ./helm/blockchain-network/values-dev.yaml \
  -n dev --create-namespace

# Prod environment
helm install blockchain-prod ./helm/blockchain-network \
  -f ./helm/blockchain-network/values-prod.yaml \
  -n prod --create-namespace
```

---

## Обновление и откат

### Обновление

```bash
# Изменить values
helm upgrade blockchain ./helm/blockchain-network \
  -n blockchain \
  --set nodes.miner.replicaCount=2

# Или с файлом
helm upgrade blockchain ./helm/blockchain-network \
  -n blockchain \
  -f values-updated.yaml

# Install если нет, upgrade если есть
helm upgrade --install blockchain ./helm/blockchain-network \
  -n blockchain --create-namespace
```

### История и откат

```bash
# История ревизий
helm history blockchain -n blockchain
# REVISION  STATUS      DESCRIPTION
# 1         superseded  Install complete
# 2         deployed    Upgrade complete

# Откат на предыдущую версию
helm rollback blockchain -n blockchain

# Откат на конкретную ревизию
helm rollback blockchain 1 -n blockchain
```

---

## Упаковка и распространение

### Упаковка chart

```bash
# Создать архив
helm package ./helm/blockchain-network
# Successfully packaged chart and saved it to: blockchain-network-0.1.0.tgz

# С другой версией
helm package ./helm/blockchain-network --version 0.2.0
```

### Создание локального репозитория

```bash
# Создать index.yaml
helm repo index . --url https://example.com/charts

# Или для локального использования
helm repo index .

# Структура:
# ./
# ├── blockchain-network-0.1.0.tgz
# └── index.yaml
```

### Публикация в GitHub Pages

```bash
# 1. Создать gh-pages branch
git checkout --orphan gh-pages

# 2. Положить .tgz и index.yaml
helm package ./helm/blockchain-network
helm repo index . --url https://username.github.io/repo-name

# 3. Push
git add .
git commit -m "Publish chart"
git push origin gh-pages

# 4. Добавить репозиторий
helm repo add my-charts https://username.github.io/repo-name
helm repo update

# 5. Установить из репозитория
helm install blockchain my-charts/blockchain-network
```

---

## Полный скрипт деплоя

```bash
#!/bin/bash
# deploy-with-helm.sh

set -e

CLUSTER_NAME="blockchain-cluster"
NAMESPACE="blockchain"
RELEASE_NAME="blockchain"

echo "=== 1. Проверка Kind кластера ==="
if ! kind get clusters | grep -q "$CLUSTER_NAME"; then
    echo "Создание кластера..."
    kind create cluster --config k8s/kind-config.yaml --name "$CLUSTER_NAME"
fi

echo "=== 2. Сборка Docker образа ==="
./gradlew bootJar
docker build -t blockchain-node:latest -f docker/Dockerfile .

echo "=== 3. Загрузка образа в Kind ==="
kind load docker-image blockchain-node:latest --name "$CLUSTER_NAME"

echo "=== 4. Установка Ingress Controller ==="
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

echo "=== 5. Деплой через Helm ==="
helm upgrade --install "$RELEASE_NAME" ./helm/blockchain-network \
  --namespace "$NAMESPACE" \
  --create-namespace \
  --wait \
  --timeout 5m

echo "=== 6. Проверка ==="
kubectl get pods -n "$NAMESPACE"
helm status "$RELEASE_NAME" -n "$NAMESPACE"

echo ""
echo "=== Готово! ==="
echo "Добавьте в /etc/hosts:"
echo "127.0.0.1 miner.localhost wallet.localhost blockchain.localhost"
echo ""
echo "Тест: curl http://miner.localhost/api/node/health"
```

---

## Итоги курса

### Что мы изучили

```
┌─────────────────────────────────────────────────────────────┐
│              DOCKER & KUBERNETES КУРС                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Модуль 1: Docker (уроки 1-5)                               │
│  ✓ Основы контейнеризации                                  │
│  ✓ Dockerfile для Java                                     │
│  ✓ Docker Compose                                          │
│  ✓ Multi-stage builds                                      │
│  ✓ Production best practices                               │
│                                                             │
│  Модуль 2: Kubernetes Basics (уроки 6-10)                   │
│  ✓ Архитектура K8s                                         │
│  ✓ Deployments, ReplicaSets, Labels                        │
│  ✓ Services, DNS, Ingress                                  │
│  ✓ ConfigMaps, Secrets                                     │
│  ✓ Практика деплоя в Kind                                  │
│                                                             │
│  Модуль 3: K8s Advanced & Helm (уроки 11-15)                │
│  ✓ Resources, Probes, Graceful Shutdown                    │
│  ✓ PersistentVolumes, StatefulSets                         │
│  ✓ Helm charts                                             │
│  ✓ Troubleshooting                                         │
│  ✓ Production-ready Helm chart                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Созданные артефакты

```
block-chain-study/
├── docker/
│   ├── Dockerfile              # Multi-stage build
│   └── docker-compose.yml      # 3-node network
│
├── k8s/
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── *-deployment.yaml       # Miner, Wallet, FullNode
│   ├── services.yaml
│   ├── ingress.yaml
│   ├── kind-config.yaml
│   └── kustomization.yaml
│
├── helm/blockchain-network/
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── _helpers.tpl
│       ├── configmap.yaml
│       ├── *-deployment.yaml
│       ├── services.yaml
│       ├── ingress.yaml
│       └── NOTES.txt
│
└── lessons-docker-k8s/
    ├── module-1-docker/        # 5 уроков
    ├── module-2-k8s-basics/    # 5 уроков
    └── module-3-k8s-advanced/  # 5 уроков
```

### Команды для работы

| Задача | Команда |
|--------|---------|
| Собрать образ | `docker build -t blockchain-node:latest -f docker/Dockerfile .` |
| Запустить локально | `docker-compose -f docker/docker-compose.yml up` |
| Создать кластер | `kind create cluster --config k8s/kind-config.yaml` |
| Загрузить образ | `kind load docker-image blockchain-node:latest` |
| Деплой K8s | `kubectl apply -k k8s/` |
| Деплой Helm | `helm install blockchain ./helm/blockchain-network` |
| Обновить | `helm upgrade blockchain ./helm/blockchain-network` |
| Откатить | `helm rollback blockchain` |
| Удалить | `helm uninstall blockchain` |

---

## Что дальше?

### Рекомендуемые темы для изучения

1. **GitOps и ArgoCD**
   - Автоматический деплой из Git
   - Declarative continuous delivery

2. **Observability**
   - Prometheus + Grafana для мониторинга
   - Jaeger для distributed tracing
   - ELK/Loki для логов

3. **Service Mesh**
   - Istio или Linkerd
   - mTLS, traffic management

4. **Security**
   - Pod Security Standards
   - Network Policies
   - OPA/Gatekeeper

5. **CI/CD**
   - GitHub Actions + Helm
   - Automated testing
   - Canary deployments

### Полезные ресурсы

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Helm Documentation](https://helm.sh/docs/)
- [CNCF Landscape](https://landscape.cncf.io/)
- [Kubernetes Patterns](https://k8spatterns.io/)

---

## Вопросы для самопроверки (финальные)

1. **Как запустить blockchain-сеть одной командой?**

   <details>
   <summary>Ответ</summary>

   ```bash
   helm install blockchain ./helm/blockchain-network -n blockchain --create-namespace
   ```
   </details>

2. **Как изменить количество wallet нод без редактирования файлов?**

   <details>
   <summary>Ответ</summary>

   ```bash
   helm upgrade blockchain ./helm/blockchain-network \
     --set nodes.wallet.replicaCount=5
   ```
   </details>

3. **Как использовать разные конфигурации для dev и prod?**

   <details>
   <summary>Ответ</summary>

   Создать values-dev.yaml и values-prod.yaml, использовать при установке:
   ```bash
   helm install blockchain-dev ./helm/blockchain-network -f values-dev.yaml
   helm install blockchain-prod ./helm/blockchain-network -f values-prod.yaml
   ```
   </details>

4. **Как откатить неудачный деплой?**

   <details>
   <summary>Ответ</summary>

   ```bash
   helm rollback blockchain -n blockchain
   # или на конкретную ревизию
   helm rollback blockchain 1 -n blockchain
   ```
   </details>

5. **Где хранится конфигурация blockchain в нашем chart?**

   <details>
   <summary>Ответ</summary>

   В ConfigMap, который создаётся из `templates/configmap.yaml` с значениями из `values.yaml` (секция `blockchain:`).
   </details>

---

## 🎉 Поздравляем!

Вы завершили курс **Docker & Kubernetes для Java-разработчика**!

Теперь вы умеете:
- ✅ Контейнеризировать Java-приложения
- ✅ Разворачивать в Kubernetes
- ✅ Настраивать сеть и Ingress
- ✅ Управлять конфигурацией
- ✅ Использовать Helm для пакетирования
- ✅ Диагностировать проблемы
- ✅ Деплоить blockchain-сеть в продакшен!

**Happy coding and deploying!** 🚀
