# Урок 6: Архитектура Kubernetes

## Цели урока

После этого урока вы будете:
- Понимать, зачем нужен Kubernetes и какие проблемы он решает
- Знать архитектуру кластера: Control Plane и Worker Nodes
- Понимать роль каждого компонента K8s
- Знать базовые абстракции: Pod, Node, Namespace
- Уметь работать с kubectl — главным инструментом управления

---

## Зачем нужен Kubernetes?

### Проблемы без оркестрации

Представьте, что у вас есть 10 серверов и 50 контейнеров. Что нужно делать вручную?

```
┌─────────────────────────────────────────────────────────────┐
│                    БЕЗ ОРКЕСТРАЦИИ                          │
├─────────────────────────────────────────────────────────────┤
│  • На какой сервер деплоить контейнер?                      │
│  • Что делать, если контейнер упал?                         │
│  • Как распределить нагрузку между контейнерами?            │
│  • Как обновить приложение без даунтайма?                   │
│  • Как масштабировать при росте нагрузки?                   │
│  • Как контейнеры находят друг друга?                       │
│  • Как управлять конфигурацией 50 контейнеров?              │
│  • Как откатиться при неудачном деплое?                     │
└─────────────────────────────────────────────────────────────┘
```

Можно написать скрипты. Потом больше скриптов. Потом скрипты для управления скриптами...

### Что даёт Kubernetes

**Kubernetes (K8s)** — это платформа для автоматизации развёртывания, масштабирования и управления контейнерными приложениями.

```
┌─────────────────────────────────────────────────────────────┐
│                    С KUBERNETES                             │
├─────────────────────────────────────────────────────────────┤
│  ✓ Вы описываете ЖЕЛАЕМОЕ состояние                         │
│  ✓ K8s АВТОМАТИЧЕСКИ приводит к этому состоянию             │
│  ✓ K8s ПОДДЕРЖИВАЕТ это состояние (self-healing)            │
└─────────────────────────────────────────────────────────────┘

Пример желаемого состояния:
"Я хочу 3 копии blockchain-node, каждая с 512MB RAM,
 доступные по адресу blockchain.local"

K8s автоматически:
- Выберет подходящие серверы
- Запустит контейнеры
- Настроит сеть и балансировку
- Перезапустит упавшие контейнеры
- Масштабирует при необходимости
```

### Декларативный подход

**Императивный** (как в Docker Compose): "Выполни команду X, потом Y, потом Z"

**Декларативный** (как в K8s): "Вот желаемое состояние. Разберись сам, как к нему прийти"

```yaml
# Декларативное описание: "хочу 3 копии nginx"
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx
spec:
  replicas: 3  # <- Это желаемое состояние
  ...
```

K8s постоянно сравнивает текущее состояние с желаемым и устраняет расхождения.

---

## Архитектура кластера

Кластер Kubernetes состоит из **узлов (nodes)**:
- **Control Plane** — управляющие компоненты (мозг)
- **Worker Nodes** — рабочие узлы (мускулы)

