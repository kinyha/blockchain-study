# Урок 13: Helm — Пакетный менеджер для Kubernetes

## Цели урока

После этого урока вы будете:
- Понимать, зачем нужен Helm
- Знать структуру Helm chart
- Уметь использовать шаблоны и values
- Уметь устанавливать и обновлять релизы
- Знать основные команды Helm

---

## Зачем нужен Helm?

### Проблема: много YAML файлов

```
┌─────────────────────────────────────────────────────────────┐
│              БЕЗ HELM                                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Для деплоя blockchain нужно:                              │
│   • namespace.yaml                                          │
│   • configmap.yaml                                          │
│   • secret.yaml                                             │
│   • miner-deployment.yaml                                   │
│   • wallet-deployment.yaml                                  │
│   • full-node-deployment.yaml                               │
│   • services.yaml                                           │
│   • ingress.yaml                                            │
│                                                             │
│   Проблемы:                                                 │
│   ✗ Дублирование (каждый deployment почти одинаковый)      │
│   ✗ Разные окружения → копирование файлов                  │
│   ✗ Нет версионирования деплоев                            │
│   ✗ Сложно откатить неудачный деплой                       │
│   ✗ Нет зависимостей между приложениями                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Helm — пакетный менеджер

```
┌─────────────────────────────────────────────────────────────┐
│                      HELM                                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Helm — это "apt/yum/brew" для Kubernetes                  │
│                                                             │
│   Концепции:                                                │
│   • Chart — пакет (набор шаблонов + metadata)              │
│   • Release — установленный экземпляр chart                │
│   • Repository — хранилище charts                          │
│   • Values — параметры для кастомизации                    │
│                                                             │
│   Преимущества:                                             │
│   ✓ Шаблонизация (один chart → разные окружения)           │
│   ✓ Версионирование (chart v1.0.0, v1.1.0)                 │
│   ✓ История релизов и откат                                │
│   ✓ Зависимости между charts                               │
│   ✓ Переиспользование (community charts)                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Установка Helm

```bash
# macOS
brew install helm

# Linux
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Windows
choco install kubernetes-helm
# или
winget install Helm.Helm

# Проверка
helm version
# version.BuildInfo{Version:"v3.13.0", ...}
```

---

## Структура Helm Chart

```
┌─────────────────────────────────────────────────────────────┐
│                 СТРУКТУРА CHART                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   blockchain-network/              # Имя chart              │
│   ├── Chart.yaml                   # Метаданные chart       │
│   ├── values.yaml                  # Значения по умолчанию  │
│   ├── charts/                      # Зависимости (субчарты) │
│   ├── templates/                   # Шаблоны K8s ресурсов   │
│   │   ├── _helpers.tpl             # Вспомогательные шаблоны│
│   │   ├── deployment.yaml                                   │
│   │   ├── service.yaml                                      │
│   │   ├── configmap.yaml                                    │
│   │   ├── ingress.yaml                                      │
│   │   ├── NOTES.txt                # Сообщение после install│
│   │   └── tests/                   # Тесты релиза           │
│   │       └── test-connection.yaml                          │
│   └── .helmignore                  # Что игнорировать       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Chart.yaml

```yaml
# Chart.yaml — метаданные chart
apiVersion: v2                    # Helm 3
name: blockchain-network          # Имя chart
description: A Helm chart for deploying blockchain network
type: application                 # application или library
version: 0.1.0                    # Версия CHART (не приложения!)
appVersion: "1.0.0"               # Версия приложения

# Опционально
keywords:
  - blockchain
  - cryptocurrency
home: https://github.com/example/blockchain
sources:
  - https://github.com/example/blockchain
maintainers:
  - name: Developer
    email: dev@example.com

# Зависимости
dependencies:
  - name: postgresql
    version: "12.x.x"
    repository: https://charts.bitnami.com/bitnami
    condition: postgresql.enabled
```

### values.yaml

```yaml
# values.yaml — значения по умолчанию
# Пользователи могут переопределять при установке

# Общие настройки
replicaCount: 1

