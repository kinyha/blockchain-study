# Урок 8: Networking — Services, DNS, Ingress

## Цели урока

После этого урока вы будете:
- Понимать сетевую модель Kubernetes
- Знать типы Services: ClusterIP, NodePort, LoadBalancer, Headless
- Понимать, как работает DNS в кластере
- Уметь настраивать Ingress для HTTP-маршрутизации
- Знать, как Pod'ы общаются друг с другом

---

## Проблема: как найти Pod?

Pod'ы — эфемерные. Они создаются, удаляются, перемещаются между узлами.

```
┌─────────────────────────────────────────────────────────────┐
│                 ПРОБЛЕМА ЭФЕМЕРНЫХ POD                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Deployment создал 3 Pod:                                   │
│  • miner-node-abc12 → IP: 10.244.1.5                       │
│  • miner-node-def34 → IP: 10.244.2.8                       │
│  • miner-node-ghi56 → IP: 10.244.1.12                      │
│                                                             │
│  Клиент хочет подключиться... к какому IP?                  │
│                                                             │
│  Через 5 минут:                                             │
│  • miner-node-abc12 удалён (scale down)                    │
│  • miner-node-xyz99 создан → IP: 10.244.3.2 (новый!)       │
│                                                             │
│  IP изменились! Как клиенту об этом узнать?                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Решение**: Service — стабильный endpoint для группы Pod.

---

## Сетевая модель Kubernetes

### Основные принципы

```
┌─────────────────────────────────────────────────────────────┐
│               СЕТЕВАЯ МОДЕЛЬ K8S                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Каждый Pod имеет уникальный IP                          │
│     (не нужен port mapping как в Docker)                    │
│                                                             │
│  2. Pod на любом Node может общаться с Pod на любом Node    │
│     напрямую по IP (без NAT)                                │
│                                                             │
│  3. Агенты на Node (kubelet) видят тот же IP Pod,           │
│     что и сам Pod                                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘

         Node 1                          Node 2
    ┌─────────────────┐            ┌─────────────────┐
    │ Pod A           │            │ Pod C           │
    │ 10.244.1.5      │◄──────────►│ 10.244.2.8      │
    │                 │  Прямая    │                 │
    │ Pod B           │  связь     │ Pod D           │
    │ 10.244.1.6      │◄──────────►│ 10.244.2.9      │
    └─────────────────┘            └─────────────────┘
```

### Типы сетей в кластере

```
┌─────────────────────────────────────────────────────────────┐
│                    СЕТЕВЫЕ ДИАПАЗОНЫ                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Pod Network (Pod CIDR):     10.244.0.0/16                  │
│  └── IP для Pod              (65,536 адресов)               │
│                                                             │
│  Service Network (Service CIDR): 10.96.0.0/12               │
│  └── Виртуальные IP для Services (1,048,576 адресов)        │
│                                                             │
│  Node Network:               192.168.1.0/24                 │
│  └── IP хостов (вне K8s)     (256 адресов)                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Service — стабильный endpoint

### Как работает Service

```
┌─────────────────────────────────────────────────────────────┐
│                      SERVICE                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Client                                                    │
│     │                                                       │
│     │  Запрос к 10.96.0.100:8080 (Service IP)              │
│     │                                                       │
│     ▼                                                       │
│  ┌────────────────────────────────────────────┐             │
│  │            Service: miner-node             │             │
│  │            ClusterIP: 10.96.0.100          │             │
│  │            Port: 8080                      │             │
│  │                                            │             │
│  │   selector: app=miner-node                 │             │
│  └────────────────────┬───────────────────────┘             │
│                       │                                     │
│         ┌─────────────┼─────────────┐                       │
│         │             │             │                       │
│         ▼             ▼             ▼                       │
│    ┌─────────┐   ┌─────────┐   ┌─────────┐                  │
│    │  Pod 1  │   │  Pod 2  │   │  Pod 3  │                  │
│    │10.244.1.5   │10.244.2.8   │10.244.1.12                 │
│    │:8080    │   │:8080    │   │:8080    │                  │
│    └─────────┘   └─────────┘   └─────────┘                  │
│                                                             │
│   kube-proxy на каждом Node создаёт правила iptables:       │
│   10.96.0.100:8080 → random(10.244.1.5, 10.244.2.8, ...)   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Структура Service

```yaml
# k8s/services.yaml
apiVersion: v1
kind: Service
metadata:
  name: miner-node              # Имя Service
  namespace: blockchain
  labels:
    app.kubernetes.io/name: miner-node