```
┌─────────────────────────────────────────────────────────────────────┐
│                      KUBERNETES CLUSTER                              │
├──────────────────────────┬──────────────────────────────────────────┤
│      CONTROL PLANE       │              WORKER NODES                 │
│      (Master Node)       │                                          │
├──────────────────────────┼──────────────────────────────────────────┤
│                          │                                          │
│  ┌──────────────────┐    │    ┌──────────────────────────────────┐  │
│  │   API Server     │◄───┼────┤           Worker Node 1          │  │
│  │   (kube-apiserver)    │    │  ┌─────────┐  ┌─────────┐        │  │
│  └────────┬─────────┘    │    │  │  Pod A  │  │  Pod B  │        │  │
│           │              │    │  └─────────┘  └─────────┘        │  │
│  ┌────────▼─────────┐    │    │       kubelet + kube-proxy       │  │
│  │    Scheduler     │    │    └──────────────────────────────────┘  │
│  │  (kube-scheduler)│    │                                          │
│  └────────┬─────────┘    │    ┌──────────────────────────────────┐  │
│           │              │    │           Worker Node 2          │  │
│  ┌────────▼─────────┐    │    │  ┌─────────┐  ┌─────────┐        │  │
│  │ Controller       │    │    │  │  Pod C  │  │  Pod D  │        │  │
│  │ Manager          │    │    │  └─────────┘  └─────────┘        │  │
│  └────────┬─────────┘    │    │       kubelet + kube-proxy       │  │
│           │              │    └──────────────────────────────────┘  │
│  ┌────────▼─────────┐    │                                          │
│  │      etcd        │    │    ┌──────────────────────────────────┐  │
│  │  (key-value DB)  │    │    │           Worker Node 3          │  │
│  └──────────────────┘    │    │  ┌─────────┐                     │  │
│                          │    │  │  Pod E  │                     │  │
└──────────────────────────┴────┴──┴─────────┴─────────────────────┘  │
                                └──────────────────────────────────────┘
```

---

## Компоненты Control Plane

### API Server (kube-apiserver)

**Единственная точка входа** для всех операций с кластером.

```
┌───────────────────────────────────────────────────────────┐
│                      API SERVER                            │
├───────────────────────────────────────────────────────────┤
│                                                           │
│   kubectl ────────┐                                       │
│                   │                                       │
│   Dashboard ──────┼──────► REST API ──────► Валидация     │
│                   │            │                          │
│   CI/CD ──────────┘            ▼                          │
│                           Авторизация                     │
│                                │                          │
│                                ▼                          │
│                         Сохранение в etcd                 │
│                                                           │
└───────────────────────────────────────────────────────────┘

Что делает:
• Принимает все запросы (kubectl, UI, API)
• Аутентифицирует и авторизует
• Валидирует запросы
• Сохраняет состояние в etcd
• Уведомляет другие компоненты об изменениях
```

**Аналогия**: API Server — это «ресепшен» в здании. Все проходят через него.

### Scheduler (kube-scheduler)

**Решает, на какой узел запустить Pod**.

```
┌───────────────────────────────────────────────────────────┐
│                      SCHEDULER                             │
├───────────────────────────────────────────────────────────┤
│                                                           │
│   Новый Pod без узла                                      │
│         │                                                 │
│         ▼                                                 │
│   ┌─────────────────┐                                     │
│   │ 1. Фильтрация   │  Какие узлы ПОДХОДЯТ?               │
│   │    (Filtering)  │  - Достаточно ресурсов?             │
│   └────────┬────────┘  - Нет taints?                      │
│            │           - Подходит по selectors?           │
│            ▼                                              │
│   ┌─────────────────┐                                     │
│   │ 2. Скоринг      │  Какой узел ЛУЧШЕ?                  │
│   │    (Scoring)    │  - Меньше нагрузка                  │
│   └────────┬────────┘  - Ближе данные                     │
│            │           - Меньше spread                    │
│            ▼                                              │
│   Назначение Pod на Node                                  │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

**Пример логики планировщика**:
```
Pod требует: 512MB RAM, 0.5 CPU

Node 1: 2GB свободно, 1 CPU свободно → ПОДХОДИТ (score: 80)
Node 2: 256MB свободно → НЕ ПОДХОДИТ
Node 3: 4GB свободно, 2 CPU свободно → ПОДХОДИТ (score: 95)

Результат: Pod → Node 3 (лучший score)
```

### Controller Manager (kube-controller-manager)

**Набор контроллеров**, каждый следит за своим типом ресурсов.

```
┌───────────────────────────────────────────────────────────┐
│                  CONTROLLER MANAGER                        │
├───────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ Replication Controller                              │  │
│  │ "Должно быть 3 Pod, сейчас 2 → создать ещё 1"      │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ Node Controller                                     │  │
│  │ "Node не отвечает 5 минут → пометить как NotReady" │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ Endpoint Controller                                 │  │
│  │ "Pod стал Ready → добавить в Service endpoints"    │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ ServiceAccount Controller                           │  │
│  │ "Новый namespace → создать default ServiceAccount" │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                           │
└───────────────────────────────────────────────────────────┘

