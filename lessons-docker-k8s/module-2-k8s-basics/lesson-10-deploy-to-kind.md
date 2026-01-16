# Урок 10: Практика — Деплой blockchain-сети в Kind

## Цели урока

После этого урока вы будете:
- Уметь развернуть локальный K8s кластер с Kind
- Понимать, как загружать локальные образы в Kind
- Уметь деплоить многокомпонентное приложение
- Знать, как отлаживать проблемы деплоя
- Иметь работающую blockchain-сеть в Kubernetes

---

## Что мы будем деплоить

```
┌─────────────────────────────────────────────────────────────────────┐
│                    BLOCKCHAIN NETWORK В K8S                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Internet                                                          │
│       │                                                             │
│       ▼                                                             │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                    Ingress Controller                       │    │
│  │                  (nginx, порты 80/443)                      │    │
│  └──────────────────────────┬──────────────────────────────────┘    │
│                             │                                       │
│         ┌───────────────────┼───────────────────┐                   │
│         │                   │                   │                   │
│         ▼                   ▼                   ▼                   │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐            │
│  │   Service   │     │   Service   │     │   Service   │            │
│  │ miner-node  │     │ wallet-node │     │  full-node  │            │
│  └──────┬──────┘     └──────┬──────┘     └──────┬──────┘            │
│         │                   │                   │                   │
│         ▼                   ▼                   ▼                   │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐            │
│  │ Deployment  │     │ Deployment  │     │ Deployment  │            │
│  │  (1 pod)    │     │  (2 pods)   │     │  (1 pod)    │            │
│  └─────────────┘     └─────────────┘     └─────────────┘            │
│                                                                     │
│  ConfigMaps: blockchain-config, blockchain-peers                    │
│  Secrets: blockchain-secrets                                        │
│  Namespace: blockchain                                              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Подготовка окружения

### Шаг 1: Проверка инструментов

```bash
# Docker
docker --version
# Docker version 24.0.0 или выше

# kubectl
kubectl version --client
# Client Version: v1.28.0 или выше

# Kind
kind version
# kind v0.20.0 или выше

# Если чего-то нет — установите (см. Урок 6)
```

### Шаг 2: Структура файлов

```
block-chain-study/
├── docker/
│   ├── Dockerfile           # Образ blockchain-node
│   └── docker-compose.yml   # Для локального теста
├── k8s/
│   ├── kind-config.yaml     # Конфигурация Kind кластера
│   ├── namespace.yaml       # Namespace blockchain
│   ├── configmap.yaml       # ConfigMaps
│   ├── secret.yaml          # Secrets (демо)
│   ├── miner-deployment.yaml
│   ├── wallet-deployment.yaml
│   ├── full-node-deployment.yaml
│   ├── services.yaml        # Все Services
│   ├── ingress.yaml         # Ingress правила
│   └── kustomization.yaml   # Для kubectl apply -k
└── src/                     # Исходный код
```

---

## Этап 1: Создание Kind кластера

### Kind конфигурация

```yaml
# k8s/kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: blockchain-cluster

nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      # HTTP для Ingress
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      # HTTPS для Ingress
      - containerPort: 443
        hostPort: 443
        protocol: TCP
```

### Создание кластера

```bash
# Удалить старый кластер (если есть)
kind delete cluster --name blockchain-cluster 2>/dev/null

# Создать новый
kind create cluster --config k8s/kind-config.yaml

# Проверить
kubectl cluster-info --context kind-blockchain-cluster
# Kubernetes control plane is running at https://127.0.0.1:xxxxx

kubectl get nodes
# NAME                               STATUS   ROLES           AGE   VERSION
# blockchain-cluster-control-plane   Ready    control-plane   1m    v1.28.0
```

---

## Этап 2: Сборка и загрузка Docker образа

### Сборка образа

```bash
# Перейти в корень проекта
cd /path/to/block-chain-study

# Собрать JAR
./gradlew bootJar

# Собрать Docker образ
docker build -t blockchain-node:latest -f docker/Dockerfile .

# Проверить
docker images | grep blockchain-node
# blockchain-node   latest   abc123def456   10 seconds ago   210MB
```

### Загрузка в Kind

**Важно**: Kind не видит локальные Docker образы автоматически!

```bash
# Загрузить образ в Kind кластер
kind load docker-image blockchain-node:latest --name blockchain-cluster