spec:
  type: ClusterIP               # Тип Service
  selector:                     # Какие Pod включать
    app.kubernetes.io/name: miner-node
  ports:
    - name: http                # Имя порта (для Ingress)
      port: 8080                # Порт Service
      targetPort: http          # Порт Pod (имя или число)
      protocol: TCP
```

---

## Типы Services

### 1. ClusterIP (по умолчанию)

```
┌─────────────────────────────────────────────────────────────┐
│                      ClusterIP                               │
│              Доступен ТОЛЬКО внутри кластера                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌─────────────────────────────────────────────────────┐   │
│   │                 Kubernetes Cluster                  │   │
│   │                                                     │   │
│   │   Client Pod ──────► Service (10.96.0.100) ──► Pods │   │
│   │                                                     │   │
│   └─────────────────────────────────────────────────────┘   │
│                                                             │
│   Внешний мир: НЕТ ДОСТУПА                                  │
│                                                             │
│   Использование:                                            │
│   • Внутренние сервисы (БД, кэш)                           │
│   • Backend API (доступ через Ingress)                      │
│   • Межсервисное общение                                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

```yaml
apiVersion: v1
kind: Service
metadata:
  name: miner-node
spec:
  type: ClusterIP        # Можно не указывать (по умолчанию)
  selector:
    app: miner-node
  ports:
    - port: 8080
      targetPort: 8080
```

### 2. NodePort

```
┌─────────────────────────────────────────────────────────────┐
│                       NodePort                               │
│            Доступен на порту каждого Node                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Внешний мир                                               │
│        │                                                    │
│        │  http://192.168.1.10:30080                        │
│        │  http://192.168.1.11:30080                        │
│        │  http://192.168.1.12:30080  (любой Node)          │
│        │                                                    │
│        ▼                                                    │
│   ┌─────────────────────────────────────────────────────┐   │
│   │                 Kubernetes Cluster                  │   │
│   │                                                     │   │
│   │  Node 1 (:30080) ──┐                                │   │
│   │  Node 2 (:30080) ──┼──► Service ──► Pods            │   │
│   │  Node 3 (:30080) ──┘                                │   │
│   │                                                     │   │
│   └─────────────────────────────────────────────────────┘   │
│                                                             │
│   Диапазон портов: 30000-32767 (по умолчанию)               │
│                                                             │
│   Использование:                                            │
│   • Разработка/тестирование                                 │
│   • On-premise без LoadBalancer                             │
│   • Не рекомендуется для production                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

```yaml
apiVersion: v1
kind: Service
metadata:
  name: miner-node-external
spec:
  type: NodePort
  selector:
    app: miner-node
  ports:
    - port: 8080          # Порт Service (внутри кластера)
      targetPort: 8080    # Порт Pod
      nodePort: 30080     # Порт на Node (30000-32767)
```

### 3. LoadBalancer

```
┌─────────────────────────────────────────────────────────────┐
│                     LoadBalancer                             │
│         Внешний балансировщик от облачного провайдера        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Внешний мир                                               │
│        │                                                    │
│        │  http://34.123.45.67 (External IP)                │
│        │                                                    │
│        ▼                                                    │
│   ┌─────────────────┐                                       │
│   │ Cloud LB        │  AWS ALB/NLB, GCP LB, Azure LB       │
│   │ (external IP)   │                                       │
│   └────────┬────────┘                                       │
│            │                                                │
│            ▼                                                │
│   ┌─────────────────────────────────────────────────────┐   │
│   │                 Kubernetes Cluster                  │   │
│   │                                                     │   │
│   │            Service (NodePort) ──► Pods              │   │
│   │                                                     │   │
│   └─────────────────────────────────────────────────────┘   │
│                                                             │
│   Использование:                                            │
│   • Production в облаке                                     │
│   • Когда нужен внешний IP                                  │
│   • Стоит денег! (за каждый LB)                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