Принцип работы (control loop):
while true:
    current_state = observe()
    desired_state = read_from_api()
    if current_state != desired_state:
        take_action_to_reconcile()
```

### etcd

**Распределённое key-value хранилище** — единственный источник правды о кластере.

```
┌───────────────────────────────────────────────────────────┐
│                         etcd                               │
├───────────────────────────────────────────────────────────┤
│                                                           │
│   Хранит ВСЁ состояние кластера:                          │
│                                                           │
│   /registry/pods/default/nginx-abc123                     │
│   /registry/services/default/my-service                   │
│   /registry/deployments/default/my-app                    │
│   /registry/configmaps/default/my-config                  │
│   /registry/secrets/default/my-secret                     │
│   ...                                                     │
│                                                           │
│   Характеристики:                                         │
│   • Консистентность (Raft consensus)                      │
│   • Высокая доступность (кластер etcd)                    │
│   • Watch механизм (уведомления об изменениях)            │
│                                                           │
└───────────────────────────────────────────────────────────┘

ВАЖНО:
• Только API Server напрямую общается с etcd
• etcd — критический компонент, нужен бэкап!
• Потеря etcd = потеря всей конфигурации кластера
```

---

## Компоненты Worker Node

### kubelet

**Агент на каждом узле**. Получает задания от API Server и выполняет их.

```
┌───────────────────────────────────────────────────────────┐
│                        kubelet                             │
├───────────────────────────────────────────────────────────┤
│                                                           │
│                  API Server                                │
│                      │                                     │
│                      ▼                                     │
│   ┌─────────────────────────────────────────────────┐     │
│   │                  kubelet                        │     │
│   │                                                 │     │
│   │  • Следит за PodSpec (желаемое состояние)      │     │
│   │  • Запускает/останавливает контейнеры          │     │
│   │  • Выполняет health checks (probes)            │     │
│   │  • Монтирует volumes                           │     │
│   │  • Отправляет статус обратно в API Server      │     │
│   │                                                 │     │
│   └──────────────────┬──────────────────────────────┘     │
│                      │                                     │
│                      ▼                                     │
│              Container Runtime                             │
│          (containerd, CRI-O, Docker)                       │
│                      │                                     │
│                      ▼                                     │
│         ┌────────┐ ┌────────┐ ┌────────┐                  │
│         │  Pod   │ │  Pod   │ │  Pod   │                  │
│         └────────┘ └────────┘ └────────┘                  │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

### kube-proxy

**Сетевой прокси на каждом узле**. Обеспечивает сетевую связность для Services.

```
┌───────────────────────────────────────────────────────────┐
│                      kube-proxy                            │
├───────────────────────────────────────────────────────────┤
│                                                           │
│   Service: my-service (ClusterIP: 10.96.0.100:80)         │
│                          │                                 │
│                          ▼                                 │
│   ┌─────────────────────────────────────────────────┐     │
│   │                 kube-proxy                      │     │
│   │                                                 │     │
│   │  Создаёт правила iptables/IPVS:                │     │
│   │                                                 │     │
│   │  10.96.0.100:80 ──► 10.244.1.5:8080 (Pod 1)   │     │
│   │                 ──► 10.244.2.3:8080 (Pod 2)   │     │
│   │                 ──► 10.244.3.7:8080 (Pod 3)   │     │
│   │                                                 │     │
│   │  Режимы работы:                                │     │
│   │  • iptables (по умолчанию)                     │     │
│   │  • IPVS (для больших кластеров)                │     │
│   │  • userspace (устаревший)                      │     │
│   │                                                 │     │
│   └─────────────────────────────────────────────────┘     │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

### Container Runtime

**Среда выполнения контейнеров**. K8s не запускает контейнеры напрямую — делегирует это runtime.

```
┌───────────────────────────────────────────────────────────┐
│                   CONTAINER RUNTIME                        │
├───────────────────────────────────────────────────────────┤
│                                                           │
│   kubelet ◄────► CRI (Container Runtime Interface)        │
│                          │                                 │
│            ┌─────────────┼─────────────┐                  │
│            │             │             │                  │
│            ▼             ▼             ▼                  │
│      containerd       CRI-O        Docker*                │
│      (default)     (OpenShift)   (deprecated)             │
│                                                           │
│   *Docker поддержка удалена в K8s 1.24+                   │
│    (но образы Docker работают везде)                      │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

