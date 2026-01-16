# Урок 14: Troubleshooting — Диагностика проблем в Kubernetes

## Цели урока

После этого урока вы будете:
- Уметь диагностировать проблемы с Pod'ами
- Знать основные команды для отладки
- Понимать, как читать Events и логи
- Уметь использовать kubectl debug
- Знать типичные проблемы и их решения

---

## Методология отладки

### Общий подход

```
┌─────────────────────────────────────────────────────────────┐
│              АЛГОРИТМ ОТЛАДКИ                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   1. Определить проблему                                    │
│      • Что не работает?                                    │
│      • Какой компонент затронут?                           │
│                                                             │
│   2. Собрать информацию                                     │
│      • kubectl get — общий статус                          │
│      • kubectl describe — детали и события                 │
│      • kubectl logs — логи приложения                      │
│                                                             │
│   3. Сузить область поиска                                  │
│      • Pod, Service, Ingress?                              │
│      • Конфигурация, сеть, ресурсы?                        │
│                                                             │
│   4. Проверить гипотезу                                     │
│      • Внести изменение                                    │
│      • Проверить результат                                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Уровни проблем

```
┌─────────────────────────────────────────────────────────────┐
│              УРОВНИ ПРОБЛЕМ                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Кластер                                                   │
│   └── Node                                                  │
│       └── Pod                                               │
│           └── Container                                     │
│               └── Приложение                               │
│                                                             │
│   Проблема может быть на любом уровне:                      │
│   • Кластер: API Server недоступен, нет ресурсов           │
│   • Node: disk pressure, memory pressure                    │
│   • Pod: не может запуститься, Pending                     │
│   • Container: CrashLoopBackOff, ImagePullBackOff          │
│   • Приложение: ошибки в коде, неверная конфигурация       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Основные команды отладки

### kubectl get — обзор состояния

```bash
# ═══════════════════════════════════════════════════════════
# СТАТУС РЕСУРСОВ
# ═══════════════════════════════════════════════════════════

# Pod'ы
kubectl get pods                    # В текущем namespace
kubectl get pods -n blockchain      # В конкретном namespace
kubectl get pods -A                 # Во всех namespaces
kubectl get pods -o wide            # С дополнительной информацией
kubectl get pods -w                 # Watch mode (следить)

# Статусы Pod:
# Pending      — ждёт планирования или создания контейнера
# Running      — все контейнеры запущены
# Succeeded    — все контейнеры успешно завершились
# Failed       — все контейнеры остановлены, хотя бы один с ошибкой
# Unknown      — состояние неизвестно (потеря связи с Node)

# Все ресурсы
kubectl get all -n blockchain

# С labels
kubectl get pods -l app=miner-node
kubectl get pods --show-labels
```

### kubectl describe — детальная информация

```bash
# ═══════════════════════════════════════════════════════════
# ДЕТАЛИ РЕСУРСОВ
# ═══════════════════════════════════════════════════════════

# Pod (самая важная команда для отладки!)
kubectl describe pod miner-node-xxxxx -n blockchain

# Что смотреть в выводе:
# 1. Status/Phase — текущее состояние
# 2. Conditions — подробные условия
# 3. Containers — статус каждого контейнера
# 4. Events — что происходило (в конце, самое важное!)

# Другие ресурсы
kubectl describe node worker-1
kubectl describe service miner-node -n blockchain
kubectl describe deployment miner-node -n blockchain
kubectl describe ingress blockchain-ingress -n blockchain
kubectl describe pvc data-blockchain-0 -n blockchain
```

### kubectl logs — логи приложения

```bash
# ═══════════════════════════════════════════════════════════
# ЛОГИ
# ═══════════════════════════════════════════════════════════

# Логи Pod
kubectl logs miner-node-xxxxx -n blockchain

# Follow (tail -f)
kubectl logs -f miner-node-xxxxx -n blockchain

# Последние N строк
kubectl logs --tail=100 miner-node-xxxxx -n blockchain

# С timestamps
kubectl logs --timestamps miner-node-xxxxx -n blockchain

# Логи предыдущего контейнера (после crash)
kubectl logs miner-node-xxxxx -n blockchain --previous

# Если несколько контейнеров в Pod
kubectl logs miner-node-xxxxx -n blockchain -c container-name

# Все контейнеры
kubectl logs miner-node-xxxxx -n blockchain --all-containers

# Логи по label selector
kubectl logs -l app=miner-node -n blockchain

# Логи всех Pod deployment
kubectl logs deployment/miner-node -n blockchain
```

### kubectl exec — выполнение команд