image:
  repository: blockchain-node
  tag: "latest"
  pullPolicy: IfNotPresent

# Настройки blockchain
blockchain:
  difficulty: 5
  genesisReward: "10.0"
  blockReward: "1.0"
  transactionFee: "0.1"

# Ресурсы
resources:
  requests:
    memory: "256Mi"
    cpu: "250m"
  limits:
    memory: "512Mi"
    cpu: "1000m"

# Service
service:
  type: ClusterIP
  port: 8080

# Ingress
ingress:
  enabled: true
  className: nginx
  hosts:
    - host: blockchain.localhost
      paths:
        - path: /
          pathType: Prefix

# Ноды
nodes:
  miner:
    enabled: true
    replicaCount: 1
  wallet:
    enabled: true
    replicaCount: 2
  fullNode:
    enabled: true
    replicaCount: 1
```

---

## Шаблоны (Templates)

### Базовый синтаксис

```yaml
# templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Release.Name }}-miner      # Имя релиза
  labels:
    {{- include "blockchain.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.nodes.miner.replicaCount }}
  selector:
    matchLabels:
      {{- include "blockchain.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "blockchain.selectorLabels" . | nindent 8 }}
    spec:
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - containerPort: {{ .Values.service.port }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
```

### Встроенные объекты

```
┌─────────────────────────────────────────────────────────────┐
│              ВСТРОЕННЫЕ ОБЪЕКТЫ                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  .Release        — информация о релизе                      │
│    .Name         — имя релиза (helm install NAME)           │
│    .Namespace    — namespace                                │
│    .IsUpgrade    — это upgrade?                             │
│    .IsInstall    — это install?                             │
│    .Revision     — номер ревизии                            │
│                                                             │
│  .Chart          — содержимое Chart.yaml                    │
│    .Name         — имя chart                                │
│    .Version      — версия chart                             │
│    .AppVersion   — версия приложения                        │
│                                                             │
│  .Values         — значения из values.yaml + переопределения│
│                                                             │
│  .Capabilities   — информация о кластере                    │
│    .KubeVersion  — версия K8s                               │
│    .APIVersions  — доступные API                            │
│                                                             │
│  .Template       — информация о текущем шаблоне             │
│    .Name         — путь к файлу шаблона                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Функции и pipelines

```yaml
# Функции шаблонизации
metadata:
  name: {{ .Release.Name | lower | trunc 63 }}
  # lower — в нижний регистр
  # trunc 63 — обрезать до 63 символов (K8s лимит)

# Условия
{{- if .Values.ingress.enabled }}
apiVersion: networking.k8s.io/v1
kind: Ingress
...
{{- end }}

# Циклы
{{- range .Values.ingress.hosts }}
  - host: {{ .host | quote }}
    http:
      paths:
        {{- range .paths }}
        - path: {{ .path }}
          pathType: {{ .pathType }}
        {{- end }}
{{- end }}

# Default значения
image: {{ .Values.image.repository | default "nginx" }}

# Required — ошибка если не задано
{{ required "image.tag is required" .Values.image.tag }}

# Конвертация в YAML
resources:
  {{- toYaml .Values.resources | nindent 2 }}
```

### _helpers.tpl — вспомогательные шаблоны

```yaml
# templates/_helpers.tpl

{{/*
Имя chart
*/}}
{{- define "blockchain.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Полное имя для ресурсов
*/}}
{{- define "blockchain.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{/*
Стандартные labels
*/}}
{{- define "blockchain.labels" -}}
helm.sh/chart: {{ include "blockchain.chart" . }}
{{ include "blockchain.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "blockchain.selectorLabels" -}}
app.kubernetes.io/name: {{ include "blockchain.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
```

### Использование helpers

```yaml
# templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "blockchain.fullname" . }}
  labels:
    {{- include "blockchain.labels" . | nindent 4 }}
spec:
  selector:
    matchLabels:
      {{- include "blockchain.selectorLabels" . | nindent 6 }}
```

---

## Основные команды Helm

### Работа с репозиториями

```bash
# Добавить репозиторий
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add stable https://charts.helm.sh/stable

# Обновить индекс
helm repo update

# Список репозиториев
helm repo list

# Поиск charts
helm search repo nginx
helm search hub nginx    # Поиск в Artifact Hub
```

### Установка и управление

```bash
# ═══════════════════════════════════════════════════════════
# УСТАНОВКА
# ═══════════════════════════════════════════════════════════

# Установить chart из репозитория
helm install my-release bitnami/nginx

# Установить локальный chart
helm install my-release ./blockchain-network

# С кастомными values
helm install my-release ./blockchain-network -f custom-values.yaml
helm install my-release ./blockchain-network --set replicaCount=3

# В конкретный namespace
helm install my-release ./blockchain-network -n blockchain --create-namespace

# Dry-run (показать что будет создано)
helm install my-release ./blockchain-network --dry-run

# Debug (подробный вывод)
helm install my-release ./blockchain-network --dry-run --debug


# ═══════════════════════════════════════════════════════════
# ПРОСМОТР
# ═══════════════════════════════════════════════════════════

# Список релизов
helm list
helm list -n blockchain
helm list -A                  # Все namespaces

# Статус релиза
helm status my-release

# История релиза
helm history my-release

# Показать values
helm get values my-release
helm get values my-release --all    # Включая defaults

# Показать сгенерированные манифесты
helm get manifest my-release


# ═══════════════════════════════════════════════════════════
# ОБНОВЛЕНИЕ
# ═══════════════════════════════════════════════════════════

# Обновить релиз
helm upgrade my-release ./blockchain-network

# С новыми values
helm upgrade my-release ./blockchain-network --set replicaCount=5

# Install если не существует, upgrade если существует
helm upgrade --install my-release ./blockchain-network


# ═══════════════════════════════════════════════════════════
# ОТКАТ
# ═══════════════════════════════════════════════════════════

# Откатить на предыдущую ревизию
helm rollback my-release

# Откатить на конкретную ревизию
helm rollback my-release 2


# ═══════════════════════════════════════════════════════════
# УДАЛЕНИЕ
# ═══════════════════════════════════════════════════════════

# Удалить релиз
helm uninstall my-release
helm uninstall my-release -n blockchain
```

### Разработка charts

```bash
# Создать новый chart (scaffold)
helm create my-chart

# Проверить синтаксис
helm lint ./my-chart

# Показать сгенерированные манифесты (template)
helm template my-release ./my-chart
helm template my-release ./my-chart -f values-dev.yaml

# Упаковать chart в .tgz
helm package ./my-chart
# → my-chart-0.1.0.tgz
```

---

## Практика: создание Helm chart

### Шаг 1: Создание структуры

```bash
# Создать scaffold
helm create blockchain-network

# Удалить лишнее (опционально)
cd blockchain-network
rm -rf templates/tests
rm templates/hpa.yaml
rm templates/serviceaccount.yaml
```

### Шаг 2: Chart.yaml

```yaml
# Chart.yaml
apiVersion: v2
name: blockchain-network
description: Helm chart for blockchain network deployment
type: application
version: 0.1.0
appVersion: "1.0.0"
```

### Шаг 3: values.yaml

```yaml
# values.yaml
global:
  namespace: blockchain

image:
  repository: blockchain-node
  tag: latest
  pullPolicy: IfNotPresent

blockchain:
  difficulty: 5
  genesisReward: "10.0"
  blockReward: "1.0"
  transactionFee: "0.1"
  minAmount: "0.1"

javaOpts: "-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

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
    resources:
      requests:
        memory: "256Mi"
        cpu: "100m"
      limits:
        memory: "512Mi"
        cpu: "500m"

  fullNode:
    enabled: true
    replicaCount: 1

service:
  type: ClusterIP
  port: 8080

ingress:
  enabled: true
  className: nginx
  annotations: {}
  hosts:
    - host: blockchain.localhost
      paths:
        - path: /
          pathType: Prefix
```

### Шаг 4: templates/configmap.yaml

```yaml
# templates/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "blockchain-network.fullname" . }}-config
  labels:
    {{- include "blockchain-network.labels" . | nindent 4 }}
data:
  BLOCKCHAIN_DIFFICULTY: {{ .Values.blockchain.difficulty | quote }}
  BLOCKCHAIN_GENESIS_REWARD: {{ .Values.blockchain.genesisReward | quote }}
  BLOCKCHAIN_BLOCK_REWARD: {{ .Values.blockchain.blockReward | quote }}
  BLOCKCHAIN_TRANSACTION_FEE: {{ .Values.blockchain.transactionFee | quote }}
  BLOCKCHAIN_MIN_AMOUNT: {{ .Values.blockchain.minAmount | quote }}
  JAVA_OPTS: {{ .Values.javaOpts | quote }}
```

### Шаг 5: templates/deployment.yaml (для miner)

```yaml
# templates/miner-deployment.yaml
{{- if .Values.nodes.miner.enabled }}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "blockchain-network.fullname" . }}-miner
  labels:
    {{- include "blockchain-network.labels" . | nindent 4 }}
    app.kubernetes.io/component: miner
spec:
  replicas: {{ .Values.nodes.miner.replicaCount }}
  selector:
    matchLabels:
      {{- include "blockchain-network.selectorLabels" . | nindent 6 }}
      app.kubernetes.io/component: miner
  template:
    metadata:
      labels:
        {{- include "blockchain-network.selectorLabels" . | nindent 8 }}
        app.kubernetes.io/component: miner
    spec:
      containers:
        - name: blockchain-node
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.service.port }}
          envFrom:
            - configMapRef:
                name: {{ include "blockchain-network.fullname" . }}-config
          env:
            - name: NODE_ROLE
              value: "miner"
            - name: SPRING_PROFILES_ACTIVE
              value: "miner"
          resources:
            {{- toYaml .Values.nodes.miner.resources | nindent 12 }}
          livenessProbe:
            httpGet:
              path: /api/node/health
              port: http
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /api/node/health
              port: http
            initialDelaySeconds: 10
            periodSeconds: 5
{{- end }}
```

### Шаг 6: templates/service.yaml

```yaml
# templates/service.yaml
{{- if .Values.nodes.miner.enabled }}
apiVersion: v1
kind: Service
metadata:
  name: {{ include "blockchain-network.fullname" . }}-miner
  labels:
    {{- include "blockchain-network.labels" . | nindent 4 }}
spec:
  type: {{ .Values.service.type }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: http
      protocol: TCP
      name: http
  selector:
    {{- include "blockchain-network.selectorLabels" . | nindent 4 }}
    app.kubernetes.io/component: miner
{{- end }}
```

### Шаг 7: Установка

```bash
# Lint
helm lint ./blockchain-network

# Template (проверить вывод)
helm template my-blockchain ./blockchain-network

# Установить
helm install my-blockchain ./blockchain-network -n blockchain --create-namespace

# Проверить
helm list -n blockchain
kubectl get all -n blockchain
```

---

## Values Override

### Несколько способов переопределения

```bash
# 1. Через --set
helm install my-release ./chart \
  --set replicaCount=3 \
  --set image.tag=v2.0.0

# 2. Через --set-string (всегда строка)
helm install my-release ./chart \
  --set-string image.tag=1.0

# 3. Через файл values
helm install my-release ./chart -f values-prod.yaml

# 4. Несколько файлов (последний приоритетнее)
helm install my-release ./chart \
  -f values.yaml \
  -f values-prod.yaml \
  -f values-secrets.yaml

# 5. Комбинация
helm install my-release ./chart \
  -f values-prod.yaml \
  --set image.tag=v2.0.0
```

### Примеры values для окружений

```yaml
# values-dev.yaml
nodes:
  miner:
    replicaCount: 1
  wallet:
    replicaCount: 1

blockchain:
  difficulty: 2

ingress:
  hosts:
    - host: blockchain.dev.local

# values-prod.yaml
nodes:
  miner:
    replicaCount: 3
    resources:
      requests:
        memory: "1Gi"
        cpu: "1000m"
  wallet:
    replicaCount: 5

blockchain:
  difficulty: 6

ingress:
  hosts:
    - host: blockchain.example.com
  tls:
    - secretName: blockchain-tls
      hosts:
        - blockchain.example.com
```

---

## NOTES.txt

```yaml
# templates/NOTES.txt
Blockchain Network deployed!

Release: {{ .Release.Name }}
Namespace: {{ .Release.Namespace }}

{{- if .Values.ingress.enabled }}
Application URL:
{{- range .Values.ingress.hosts }}
  http://{{ .host }}
{{- end }}
{{- else }}
Get the application URL by running:
  kubectl port-forward svc/{{ include "blockchain-network.fullname" . }}-miner {{ .Values.service.port }}:{{ .Values.service.port }} -n {{ .Release.Namespace }}
  Then visit: http://localhost:{{ .Values.service.port }}
{{- end }}

Check pods:
  kubectl get pods -n {{ .Release.Namespace }} -l app.kubernetes.io/instance={{ .Release.Name }}

Check logs:
  kubectl logs -f deployment/{{ include "blockchain-network.fullname" . }}-miner -n {{ .Release.Namespace }}
```

---

## Частые ошибки

### 1. YAML indentation

```yaml
# ❌ НЕПРАВИЛЬНО
resources:
{{- toYaml .Values.resources }}    # Без nindent!

# ✓ ПРАВИЛЬНО
resources:
  {{- toYaml .Values.resources | nindent 2 }}
```

### 2. Whitespace control

```yaml
# Проблема: лишние пустые строки

{{- if .Values.enabled }}        # "-" убирает whitespace ДО
apiVersion: v1
{{- end }}                       # "-" убирает whitespace ДО

# Без "-":
{{if .Values.enabled}}
apiVersion: v1              # Будет пустая строка перед
{{ end }}
```

### 3. Quote vs без quote

```yaml
# Числа и boolean не нужно quote
replicas: {{ .Values.replicaCount }}

# Строки которые могут быть числами — нужно quote
image:
  tag: {{ .Values.image.tag | quote }}
  # "1.0" вместо 1.0 (который станет 1)
```

---

## Вопросы для самопроверки

1. **Что такое Helm chart?**

   <details>
   <summary>Ответ</summary>

   Chart — это пакет, содержащий шаблоны K8s ресурсов и метаданные. Позволяет параметризировать и переиспользовать конфигурацию.
   </details>

2. **Чем Release отличается от Chart?**

   <details>
   <summary>Ответ</summary>

   - **Chart**: пакет/шаблон (как .deb файл)
   - **Release**: установленный экземпляр chart (как установленный пакет)

   Один chart может иметь много releases.
   </details>

3. **Как переопределить values при установке?**

   <details>
   <summary>Ответ</summary>

   ```bash
   # Через файл
   helm install release chart -f values-custom.yaml

   # Через --set
   helm install release chart --set key=value
   ```
   </details>

4. **Что делает `helm upgrade --install`?**

   <details>
   <summary>Ответ</summary>

   Если release не существует — создаёт (install). Если существует — обновляет (upgrade). Удобно для CI/CD.
   </details>

5. **Как откатить неудачный релиз?**

   <details>
   <summary>Ответ</summary>

   ```bash
   # На предыдущую версию
   helm rollback release-name

   # На конкретную ревизию
   helm rollback release-name 2
   ```
   </details>

---

## Итоги урока

| Концепт | Описание |
|---------|----------|
| **Chart** | Пакет с шаблонами K8s ресурсов |
| **Release** | Установленный экземпляр chart |
| **values.yaml** | Параметры по умолчанию |
| **templates/** | Go templates для K8s манифестов |
| **_helpers.tpl** | Переиспользуемые шаблоны |

| Команда | Действие |
|---------|----------|
| `helm create` | Создать новый chart |
| `helm install` | Установить chart |
| `helm upgrade` | Обновить release |
| `helm rollback` | Откатить release |
| `helm uninstall` | Удалить release |
| `helm template` | Рендерить шаблоны локально |
| `helm lint` | Проверить синтаксис |

В следующем уроке изучим **Troubleshooting** — как диагностировать и решать проблемы в K8s.