```yaml
apiVersion: v1
kind: Service
metadata:
  name: miner-node-lb
spec:
  type: LoadBalancer
  selector:
    app: miner-node
  ports:
    - port: 80            # Внешний порт
      targetPort: 8080    # Порт Pod
```

```bash
# После создания
kubectl get svc miner-node-lb
# NAME            TYPE           CLUSTER-IP     EXTERNAL-IP    PORT(S)
# miner-node-lb   LoadBalancer   10.96.0.100    34.123.45.67   80:30123/TCP
#                                               ▲
#                                               │
#                                    Этот IP выдаёт облако
```

### 4. Headless Service (clusterIP: None)

```
┌─────────────────────────────────────────────────────────────┐
│                   Headless Service                           │
│              DNS возвращает IP всех Pod напрямую             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Обычный Service:                                          │
│   nslookup miner-node → 10.96.0.100 (один Service IP)      │
│                                                             │
│   Headless Service:                                         │
│   nslookup blockchain-peers → 10.244.1.5                   │
│                               10.244.2.8                   │
│                               10.244.1.12                  │
│                               (все Pod IP)                 │
│                                                             │
│   Использование:                                            │
│   • Peer-to-peer коммуникация                              │
│   • StatefulSets (стабильные DNS имена для каждого Pod)    │
│   • Service discovery для blockchain нод                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

```yaml
# k8s/services.yaml — Headless для peer discovery
apiVersion: v1
kind: Service
metadata:
  name: blockchain-peers
  namespace: blockchain
spec:
  type: ClusterIP
  clusterIP: None          # ← Headless!
  selector:
    app.kubernetes.io/part-of: blockchain-network
  ports:
    - name: http
      port: 8080
      targetPort: http
```

---

## DNS в Kubernetes

### CoreDNS

Kubernetes включает встроенный DNS-сервер (**CoreDNS**), который:
- Автоматически создаёт DNS-записи для Services
- Позволяет обращаться к сервисам по имени

```bash
# Проверить CoreDNS
kubectl get pods -n kube-system -l k8s-app=kube-dns
# NAME                       READY   STATUS    RESTARTS
# coredns-5d78c9869d-xxxxx   1/1     Running   0
# coredns-5d78c9869d-yyyyy   1/1     Running   0
```

### DNS-имена Services

```
┌─────────────────────────────────────────────────────────────┐
│                   DNS NAMING CONVENTION                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Полное имя (FQDN):                                         │
│  <service>.<namespace>.svc.cluster.local                    │
│                                                             │
│  Примеры:                                                   │
│  • miner-node.blockchain.svc.cluster.local                  │
│  • wallet-node.blockchain.svc.cluster.local                 │
│  • postgres.database.svc.cluster.local                      │
│                                                             │
│  Сокращения (в том же namespace):                           │
│  • miner-node                    # В том же namespace       │
│  • miner-node.blockchain         # Из другого namespace     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Примеры использования

```yaml
# Pod в namespace blockchain
apiVersion: v1
kind: Pod
metadata:
  name: client
  namespace: blockchain
spec:
  containers:
    - name: app
      image: curlimages/curl
      env:
        # Короткое имя (тот же namespace)
        - name: MINER_URL
          value: "http://miner-node:8080"

        # Полное имя (другой namespace)
        - name: POSTGRES_URL
          value: "http://postgres.database.svc.cluster.local:5432"

        # Headless Service — можно обращаться к конкретному Pod
        # pod-name.service-name.namespace.svc.cluster.local
        - name: PEER_0
          value: "http://miner-node-0.blockchain-peers.blockchain.svc.cluster.local:8080"
```

```bash
# Проверка DNS из Pod
kubectl exec -it client -n blockchain -- sh

# Внутри Pod:
nslookup miner-node
# Server:    10.96.0.10
# Name:      miner-node.blockchain.svc.cluster.local
# Address:   10.96.0.100

nslookup blockchain-peers
# Server:    10.96.0.10
# Name:      blockchain-peers.blockchain.svc.cluster.local
# Address:   10.244.1.5
# Address:   10.244.2.8
# Address:   10.244.1.12

curl http://miner-node:8080/api/node/health
```