```bash
# ═══════════════════════════════════════════════════════════
# ВЫПОЛНЕНИЕ КОМАНД В КОНТЕЙНЕРЕ
# ═══════════════════════════════════════════════════════════

# Одна команда
kubectl exec miner-node-xxxxx -n blockchain -- env
kubectl exec miner-node-xxxxx -n blockchain -- cat /app/config.yaml

# Интерактивный shell
kubectl exec -it miner-node-xxxxx -n blockchain -- /bin/sh
kubectl exec -it miner-node-xxxxx -n blockchain -- /bin/bash

# В конкретный контейнер
kubectl exec -it miner-node-xxxxx -n blockchain -c sidecar -- /bin/sh

# Типичные проверки внутри контейнера:
# env                           # Переменные окружения
# cat /etc/resolv.conf          # DNS настройки
# wget -qO- http://service:8080 # Проверка сети
# df -h                         # Дисковое пространство
# ps aux                        # Процессы
```

### kubectl events — события

```bash
# ═══════════════════════════════════════════════════════════
# СОБЫТИЯ КЛАСТЕРА
# ═══════════════════════════════════════════════════════════

# События в namespace
kubectl get events -n blockchain

# Сортировка по времени
kubectl get events -n blockchain --sort-by='.lastTimestamp'

# Watch mode
kubectl get events -n blockchain -w

# Только Warning
kubectl get events -n blockchain --field-selector type=Warning

# События для конкретного Pod
kubectl get events -n blockchain --field-selector involvedObject.name=miner-node-xxxxx
```

---

## Типичные проблемы и решения

### 1. Pod в статусе Pending

```bash
kubectl get pods
# NAME              READY   STATUS    RESTARTS   AGE
# miner-node-xxx    0/1     Pending   0          5m

kubectl describe pod miner-node-xxx
# Events:
# Warning  FailedScheduling  default-scheduler  0/3 nodes available: ...
```

```
┌─────────────────────────────────────────────────────────────┐
│              PENDING — ПРИЧИНЫ                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  "0/X nodes are available: X Insufficient cpu/memory"       │
│  → Не хватает ресурсов. Уменьшить requests или добавить ноды│
│                                                             │
│  "0/X nodes are available: X node(s) had taint..."         │
│  → Taints не позволяют. Добавить tolerations или убрать taint│
│                                                             │
│  "0/X nodes are available: X node(s) didn't match selector"│
│  → nodeSelector не совпадает. Проверить labels нод          │
│                                                             │
│  "persistentvolumeclaim not found"                          │
│  → PVC не существует. Создать PVC                           │
│                                                             │
│  "pod has unbound immediate PersistentVolumeClaims"        │
│  → PVC не может найти PV. Проверить StorageClass           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2. Pod в статусе ImagePullBackOff

```bash
kubectl get pods
# NAME              READY   STATUS             RESTARTS   AGE
# miner-node-xxx    0/1     ImagePullBackOff   0          5m

kubectl describe pod miner-node-xxx
# Events:
# Warning  Failed  kubelet  Failed to pull image "blockchain-node:v999"
```

```
┌─────────────────────────────────────────────────────────────┐
│              IMAGEPULLBACKOFF — ПРИЧИНЫ                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  "repository does not exist or may require authorization"   │
│  → Неверное имя образа или нет доступа к registry          │
│                                                             │
│  "manifest unknown"                                         │
│  → Неверный tag образа                                     │
│                                                             │
│  "unauthorized: authentication required"                    │
│  → Нужен imagePullSecret                                   │
│                                                             │
│  Решения:                                                   │
│  • Проверить имя и tag образа                              │
│  • Для Kind: kind load docker-image image:tag              │
│  • Создать imagePullSecret для private registry            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 3. Pod в статусе CrashLoopBackOff

```bash
kubectl get pods
# NAME              READY   STATUS             RESTARTS   AGE
# miner-node-xxx    0/1     CrashLoopBackOff   5          5m

kubectl describe pod miner-node-xxx
# Last State: Terminated
#   Reason: Error
#   Exit Code: 1
```