---

## Основные абстракции

### Pod — минимальная единица деплоя

**Pod** — это группа контейнеров, которые:
- Запускаются вместе на одном узле
- Делят сеть (один IP) и storage
- Планируются и масштабируются как единое целое

```
┌───────────────────────────────────────────────────────────┐
│                          POD                               │
│                    IP: 10.244.1.5                          │
├───────────────────────────────────────────────────────────┤
│                                                           │
│   ┌─────────────────┐    ┌─────────────────┐              │
│   │   Container 1   │    │   Container 2   │              │
│   │  (main app)     │    │  (sidecar)      │              │
│   │                 │    │                 │              │
│   │  localhost:8080 │◄──►│  localhost:9090 │              │
│   │                 │    │                 │              │
│   └────────┬────────┘    └────────┬────────┘              │
│            │                      │                        │
│            └──────────┬───────────┘                        │
│                       │                                    │
│              ┌────────▼────────┐                           │
│              │  Shared Volume  │                           │
│              │   /data         │                           │
│              └─────────────────┘                           │
│                                                           │
└───────────────────────────────────────────────────────────┘

Типичные паттерны:
• Один контейнер на Pod (90% случаев)
• Sidecar: main + logging/monitoring
• Ambassador: main + proxy
• Adapter: main + format converter
```

**Почему Pod, а не просто контейнер?**

```yaml
# Пример: blockchain node + metrics exporter
apiVersion: v1
kind: Pod
metadata:
  name: blockchain-node
spec:
  containers:
    - name: node
      image: blockchain-node:latest
      ports:
        - containerPort: 8080

    - name: metrics-exporter
      image: prom/node-exporter:latest
      ports:
        - containerPort: 9100
      # Контейнеры в Pod делят localhost!
      # metrics-exporter видит node на localhost:8080
```

### Node — рабочая машина

**Node** — физический или виртуальный сервер в кластере.

```bash
# Посмотреть узлы кластера
$ kubectl get nodes
NAME                 STATUS   ROLES           AGE   VERSION
control-plane        Ready    control-plane   10d   v1.28.0
worker-1             Ready    <none>          10d   v1.28.0
worker-2             Ready    <none>          10d   v1.28.0

# Детальная информация об узле
$ kubectl describe node worker-1
Name:               worker-1
Roles:              <none>
Labels:             kubernetes.io/arch=amd64
                    kubernetes.io/os=linux
                    node.kubernetes.io/instance-type=m5.large
Capacity:
  cpu:              2
  memory:           8Gi
  pods:             110
Allocatable:
  cpu:              1900m
  memory:           7Gi
  pods:             110
Conditions:
  Ready:            True
  MemoryPressure:   False
  DiskPressure:     False
```

### Namespace — логическая изоляция

**Namespace** — виртуальный кластер внутри кластера.