---

## Ingress — HTTP-маршрутизация

### Зачем нужен Ingress

```
┌─────────────────────────────────────────────────────────────┐
│                  БЕЗ INGRESS                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Каждому сервису нужен отдельный LoadBalancer:             │
│                                                             │
│   miner.example.com  → LoadBalancer ($$$) → miner-svc       │
│   wallet.example.com → LoadBalancer ($$$) → wallet-svc      │
│   api.example.com    → LoadBalancer ($$$) → api-svc         │
│                                                             │
│   3 сервиса = 3 LoadBalancer = 3× стоимость                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    С INGRESS                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Один LoadBalancer для всех:                               │
│                                                             │
│   *.example.com → LoadBalancer → Ingress Controller         │
│                                        │                    │
│                           ┌────────────┼────────────┐       │
│                           │            │            │       │
│                           ▼            ▼            ▼       │
│                      miner-svc    wallet-svc    api-svc     │
│                                                             │
│   Маршрутизация по:                                         │
│   • Хосту (miner.example.com → miner-svc)                   │
│   • Пути (/api/blocks → miner-svc)                          │
│   • TLS termination                                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Компоненты Ingress

```
┌─────────────────────────────────────────────────────────────┐
│                  INGRESS АРХИТЕКТУРА                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   1. Ingress Resource (YAML)                                │
│      Описывает правила маршрутизации                        │
│                                                             │
│   2. Ingress Controller (Pod)                               │
│      Реализует правила (nginx, traefik, HAProxy...)         │
│      Нужно установить отдельно!                             │
│                                                             │
│            Internet                                         │
│                │                                            │
│                ▼                                            │
│   ┌────────────────────────┐                                │
│   │   Ingress Controller   │  nginx, traefik, etc.          │
│   │   (читает Ingress      │                                │
│   │    ресурсы и           │                                │
│   │    настраивает себя)   │                                │
│   └───────────┬────────────┘                                │
│               │                                             │
│      ┌────────┴────────┐                                    │
│      │                 │                                    │
│      ▼                 ▼                                    │
│   Service A         Service B                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Установка Ingress Controller (nginx)

```bash
# Для Kind кластера
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Ждём готовности
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s

# Проверка
kubectl get pods -n ingress-nginx
# NAME                                        READY   STATUS
# ingress-nginx-controller-xxxxx-yyyyy        1/1     Running
```