```
┌─────────────────────────────────────────────────────────────┐
│              CRASHLOOPBACKOFF — ПРИЧИНЫ                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Контейнер постоянно падает и перезапускается               │
│                                                             │
│  Диагностика:                                               │
│  kubectl logs miner-node-xxx --previous                    │
│                                                             │
│  Частые причины:                                            │
│  • Ошибка в приложении (исключение при старте)             │
│  • Неверная конфигурация (переменные окружения)            │
│  • Отсутствует зависимость (БД недоступна)                 │
│  • Liveness probe fail до готовности                       │
│  • Неверная команда/entrypoint                             │
│  • OOMKilled (не хватает памяти)                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 4. Pod в статусе OOMKilled

```bash
kubectl describe pod miner-node-xxx
# Last State: Terminated
#   Reason: OOMKilled
#   Exit Code: 137
```

```
┌─────────────────────────────────────────────────────────────┐
│              OOMKILLED — РЕШЕНИЯ                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Приложение использовало больше памяти чем limit            │
│                                                             │
│  Решения:                                                   │
│  1. Увеличить limits.memory                                │
│  2. Оптимизировать приложение                              │
│  3. Для Java: настроить -XX:MaxRAMPercentage=75.0          │
│  4. Проверить утечки памяти                                │
│                                                             │
│  Мониторинг памяти:                                         │
│  kubectl top pod miner-node-xxx                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 5. Service не работает

```bash
# Pod'ы Running, но Service не отвечает
curl http://miner-node:8080
# connection refused или timeout
```

```
┌─────────────────────────────────────────────────────────────┐
│              SERVICE НЕ РАБОТАЕТ                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Диагностика:                                               │
│                                                             │
│  1. Проверить Endpoints                                     │
│     kubectl get endpoints miner-node -n blockchain         │
│     # Должны быть IP адреса Pod'ов                         │
│     # Если пусто — проблема с selector                     │
│                                                             │
│  2. Проверить selector Service                              │
│     kubectl describe service miner-node -n blockchain      │
│     # Selector: app.kubernetes.io/name=miner-node          │
│                                                             │
│  3. Проверить labels Pod                                    │
│     kubectl get pods --show-labels -n blockchain           │
│     # Labels должны совпадать с selector                   │
│                                                             │
│  4. Проверить что Pod Ready                                 │
│     kubectl get pods -n blockchain                         │
│     # READY должен быть 1/1                                │
│     # Если 0/1 — readinessProbe fail                       │
│                                                             │
│  5. Проверить порты                                         │
│     kubectl describe service miner-node -n blockchain      │
│     # Port и TargetPort должны быть правильными            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 6. Ingress не работает

```bash
curl http://blockchain.localhost
# 404 Not Found или 502 Bad Gateway
```

```
┌─────────────────────────────────────────────────────────────┐
│              INGRESS НЕ РАБОТАЕТ                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Диагностика:                                               │
│                                                             │
│  1. Ingress Controller запущен?                             │
│     kubectl get pods -n ingress-nginx                      │
│                                                             │
│  2. Ingress создан?                                         │
│     kubectl get ingress -n blockchain                      │
│     # ADDRESS должен быть заполнен                         │
│                                                             │
│  3. Логи Ingress Controller                                 │
│     kubectl logs -n ingress-nginx \                        │
│       -l app.kubernetes.io/component=controller            │
│                                                             │
│  4. Service существует и работает?                          │
│     kubectl get svc -n blockchain                          │
│     kubectl get endpoints -n blockchain                    │
│                                                             │
│  5. /etc/hosts настроен? (для локальной разработки)        │
│                                                             │
│  6. IngressClass правильный?                                │
│     kubectl get ingressclass                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## kubectl debug

### Debug контейнер (K8s 1.25+)

```bash
# Запустить debug контейнер рядом с существующим Pod
kubectl debug miner-node-xxx -n blockchain -it --image=busybox

# Debug с копией Pod (не влияет на оригинал)
kubectl debug miner-node-xxx -n blockchain -it --image=busybox --copy-to=debug-pod

# Debug на уровне Node
kubectl debug node/worker-1 -it --image=busybox
```

### Эфемерные контейнеры

```bash
# Добавить debug контейнер к работающему Pod
kubectl debug -it miner-node-xxx -n blockchain \
  --image=busybox \
  --target=blockchain-node  # Share process namespace

# Внутри можно:
# - Видеть процессы основного контейнера
# - Использовать debug инструменты
```

---

## Сетевая отладка

### DNS

```bash
# Запустить debug Pod
kubectl run debug --image=busybox -n blockchain --rm -it -- sh

# Внутри:
nslookup miner-node
nslookup miner-node.blockchain.svc.cluster.local

# Проверить resolv.conf
cat /etc/resolv.conf

# Проверить CoreDNS
kubectl get pods -n kube-system -l k8s-app=kube-dns
kubectl logs -n kube-system -l k8s-app=kube-dns
```

### Connectivity

```bash
# Из debug Pod:
wget -qO- http://miner-node:8080/api/node/health
wget -qO- --timeout=5 http://external-service:8080

# Проверить порт
nc -zv miner-node 8080

# Traceroute
traceroute miner-node
```

### Network Policies