```
┌───────────────────────────────────────────────────────────┐
│                    KUBERNETES CLUSTER                      │
├───────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────────┐  ┌─────────────────┐                 │
│  │ namespace:      │  │ namespace:      │                 │
│  │ default         │  │ kube-system     │                 │
│  │                 │  │                 │                 │
│  │ • user pods     │  │ • coredns       │                 │
│  │ • user services │  │ • kube-proxy    │                 │
│  │                 │  │ • metrics-server│                 │
│  └─────────────────┘  └─────────────────┘                 │
│                                                           │
│  ┌─────────────────┐  ┌─────────────────┐                 │
│  │ namespace:      │  │ namespace:      │                 │
│  │ blockchain      │  │ monitoring      │                 │
│  │                 │  │                 │                 │
│  │ • miner-node    │  │ • prometheus    │                 │
│  │ • wallet-node   │  │ • grafana       │                 │
│  │ • full-node     │  │ • alertmanager  │                 │
│  └─────────────────┘  └─────────────────┘                 │
│                                                           │
└───────────────────────────────────────────────────────────┘

Зачем namespaces?
• Разделение окружений (dev, staging, prod)
• Изоляция команд/проектов
• Квоты ресурсов на namespace
• RBAC (права доступа) на namespace
```

```bash
# Стандартные namespaces
$ kubectl get namespaces
NAME              STATUS   AGE
default           Active   10d   # Для пользователей (по умолчанию)
kube-system       Active   10d   # Системные компоненты K8s
kube-public       Active   10d   # Публичные ресурсы (обычно пуст)
kube-node-lease   Active   10d   # Node heartbeats

# Создать namespace
$ kubectl create namespace blockchain

# Работать в namespace
$ kubectl get pods -n blockchain
$ kubectl get pods --namespace blockchain

# Или установить namespace по умолчанию
$ kubectl config set-context --current --namespace=blockchain
```

---

## kubectl — командная строка K8s

### Установка

```bash
# macOS
brew install kubectl

# Linux
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# Windows (PowerShell)
choco install kubernetes-cli
# или
winget install Kubernetes.kubectl

# Проверка
kubectl version --client
```

### Структура команд

```
kubectl [command] [TYPE] [NAME] [flags]

command: get, describe, create, apply, delete, logs, exec...
TYPE:    pod, deployment, service, configmap, secret...
NAME:    имя ресурса (опционально)
flags:   -n namespace, -o output, --all-namespaces...
```

### Основные команды

```bash
# ═══════════════════════════════════════════════════════════
# ПРОСМОТР РЕСУРСОВ
# ═══════════════════════════════════════════════════════════

# Список подов
kubectl get pods                    # В текущем namespace
kubectl get pods -n kube-system     # В конкретном namespace
kubectl get pods -A                 # Во всех namespaces
kubectl get pods -o wide            # С дополнительной информацией
kubectl get pods -o yaml            # В YAML формате

# Детальная информация
kubectl describe pod nginx-abc123

# Все ресурсы
kubectl get all                     # pods, services, deployments...
kubectl api-resources               # Все доступные типы ресурсов


# ═══════════════════════════════════════════════════════════
# СОЗДАНИЕ И УДАЛЕНИЕ
# ═══════════════════════════════════════════════════════════

# Создать из YAML файла
kubectl apply -f pod.yaml           # Создать или обновить
kubectl create -f pod.yaml          # Только создать (ошибка если есть)

# Создать из нескольких файлов
kubectl apply -f ./k8s/             # Все файлы в директории
kubectl apply -f file1.yaml -f file2.yaml

# Удалить
kubectl delete pod nginx
kubectl delete -f pod.yaml
kubectl delete pods --all           # Все поды в namespace


# ═══════════════════════════════════════════════════════════
# ОТЛАДКА
# ═══════════════════════════════════════════════════════════

# Логи
kubectl logs nginx-abc123           # Логи контейнера
kubectl logs nginx-abc123 -f        # Следить (tail -f)
kubectl logs nginx-abc123 --previous  # Логи предыдущего контейнера
kubectl logs nginx-abc123 -c sidecar  # Логи конкретного контейнера

# Выполнить команду в контейнере
kubectl exec nginx-abc123 -- ls /app
kubectl exec -it nginx-abc123 -- /bin/sh  # Интерактивный shell

# Port forwarding (для отладки)
kubectl port-forward pod/nginx-abc123 8080:80
# Теперь localhost:8080 → pod:80


# ═══════════════════════════════════════════════════════════
# ПОЛЕЗНЫЕ ФЛАГИ
# ═══════════════════════════════════════════════════════════

-n, --namespace     # Указать namespace
-A, --all-namespaces  # Все namespaces
-o wide             # Расширенный вывод
-o yaml             # YAML формат
-o json             # JSON формат
-o name             # Только имена
--watch, -w         # Следить за изменениями
--dry-run=client    # Проверить без выполнения
```