# Проверить (внутри Kind node)
docker exec -it blockchain-cluster-control-plane crictl images | grep blockchain
# docker.io/library/blockchain-node   latest   abc123def456   5 minutes ago   210MB
```

**Альтернатива**: использовать локальный registry

```bash
# Запустить локальный registry
docker run -d -p 5000:5000 --name registry registry:2

# Тегировать и пушить
docker tag blockchain-node:latest localhost:5000/blockchain-node:latest
docker push localhost:5000/blockchain-node:latest

# В deployment использовать: localhost:5000/blockchain-node:latest
```

---

## Этап 3: Установка Ingress Controller

```bash
# Установить nginx ingress для Kind
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Ждать готовности (может занять 1-2 минуты)
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

# Проверить
kubectl get pods -n ingress-nginx
# NAME                                        READY   STATUS    RESTARTS   AGE
# ingress-nginx-controller-xxxxxxxxx-xxxxx   1/1     Running   0          2m
```

---

## Этап 4: Деплой приложения

### Способ 1: По одному файлу

```bash
# 1. Namespace
kubectl apply -f k8s/namespace.yaml
# namespace/blockchain created

# 2. ConfigMaps
kubectl apply -f k8s/configmap.yaml
# configmap/blockchain-config created
# configmap/blockchain-peers created

# 3. Secrets (создаём императивно для безопасности)
kubectl create secret generic blockchain-secrets \
  --from-literal=api-key=demo-api-key-12345 \
  --namespace=blockchain
# secret/blockchain-secrets created

# 4. Deployments
kubectl apply -f k8s/miner-deployment.yaml
kubectl apply -f k8s/wallet-deployment.yaml
kubectl apply -f k8s/full-node-deployment.yaml

# 5. Services
kubectl apply -f k8s/services.yaml

# 6. Ingress
kubectl apply -f k8s/ingress.yaml
```

### Способ 2: Через Kustomize (рекомендуется)

```bash
# Применить всё сразу
kubectl apply -k k8s/

# Или просмотреть что будет применено
kubectl kustomize k8s/
```

### Проверка деплоя

```bash
# Все ресурсы в namespace blockchain
kubectl get all -n blockchain

# Ожидаемый вывод:
# NAME                               READY   STATUS    RESTARTS   AGE
# pod/full-node-xxxxxxxxx-xxxxx     1/1     Running   0          1m
# pod/miner-node-xxxxxxxxx-xxxxx    1/1     Running   0          1m
# pod/wallet-node-xxxxxxxxx-xxxxx   1/1     Running   0          1m
# pod/wallet-node-xxxxxxxxx-yyyyy   1/1     Running   0          1m
#
# NAME                      TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)
# service/blockchain-peers  ClusterIP   None            <none>        8080/TCP
# service/full-node         ClusterIP   10.96.x.x       <none>        8080/TCP
# service/miner-node        ClusterIP   10.96.x.x       <none>        8080/TCP
# service/wallet-node       ClusterIP   10.96.x.x       <none>        8080/TCP
#
# NAME                          READY   UP-TO-DATE   AVAILABLE   AGE
# deployment.apps/full-node     1/1     1            1           1m
# deployment.apps/miner-node    1/1     1            1           1m
# deployment.apps/wallet-node   2/2     2            2           1m
```

---

## Этап 5: Проверка работоспособности

### Проверка Pod

```bash
# Статус Pod
kubectl get pods -n blockchain

# Если Pod не Running — смотрим детали
kubectl describe pod <pod-name> -n blockchain

# Логи
kubectl logs -f deployment/miner-node -n blockchain

# Shell внутрь Pod (для отладки)
kubectl exec -it deployment/miner-node -n blockchain -- /bin/sh
```

### Проверка Services

```bash
# Endpoints (должны быть IP Pod)
kubectl get endpoints -n blockchain

# NAME               ENDPOINTS
# blockchain-peers   10.244.0.5:8080,10.244.0.6:8080,10.244.0.7:8080,10.244.0.8:8080
# full-node          10.244.0.7:8080
# miner-node         10.244.0.5:8080
# wallet-node        10.244.0.6:8080,10.244.0.8:8080

# Если ENDPOINTS пусто — проблема с selector/labels
```

### Проверка DNS

```bash
# Запустить тестовый Pod
kubectl run dns-test --image=busybox:1.36 -n blockchain --rm -it -- sh