```bash
# Есть ли NetworkPolicy?
kubectl get networkpolicy -n blockchain

# Может блокировать трафик
kubectl describe networkpolicy -n blockchain
```

---

## Мониторинг ресурсов

### kubectl top

```bash
# Требует metrics-server

# Ресурсы Pod
kubectl top pods -n blockchain
# NAME              CPU(cores)   MEMORY(bytes)
# miner-node-xxx    250m         350Mi

# Ресурсы Node
kubectl top nodes
# NAME       CPU(cores)   CPU%   MEMORY(bytes)   MEMORY%
# worker-1   500m         25%    2Gi             50%

# Сортировка
kubectl top pods -n blockchain --sort-by=cpu
kubectl top pods -n blockchain --sort-by=memory
```

### Установка metrics-server для Kind

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Для Kind нужна дополнительная настройка
kubectl patch deployment metrics-server -n kube-system \
  --type='json' \
  -p='[{"op": "add", "path": "/spec/template/spec/containers/0/args/-", "value": "--kubelet-insecure-tls"}]'
```

---

## Полезные инструменты

### stern — улучшенные логи

```bash
# Установка
brew install stern

# Логи всех Pod по паттерну
stern miner -n blockchain

# С timestamps
stern miner -n blockchain -t

# Только определённые контейнеры
stern miner -n blockchain -c blockchain-node
```

### k9s — терминальный UI

```bash
# Установка
brew install k9s

# Запуск
k9s
k9s -n blockchain

# Горячие клавиши:
# : — командная строка
# / — поиск
# d — describe
# l — logs
# s — shell
# ctrl-d — delete
```

### kubectx/kubens — переключение контекста

```bash
# Установка
brew install kubectx

# Переключить контекст (кластер)
kubectx my-cluster

# Переключить namespace
kubens blockchain
```

---

## Чеклист отладки

```
┌─────────────────────────────────────────────────────────────┐
│              ЧЕКЛИСТ ОТЛАДКИ                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  □ Pod                                                      │
│    □ kubectl get pods — статус                             │
│    □ kubectl describe pod — события                        │
│    □ kubectl logs — логи приложения                        │
│    □ kubectl logs --previous — логи после crash            │
│                                                             │
│  □ Service                                                  │
│    □ kubectl get endpoints — есть ли IP?                   │
│    □ kubectl describe service — selector                   │
│    □ Labels Pod совпадают с selector?                      │
│                                                             │
│  □ Ingress                                                  │
│    □ Ingress Controller запущен?                           │
│    □ kubectl get ingress — ADDRESS заполнен?               │
│    □ Логи Ingress Controller                               │
│                                                             │
│  □ Конфигурация                                             │
│    □ ConfigMap/Secret существуют?                          │
│    □ Переменные окружения правильные?                      │
│    □ Volume mounts правильные?                             │
│                                                             │
│  □ Ресурсы                                                  │
│    □ kubectl top — использование CPU/Memory                │
│    □ Limits достаточные?                                   │
│    □ Node не в pressure?                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Вопросы для самопроверки

1. **Как посмотреть логи предыдущего (упавшего) контейнера?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl logs pod-name --previous
   ```
   </details>

2. **Pod в Pending. Как узнать причину?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl describe pod pod-name
   # Смотреть секцию Events в конце вывода
   ```
   </details>

3. **Endpoints Service пустые. Что проверить?**

   <details>
   <summary>Ответ</summary>

   - Selector Service совпадает с labels Pod?
   - Pod в статусе Ready?
   - Pod в том же namespace?
   </details>

4. **Как проверить DNS из Pod?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl run debug --image=busybox --rm -it -- nslookup service-name
   ```
   </details>

5. **Как получить shell в работающем контейнере?**

   <details>
   <summary>Ответ</summary>

   ```bash
   kubectl exec -it pod-name -- /bin/sh
   ```
   </details>

---

## Итоги урока

| Команда | Назначение |
|---------|------------|
| `kubectl get` | Обзор состояния |
| `kubectl describe` | Детали и события |
| `kubectl logs` | Логи приложения |
| `kubectl exec` | Выполнение команд |
| `kubectl events` | События кластера |
| `kubectl top` | Использование ресурсов |
| `kubectl debug` | Debug контейнеры |

| Статус Pod | Значение |
|------------|----------|
| **Pending** | Ждёт планирования |
| **Running** | Работает |
| **CrashLoopBackOff** | Падает и перезапускается |
| **ImagePullBackOff** | Не может скачать образ |
| **OOMKilled** | Убит из-за нехватки памяти |

В следующем уроке — **Финал**: создание полного Helm chart для blockchain-сети.