### kubeconfig

**kubeconfig** — файл конфигурации для подключения к кластерам.

```bash
# По умолчанию: ~/.kube/config
# Можно переопределить: export KUBECONFIG=/path/to/config

# Посмотреть текущий контекст
kubectl config current-context

# Список контекстов (кластеров)
kubectl config get-contexts

# Переключить контекст
kubectl config use-context my-cluster

# Структура kubeconfig
cat ~/.kube/config
```

```yaml
# ~/.kube/config
apiVersion: v1
kind: Config
current-context: my-cluster

# Кластеры — куда подключаться
clusters:
  - name: my-cluster
    cluster:
      server: https://192.168.1.100:6443
      certificate-authority: /path/to/ca.crt

# Пользователи — как аутентифицироваться
users:
  - name: admin
    user:
      client-certificate: /path/to/admin.crt
      client-key: /path/to/admin.key

# Контексты — комбинация кластер + пользователь + namespace
contexts:
  - name: my-cluster
    context:
      cluster: my-cluster
      user: admin
      namespace: default
```

---

## Kind — локальный Kubernetes

**Kind (Kubernetes IN Docker)** — запускает K8s кластер в Docker контейнерах.

### Установка

```bash
# macOS
brew install kind

# Linux
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.20.0/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind

# Windows
choco install kind

# Проверка
kind version
```

### Основные команды

```bash
# Создать кластер (1 узел)
kind create cluster

# Создать кластер с именем
kind create cluster --name blockchain-cluster

# Создать из конфигурации
kind create cluster --config kind-config.yaml

# Список кластеров
kind get clusters

# Удалить кластер
kind delete cluster
kind delete cluster --name blockchain-cluster

# Загрузить Docker образ в кластер
# (для локальных образов, не из registry)
kind load docker-image blockchain-node:latest
```

### Конфигурация Kind

```yaml
# k8s/kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: blockchain-cluster

nodes:
  # Control plane с port mapping для Ingress
  - role: control-plane
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"

  # Worker узлы (опционально)
  - role: worker
  - role: worker
```

```bash
# Создать кластер с конфигурацией
kind create cluster --config k8s/kind-config.yaml

# Проверить
kubectl cluster-info
kubectl get nodes
```

---

## Как всё работает вместе

Разберём жизненный цикл создания Pod:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ЖИЗНЕННЫЙ ЦИКЛ POD                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. kubectl apply -f pod.yaml                                       │
│         │                                                           │
│         ▼                                                           │
│  2. API Server                                                      │
│     • Аутентификация (кто?)                                         │
│     • Авторизация (можно?)                                          │
│     • Admission control (валидация)                                 │
│     • Сохранение в etcd                                             │
│         │                                                           │
│         ▼                                                           │
│  3. Scheduler (watch на новые поды без узла)                        │
│     • Filtering: какие узлы подходят?                               │
│     • Scoring: какой узел лучший?                                   │
│     • Назначение: Pod → Node X                                      │
│     • Обновление в etcd через API Server                            │
│         │                                                           │
│         ▼                                                           │
│  4. kubelet на Node X (watch на поды для своего узла)               │
│     • Скачивание образа (если нет)                                  │
│     • Создание контейнеров через Container Runtime                  │
│     • Запуск Probes                                                 │
│     • Отправка статуса в API Server                                 │
│         │                                                           │
│         ▼                                                           │
│  5. Pod Running!                                                    │
│     • kube-proxy обновляет iptables                                 │
│     • Endpoint Controller добавляет в Service                       │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Практика: первый запуск