# Внутри:
nslookup miner-node
# Server:    10.96.0.10
# Address:   10.96.0.10:53
# Name:      miner-node.blockchain.svc.cluster.local
# Address:   10.96.x.x

wget -qO- http://miner-node:8080/api/node/health
# {"status":"UP"}
```

### Проверка Ingress

```bash
# Статус Ingress
kubectl get ingress -n blockchain

# NAME                       CLASS   HOSTS                              ADDRESS     PORTS
# blockchain-ingress         nginx   miner.localhost,wallet.localhost   localhost   80
# blockchain-ingress-simple  nginx   ...                                localhost   80
```

### Настройка /etc/hosts

```bash
# Linux/macOS: sudo nano /etc/hosts
# Windows: C:\Windows\System32\drivers\etc\hosts (от админа)

# Добавить:
127.0.0.1 miner.localhost
127.0.0.1 wallet.localhost
127.0.0.1 full.localhost
127.0.0.1 blockchain.localhost
```

### Тестирование API

```bash
# Через host-based routing
curl http://miner.localhost/api/node/health
# {"status":"UP","nodeRole":"miner",...}

curl http://wallet.localhost/api/wallet/address
# {"address":"..."}

curl http://blockchain.localhost/api/blocks/height
# {"height":0}

# Через path-based routing (если настроен)
curl http://localhost/miner/api/node/health
curl http://localhost/wallet/api/wallet/address

# Port-forward (альтернатива, если Ingress не работает)
kubectl port-forward service/miner-node 8080:8080 -n blockchain &
curl http://localhost:8080/api/node/health
```

---

## Этап 6: Тестирование blockchain функционала

### Проверка синхронизации

```bash
# Высота цепочки на всех нодах
curl -s http://miner.localhost/api/blocks/height
curl -s http://wallet.localhost/api/blocks/height
curl -s http://full.localhost/api/blocks/height