### Структура Ingress

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: blockchain-ingress
  namespace: blockchain
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /$2
spec:
  ingressClassName: nginx           # Какой controller использовать
  rules:
    # Маршрутизация по хосту
    - host: miner.localhost
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: miner-node
                port:
                  name: http

    - host: wallet.localhost
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: wallet-node
                port:
                  name: http

    # Маршрутизация по пути
    - http:
        paths:
          - path: /miner(/|$)(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: miner-node
                port:
                  name: http

          - path: /wallet(/|$)(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: wallet-node
                port:
                  name: http
```

### Path Types

```yaml
# 1. Prefix — начинается с пути
- path: /api
  pathType: Prefix
  # Совпадает: /api, /api/, /api/blocks, /api/blocks/1

# 2. Exact — точное совпадение
- path: /api
  pathType: Exact
  # Совпадает: только /api
  # НЕ совпадает: /api/, /api/blocks

# 3. ImplementationSpecific — зависит от Ingress Controller
- path: /api(/|$)(.*)
  pathType: ImplementationSpecific
  # Для nginx с regex
```

### TLS (HTTPS)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: blockchain-ingress-tls
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - blockchain.example.com
      secretName: blockchain-tls-secret   # Secret с сертификатом
  rules:
    - host: blockchain.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: full-node
                port:
                  name: http
```

```bash
# Создать Secret с сертификатом
kubectl create secret tls blockchain-tls-secret \
  --cert=path/to/tls.crt \
  --key=path/to/tls.key \
  -n blockchain
```

---

## Практика: сеть blockchain

### Шаг 1: Создание Services

```bash
# Применяем services
kubectl apply -f k8s/services.yaml

# Проверяем
kubectl get services -n blockchain
# NAME              TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)
# miner-node        ClusterIP   10.96.0.100     <none>        8080/TCP
# wallet-node       ClusterIP   10.96.0.101     <none>        8080/TCP
# full-node         ClusterIP   10.96.0.102     <none>        8080/TCP
# blockchain-peers  ClusterIP   None            <none>        8080/TCP
```

### Шаг 2: Проверка DNS

```bash
# Запускаем тестовый Pod
kubectl run dns-test --image=busybox:1.36 -n blockchain --rm -it -- sh

# Внутри Pod:
nslookup miner-node
# Address: 10.96.0.100

nslookup blockchain-peers
# Address: 10.244.1.5
# Address: 10.244.2.8

wget -qO- http://miner-node:8080/api/node/health
```

### Шаг 3: Установка Ingress Controller

```bash
# Для Kind
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Ждём
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s
```

### Шаг 4: Применение Ingress

```bash
kubectl apply -f k8s/ingress.yaml

# Проверяем
kubectl get ingress -n blockchain
# NAME                 CLASS   HOSTS                                ADDRESS     PORTS
# blockchain-ingress   nginx   miner.localhost,wallet.localhost     localhost   80

# Тестируем (добавьте в /etc/hosts: 127.0.0.1 miner.localhost)
curl http://miner.localhost/api/node/health
curl http://localhost/miner/api/node/health
```

### Настройка /etc/hosts для Kind

```bash
# Linux/macOS: /etc/hosts
# Windows: C:\Windows\System32\drivers\etc\hosts

127.0.0.1 miner.localhost
127.0.0.1 wallet.localhost
127.0.0.1 full.localhost
127.0.0.1 blockchain.localhost
```

---

## Network Policies (кратко)

**Network Policies** — firewall для Pod-to-Pod трафика.

```yaml
# Разрешить входящий трафик только от Pod с label app=frontend
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-frontend-only
  namespace: blockchain
spec:
  podSelector:
    matchLabels:
      app: miner-node
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: frontend
      ports:
        - protocol: TCP
          port: 8080
```

**Важно**: Network Policies требуют CNI plugin с поддержкой (Calico, Cilium). Kind с kindnet не поддерживает Network Policies по умолчанию.

---

## Отладка сети

### Проверка Service

```bash
# Endpoints — реальные IP Pod за Service
kubectl get endpoints miner-node -n blockchain
# NAME         ENDPOINTS                                AGE
# miner-node   10.244.1.5:8080,10.244.2.8:8080         5m

# Если ENDPOINTS пустой — проверьте selector и labels Pod
kubectl describe service miner-node -n blockchain
kubectl get pods -n blockchain --show-labels
```

### Проверка DNS

```bash
# Запустить Pod для отладки
kubectl run debug --image=busybox:1.36 -n blockchain --rm -it -- sh

# Проверить DNS
nslookup miner-node
nslookup miner-node.blockchain.svc.cluster.local

# Проверить доступность
wget -qO- http://miner-node:8080/api/node/health
```

### Проверка Ingress

```bash
# Логи Ingress Controller
kubectl logs -n ingress-nginx -l app.kubernetes.io/component=controller -f

# Конфигурация nginx (внутри controller)
kubectl exec -n ingress-nginx \
  $(kubectl get pods -n ingress-nginx -l app.kubernetes.io/component=controller -o name) \
  -- cat /etc/nginx/nginx.conf
```

---

## Частые ошибки

### 1. Service без Endpoints

```bash
kubectl get endpoints my-service
# NAME         ENDPOINTS   AGE
# my-service   <none>      5m    # ← Проблема!

# Причины:
# • Selector не совпадает с labels Pod
# • Pod не в статусе Ready
# • Pod в другом namespace

# Диагностика
kubectl describe svc my-service    # Проверить Selector
kubectl get pods --show-labels     # Проверить Labels
kubectl get pods                   # Проверить STATUS = Ready
```

### 2. Ingress не работает

```bash
# Проверки:
# 1. Ingress Controller установлен?
kubectl get pods -n ingress-nginx

# 2. Ingress применён?
kubectl get ingress -n blockchain

# 3. Service существует?
kubectl get svc -n blockchain

# 4. IngressClass правильный?
kubectl get ingressclass

# 5. Логи Controller
kubectl logs -n ingress-nginx -l app.kubernetes.io/component=controller
```

### 3. DNS не резолвится

```bash
# Проверить CoreDNS
kubectl get pods -n kube-system -l k8s-app=kube-dns

# Логи CoreDNS
kubectl logs -n kube-system -l k8s-app=kube-dns

# Проверить resolv.conf в Pod
kubectl exec my-pod -- cat /etc/resolv.conf
# nameserver 10.96.0.10
# search default.svc.cluster.local svc.cluster.local cluster.local
```

---

## Вопросы для самопроверки

### Теоретические

1. **Зачем нужен Service, если у Pod есть IP?**

   <details>
   <summary>Ответ</summary>

   Pod эфемерны — их IP меняются при пересоздании. Service предоставляет стабильный IP и DNS-имя, а также балансировку нагрузки между Pod.
   </details>

2. **Чем отличаются ClusterIP, NodePort и LoadBalancer?**

   <details>
   <summary>Ответ</summary>

   - **ClusterIP**: доступен только внутри кластера
   - **NodePort**: доступен на порту каждого Node (30000-32767)
   - **LoadBalancer**: внешний IP от облачного провайдера
   </details>

3. **Что такое Headless Service и когда его использовать?**

   <details>
   <summary>Ответ</summary>

   Headless Service (clusterIP: None) не имеет единого IP. DNS возвращает IP всех Pod напрямую. Используется для:
   - Peer-to-peer коммуникации
   - StatefulSets
   - Service discovery
   </details>

4. **Как Pod в namespace A обратиться к Service в namespace B?**

   <details>
   <summary>Ответ</summary>

   По полному DNS-имени: `service-name.namespace-b.svc.cluster.local`
   </details>

5. **Зачем нужен Ingress Controller?**

   <details>
   <summary>Ответ</summary>

   Ingress resource — это только описание правил маршрутизации. Ingress Controller — это реализация, которая читает эти правила и настраивает реальный reverse proxy (nginx, traefik и т.д.).
   </details>

### Практические

6. **Как проверить, какие Pod стоят за Service?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl get endpoints service-name
   # или
   kubectl describe service service-name
   ```
   </details>

7. **Как протестировать DNS из Pod?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl run debug --image=busybox --rm -it -- nslookup service-name
   ```
   </details>

8. **Как посмотреть логи Ingress Controller?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl logs -n ingress-nginx -l app.kubernetes.io/component=controller -f
   ```
   </details>

9. **Почему Endpoints Service пустой?**

   <details>
   <summary>Ответ</summary>

   - Selector Service не совпадает с labels Pod
   - Pod не в статусе Ready
   - Pod в другом namespace
   - Нет Pod, соответствующих selector
   </details>

10. **Как сделать Service доступным извне без LoadBalancer?**

    <details>
    <summary>Ответ</summary>

    Использовать NodePort или Ingress:
    ```yaml
    # NodePort
    spec:
      type: NodePort
      ports:
        - port: 8080
          nodePort: 30080

    # Или Ingress (предпочтительно)
    ```
    </details>

---

## Итоги урока

| Тип Service | Доступность | Использование |
|-------------|-------------|---------------|
| **ClusterIP** | Внутри кластера | Внутренние сервисы |
| **NodePort** | Node IP:30000-32767 | Разработка |
| **LoadBalancer** | Внешний IP | Production в облаке |
| **Headless** | DNS → Pod IPs | P2P, StatefulSets |

| Концепт | Описание |
|---------|----------|
| **Service** | Стабильный endpoint для Pod |
| **Endpoints** | Реальные IP Pod за Service |
| **CoreDNS** | Встроенный DNS сервер |
| **Ingress** | HTTP/HTTPS маршрутизация |
| **Ingress Controller** | Реализация Ingress (nginx, traefik) |

| DNS формат | Пример |
|------------|--------|
| Короткий | `miner-node` |
| С namespace | `miner-node.blockchain` |
| FQDN | `miner-node.blockchain.svc.cluster.local` |

В следующем уроке изучим **Configuration** — ConfigMaps и Secrets для управления конфигурацией.