### Шаг 1: Создаём Kind кластер

```bash
# Создать кластер
kind create cluster --name blockchain-cluster

# Проверить подключение
kubectl cluster-info
kubectl get nodes

# Ожидаемый вывод:
# NAME                                STATUS   ROLES           AGE   VERSION
# blockchain-cluster-control-plane   Ready    control-plane   1m    v1.28.0
```

### Шаг 2: Запускаем первый Pod

```bash
# Создаём Pod императивно (для теста)
kubectl run nginx --image=nginx:alpine

# Смотрим статус
kubectl get pods
kubectl get pods -o wide

# Детали
kubectl describe pod nginx

# Логи
kubectl logs nginx

# Удаляем
kubectl delete pod nginx
```

### Шаг 3: Создаём Pod декларативно

```yaml
# test-pod.yaml
apiVersion: v1
kind: Pod
metadata:
  name: nginx-test
  labels:
    app: nginx
spec:
  containers:
    - name: nginx
      image: nginx:alpine
      ports:
        - containerPort: 80
```

```bash
# Применяем
kubectl apply -f test-pod.yaml

# Проверяем
kubectl get pods
kubectl describe pod nginx-test

# Пробуем port-forward
kubectl port-forward pod/nginx-test 8080:80 &

# В другом терминале или браузере
curl http://localhost:8080

# Убираем
kubectl delete -f test-pod.yaml
```

### Шаг 4: Исследуем системные компоненты

```bash
# Системные поды
kubectl get pods -n kube-system

# Ожидаемый вывод (примерно):
# NAME                                                   READY   STATUS
# coredns-5d78c9869d-xxxxx                              1/1     Running
# coredns-5d78c9869d-yyyyy                              1/1     Running
# etcd-blockchain-cluster-control-plane                 1/1     Running
# kindnet-xxxxx                                         1/1     Running
# kube-apiserver-blockchain-cluster-control-plane       1/1     Running
# kube-controller-manager-blockchain-cluster-...        1/1     Running
# kube-proxy-xxxxx                                      1/1     Running
# kube-scheduler-blockchain-cluster-control-plane       1/1     Running
```

---

## Частые ошибки и решения

### 1. Pod в статусе Pending

```bash
$ kubectl get pods
NAME    READY   STATUS    RESTARTS   AGE
nginx   0/1     Pending   0          5m

# Диагностика
$ kubectl describe pod nginx
Events:
  Warning  FailedScheduling  default-scheduler
           0/1 nodes are available: 1 Insufficient cpu.

# Причины:
# • Не хватает ресурсов на узлах
# • Нет узлов с нужными labels
# • Taints не позволяют планирование
```

### 2. Pod в статусе ImagePullBackOff

```bash
$ kubectl get pods
NAME    READY   STATUS             RESTARTS   AGE
nginx   0/1     ImagePullBackOff   0          2m

# Причины:
# • Неверное имя образа
# • Образ не существует в registry
# • Нет доступа к private registry

# Диагностика
$ kubectl describe pod nginx
Events:
  Warning  Failed   kubelet  Failed to pull image "nginx:nonexistent"
```

### 3. Pod в статусе CrashLoopBackOff

```bash
$ kubectl get pods
NAME    READY   STATUS             RESTARTS   AGE
app     0/1     CrashLoopBackOff   5          10m

# Контейнер падает и перезапускается

# Диагностика
$ kubectl logs app
$ kubectl logs app --previous  # Логи предыдущего контейнера

# Причины:
# • Ошибка в приложении
# • Неверная конфигурация
# • Не пройден health check
```