# Должна быть одинаковая (или почти, если идёт синхронизация)
```

### Создание транзакции

```bash
# Получить адрес кошелька
WALLET_ADDRESS=$(curl -s http://wallet.localhost/api/wallet/address | jq -r '.address')
echo "Wallet: $WALLET_ADDRESS"

# Баланс (изначально 0)
curl -s http://wallet.localhost/api/wallet/balance
# {"balance":0}

# Для получения баланса нужно сначала замайнить блок
curl -X POST http://miner.localhost/api/mining/mine
# {"message":"Block mined","blockIndex":1}

# Проверить баланс на miner (он получил genesis reward)
curl -s http://miner.localhost/api/wallet/balance
# {"balance":10.0}  # genesis reward
```

### Масштабирование

```bash
# Увеличить количество wallet нод
kubectl scale deployment wallet-node --replicas=3 -n blockchain

# Проверить
kubectl get pods -n blockchain -l app.kubernetes.io/name=wallet-node
# NAME                           READY   STATUS    RESTARTS   AGE
# wallet-node-xxxxxxxxx-aaaaa   1/1     Running   0          5m
# wallet-node-xxxxxxxxx-bbbbb   1/1     Running   0          5m
# wallet-node-xxxxxxxxx-ccccc   1/1     Running   0          10s

# Service автоматически включит новый Pod
kubectl get endpoints wallet-node -n blockchain
```

---

## Отладка проблем

### Pod не запускается

```bash
# 1. Проверить статус
kubectl get pods -n blockchain
# STATUS: Pending / ImagePullBackOff / CrashLoopBackOff / Error

# 2. Детали события
kubectl describe pod <pod-name> -n blockchain
# Смотрите секцию Events в конце

# 3. Частые проблемы:

# ImagePullBackOff — образ не найден
# Решение: kind load docker-image blockchain-node:latest --name blockchain-cluster

# Pending — нет ресурсов
# Решение: уменьшить requests в deployment

# CrashLoopBackOff — приложение падает
# Решение: kubectl logs <pod-name> -n blockchain --previous
```

### Service не работает

```bash
# 1. Проверить Endpoints
kubectl get endpoints <service-name> -n blockchain
# Если пусто — selector не совпадает с labels Pod

# 2. Проверить selector
kubectl describe service <service-name> -n blockchain
# Selector: app.kubernetes.io/name=miner-node

# 3. Проверить labels Pod
kubectl get pods -n blockchain --show-labels

# 4. Labels должны совпадать!
```

### Ingress не работает

```bash
# 1. Ingress Controller запущен?
kubectl get pods -n ingress-nginx
# Должен быть Running

# 2. Ingress создан?
kubectl get ingress -n blockchain

# 3. Логи Ingress Controller
kubectl logs -n ingress-nginx -l app.kubernetes.io/component=controller --tail=50

# 4. /etc/hosts настроен?
cat /etc/hosts | grep localhost

# 5. Port 80 свободен?
# Windows: netstat -ano | findstr :80
# Linux: ss -tlnp | grep :80
```

### Приложение работает, но ошибки внутри

```bash
# 1. Логи приложения
kubectl logs deployment/miner-node -n blockchain -f

# 2. Shell внутрь
kubectl exec -it deployment/miner-node -n blockchain -- /bin/sh

# Внутри проверить:
env | grep BLOCKCHAIN   # Переменные окружения
wget -qO- http://localhost:8080/api/node/health  # Health check
cat /app/config/*       # Примонтированные конфиги (если есть)

# 3. Проверить connectivity между Pod
kubectl exec -it deployment/miner-node -n blockchain -- \
  wget -qO- http://wallet-node:8080/api/node/health
```

---

## Полезные команды для работы

### Быстрые команды

```bash
# Алиасы (добавить в ~/.bashrc или ~/.zshrc)
alias k='kubectl'
alias kgp='kubectl get pods'
alias kgs='kubectl get services'
alias kgi='kubectl get ingress'
alias kd='kubectl describe'
alias kl='kubectl logs -f'
alias ke='kubectl exec -it'

# Установить namespace по умолчанию
kubectl config set-context --current --namespace=blockchain
```

### Мониторинг в реальном времени

```bash
# Следить за Pod
kubectl get pods -n blockchain -w

# Следить за событиями
kubectl get events -n blockchain -w --sort-by='.lastTimestamp'

# Dashboard (опционально)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml
kubectl proxy
# Открыть: http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/
```

### Очистка

```bash
# Удалить все ресурсы в namespace
kubectl delete all --all -n blockchain

# Удалить namespace (и всё в нём)
kubectl delete namespace blockchain

# Удалить Kind кластер полностью
kind delete cluster --name blockchain-cluster
```

---

## Скрипт для быстрого деплоя

```bash
#!/bin/bash
# deploy.sh — полный деплой blockchain в Kind

set -e  # Остановиться при ошибке

echo "=== 1. Создание Kind кластера ==="
kind delete cluster --name blockchain-cluster 2>/dev/null || true
kind create cluster --config k8s/kind-config.yaml

echo "=== 2. Сборка Docker образа ==="
./gradlew bootJar
docker build -t blockchain-node:latest -f docker/Dockerfile .

echo "=== 3. Загрузка образа в Kind ==="
kind load docker-image blockchain-node:latest --name blockchain-cluster

echo "=== 4. Установка Ingress Controller ==="
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

echo "=== 5. Деплой приложения ==="
kubectl apply -k k8s/

echo "=== 6. Ожидание готовности Pod ==="
kubectl wait --namespace blockchain \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/part-of=blockchain-network \
  --timeout=120s

echo "=== 7. Проверка ==="
kubectl get all -n blockchain

echo ""
echo "=== Готово! ==="
echo "Добавьте в /etc/hosts:"
echo "127.0.0.1 miner.localhost wallet.localhost full.localhost blockchain.localhost"
echo ""
echo "Тест: curl http://miner.localhost/api/node/health"
```

```bash
# Сделать исполняемым и запустить
chmod +x deploy.sh
./deploy.sh
```

---

## Вопросы для самопроверки

### Практические

1. **Как загрузить локальный Docker образ в Kind?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kind load docker-image image-name:tag --name cluster-name
   ```
   </details>

2. **Pod в статусе ImagePullBackOff. Что делать?**

   <details>
   <summary>Ответ</summary>

   - Проверить имя образа в deployment
   - Загрузить образ в Kind: `kind load docker-image ...`
   - Или использовать `imagePullPolicy: IfNotPresent`
   </details>

3. **Как проверить, что Service видит Pod?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl get endpoints service-name -n namespace
   # Должны быть IP Pod
   ```
   </details>

4. **Ingress установлен, но curl не работает. Что проверить?**

   <details>
   <summary>Ответ</summary>

   - Ingress Controller запущен: `kubectl get pods -n ingress-nginx`
   - /etc/hosts настроен
   - Port 80 не занят другим приложением
   - Логи controller: `kubectl logs -n ingress-nginx -l app.kubernetes.io/component=controller`
   </details>

5. **Как посмотреть логи всех Pod deployment одновременно?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl logs -f deployment/name -n namespace --all-containers

   # Или использовать stern (сторонний инструмент)
   stern miner-node -n blockchain
   ```
   </details>

6. **Как обновить приложение после изменения кода?**

   <details>
   <summary>Ответ</summary>

   ```bash
   # 1. Пересобрать
   ./gradlew bootJar
   docker build -t blockchain-node:latest -f docker/Dockerfile .

   # 2. Загрузить в Kind
   kind load docker-image blockchain-node:latest --name blockchain-cluster

   # 3. Перезапустить deployment
   kubectl rollout restart deployment -n blockchain
   ```
   </details>

7. **Endpoints Service пустые. Как диагностировать?**

   <details>
   <summary>Ответ</summary>

   ```bash
   # 1. Проверить selector Service
   kubectl describe svc service-name -n namespace | grep Selector

   # 2. Проверить labels Pod
   kubectl get pods -n namespace --show-labels

   # 3. Убедиться что labels совпадают с selector
   ```
   </details>

8. **Как выполнить команду во всех Pod deployment?**

   <details>
   <summary>Ответ</summary>

   ```bash
   # Получить список Pod
   for pod in $(kubectl get pods -n blockchain -l app.kubernetes.io/name=wallet-node -o name); do
     echo "=== $pod ==="
     kubectl exec -n blockchain $pod -- env | grep BLOCKCHAIN
   done
   ```
   </details>

9. **Как быстро удалить и пересоздать всё?**

   <details>
   <summary>Ответ</summary>

   ```bash
   # Вариант 1: удалить namespace
   kubectl delete namespace blockchain
   kubectl apply -k k8s/

   # Вариант 2: удалить кластер
   kind delete cluster --name blockchain-cluster
   # И запустить deploy.sh заново
   ```
   </details>

10. **Приложение падает сразу после старта (CrashLoopBackOff). Как отладить?**

    <details>
    <summary>Ответ</summary>

    ```bash
    # 1. Логи текущего контейнера
    kubectl logs pod-name -n namespace

    # 2. Логи предыдущего (упавшего) контейнера
    kubectl logs pod-name -n namespace --previous

    # 3. Describe для событий
    kubectl describe pod pod-name -n namespace

    # 4. Проверить env переменные и ConfigMaps
    # 5. Проверить что образ содержит правильное приложение
    ```
    </details>

---

## Итоги урока

### Чеклист успешного деплоя

```
□ Kind кластер создан и работает
□ Docker образ собран
□ Образ загружен в Kind
□ Ingress Controller установлен
□ Namespace создан
□ ConfigMaps применены
□ Secrets созданы
□ Deployments применены и Pod'ы Running
□ Services созданы и имеют Endpoints
□ Ingress настроен
□ /etc/hosts обновлён
□ API отвечает через Ingress
```

### Команды для ежедневной работы

| Задача | Команда |
|--------|---------|
| Создать кластер | `kind create cluster --config k8s/kind-config.yaml` |
| Загрузить образ | `kind load docker-image image:tag --name cluster` |
| Применить манифесты | `kubectl apply -k k8s/` |
| Проверить Pod | `kubectl get pods -n blockchain` |
| Логи | `kubectl logs -f deployment/name -n blockchain` |
| Shell в Pod | `kubectl exec -it pod-name -n blockchain -- /bin/sh` |
| Перезапустить | `kubectl rollout restart deployment -n blockchain` |
| Удалить кластер | `kind delete cluster --name blockchain-cluster` |

---

## Что дальше?

**Модуль 2 завершён!** Вы умеете:
- Разворачивать K8s кластер локально
- Деплоить многокомпонентные приложения
- Настраивать сеть и Ingress
- Управлять конфигурацией
- Отлаживать проблемы

В **Модуле 3** (уроки 11-15) изучим:
- Resource limits и Probes
- Stateful приложения и Persistent Volumes
- Helm для пакетирования
- Troubleshooting и observability
- Создание полноценного Helm chart для blockchain-сети