### 4. Не могу подключиться к API Server

```bash
$ kubectl get pods
The connection to the server localhost:8080 was refused

# Причины:
# • Kind кластер не запущен
# • Неверный kubeconfig
# • API Server недоступен

# Решение
kind get clusters           # Есть ли кластер?
docker ps                   # Запущен ли контейнер Kind?
export KUBECONFIG=~/.kube/config
```

---

## Вопросы для самопроверки

### Теоретические

1. **Что такое декларативный подход в Kubernetes?**

   <details>
   <summary>Ответ</summary>

   Вы описываете желаемое состояние (например, "хочу 3 реплики"), а K8s сам определяет, какие действия нужны для достижения этого состояния. В отличие от императивного подхода, где вы указываете конкретные команды ("создай pod", "удали pod").
   </details>

2. **Какие компоненты входят в Control Plane и за что отвечают?**

   <details>
   <summary>Ответ</summary>

   - **API Server** — единая точка входа, REST API, аутентификация/авторизация
   - **Scheduler** — выбирает узел для нового Pod
   - **Controller Manager** — набор контроллеров, поддерживающих желаемое состояние
   - **etcd** — распределённое хранилище состояния кластера
   </details>

3. **Чем Pod отличается от контейнера?**

   <details>
   <summary>Ответ</summary>

   Pod — это группа из одного или нескольких контейнеров, которые:
   - Запускаются на одном узле
   - Делят сеть (один IP, localhost)
   - Могут делить storage (volumes)
   - Являются минимальной единицей планирования в K8s
   </details>

4. **Зачем нужны Namespaces?**

   <details>
   <summary>Ответ</summary>

   - Логическая изоляция ресурсов
   - Разделение окружений (dev, staging, prod)
   - Разграничение доступа (RBAC)
   - Квоты ресурсов
   </details>

5. **Какую роль играет kubelet?**

   <details>
   <summary>Ответ</summary>

   kubelet — агент на каждом узле, который:
   - Получает PodSpec от API Server
   - Запускает контейнеры через Container Runtime
   - Выполняет health checks (probes)
   - Монтирует volumes
   - Отправляет статус обратно в API Server
   </details>

### Практические

6. **Как посмотреть все поды во всех namespaces?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl get pods -A
   # или
   kubectl get pods --all-namespaces
   ```
   </details>

7. **Как получить логи предыдущего (упавшего) контейнера?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl logs pod-name --previous
   ```
   </details>

8. **Как узнать, почему Pod не может запуститься?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl describe pod pod-name
   # Смотреть секцию Events в конце
   ```
   </details>

9. **Как переключиться на другой namespace по умолчанию?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl config set-context --current --namespace=my-namespace
   ```
   </details>

10. **Как загрузить локальный Docker образ в Kind кластер?**

    <details>
    <summary>Ответ</summary>

    ```bash
    # Образы из локального Docker не видны Kind'у автоматически
    kind load docker-image my-image:tag --name cluster-name
    ```
    </details>

---

## Итоги урока

| Компонент | Роль |
|-----------|------|
| **API Server** | Единая точка входа, REST API |
| **Scheduler** | Выбирает узел для Pod |
| **Controller Manager** | Поддерживает желаемое состояние |
| **etcd** | Хранилище состояния |
| **kubelet** | Агент на узле, запускает контейнеры |
| **kube-proxy** | Сетевая связность Services |

| Абстракция | Назначение |
|------------|------------|
| **Pod** | Минимальная единица деплоя |
| **Node** | Рабочая машина |
| **Namespace** | Логическая изоляция |

| Инструмент | Назначение |
|------------|------------|
| **kubectl** | CLI для управления кластером |
| **Kind** | Локальный K8s в Docker |

В следующем уроке мы изучим **Workloads** — Deployments, ReplicaSets, и как управлять жизненным циклом приложений.
