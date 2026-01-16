# Урок 1: Основы Docker

## Цели урока
После этого урока вы будете:
- Понимать, какую проблему решает Docker
- Знать разницу между контейнерами и виртуальными машинами
- Понимать архитектуру Docker и как компоненты взаимодействуют
- Уметь работать с базовыми командами Docker
- Запускать Java-приложения в контейнерах

---

## 1. Зачем нужен Docker

### Проблема: "It works on my machine"

Представьте ситуацию: вы разработали Java-приложение на своём компьютере. Всё работает отлично. Отправляете коллеге — у него не запускается. Деплоите на сервер — снова ошибки.

**Почему так происходит?**

```
Ваш компьютер:                    Сервер:
├── Java 21.0.2                   ├── Java 17.0.1        ← Другая версия
├── Maven 3.9.6                   ├── Maven 3.8.4        ← Другая версия
├── JAVA_HOME=/usr/lib/jvm/21     ├── JAVA_HOME=/opt/java ← Другой путь
├── PostgreSQL 16                 ├── PostgreSQL 14       ← Другая версия
└── Ubuntu 22.04                  └── CentOS 8            ← Другая ОС
```

Различия в:
- Версиях языка/рантайма
- Версиях библиотек и зависимостей
- Переменных окружения
- Операционной системе
- Конфигурации сети

**Традиционные решения и их проблемы:**

| Решение | Проблема |
|---------|----------|
| Документация "как установить" | Устаревает, люди делают ошибки |
| Скрипты установки | Разные ОС требуют разных скриптов |
| Виртуальные машины | Тяжёлые (гигабайты), медленный запуск |

### Решение Docker: упаковка окружения вместе с приложением

Docker позволяет упаковать приложение **вместе со всем его окружением** в единый артефакт — **образ (image)**.

```
Docker Image: my-blockchain-app:1.0
┌─────────────────────────────────────┐
│  Ваше приложение (blockchain.jar)   │
├─────────────────────────────────────┤
│  Java 21 JRE                        │
├─────────────────────────────────────┤
│  Нужные библиотеки                  │
├─────────────────────────────────────┤
│  Конфигурация                       │
├─────────────────────────────────────┤
│  Минимальная ОС (Alpine Linux)      │
└─────────────────────────────────────┘
```

Теперь этот образ можно запустить **где угодно**, где есть Docker:
- На вашем компьютере
- На компьютере коллеги
- На тестовом сервере
- В продакшене
- В облаке (AWS, GCP, Azure)

**Результат:** если работает локально — работает везде.

---

## 2. Контейнеры vs Виртуальные машины

### Виртуальная машина (VM)

VM эмулирует **полный компьютер** с собственной операционной системой.

```
┌────────────────────────────────────────────────────────┐
│                    Ваш компьютер                        │
├────────────────────────────────────────────────────────┤
│                     Host OS (Windows/Mac/Linux)         │
├────────────────────────────────────────────────────────┤
│                     Hypervisor (VMware, VirtualBox)     │
├──────────────────┬──────────────────┬──────────────────┤
│       VM 1       │       VM 2       │       VM 3       │
├──────────────────┼──────────────────┼──────────────────┤
│ Guest OS (Ubuntu)│ Guest OS (CentOS)│ Guest OS (Debian)│
│     ~2 GB        │      ~1.5 GB     │      ~1 GB       │
├──────────────────┼──────────────────┼──────────────────┤
│   Библиотеки     │   Библиотеки     │   Библиотеки     │
├──────────────────┼──────────────────┼──────────────────┤
│   Приложение A   │   Приложение B   │   Приложение C   │
└──────────────────┴──────────────────┴──────────────────┘
```

**Характеристики VM:**
- Каждая VM содержит **полную копию ОС** (ядро, драйверы, системные утилиты)
- Размер: гигабайты
- Запуск: минуты
- Изоляция: полная (разные ядра ОС)

### Контейнер

Контейнер — это **изолированный процесс**, который использует ядро хост-системы.

```
┌────────────────────────────────────────────────────────┐
│                    Ваш компьютер                       │
├────────────────────────────────────────────────────────┤
│                     Host OS (Linux kernel)             │
├────────────────────────────────────────────────────────┤
│                     Docker Engine                      │
├──────────────────┬──────────────────┬──────────────────┤
│   Container 1    │   Container 2    │   Container 3    │
├──────────────────┼──────────────────┼──────────────────┤
│   Библиотеки     │   Библиотеки     │   Библиотеки     │
│     ~50 MB       │      ~80 MB      │      ~50 MB      │
├──────────────────┼──────────────────┼──────────────────┤
│   Приложение A   │   Приложение B   │   Приложение C   │
└──────────────────┴──────────────────┴──────────────────┘
        │                  │                  │
        └──────────────────┴──────────────────┘
                           │
                    Общее ядро Linux
```

**Характеристики контейнеров:**
- Контейнеры **разделяют ядро** хост-системы
- Размер: мегабайты (только приложение + его зависимости)
- Запуск: секунды (не нужно загружать ОС)
- Изоляция: на уровне процессов (namespaces, cgroups)

### Сравнение

| Характеристика | Виртуальная машина | Контейнер |
|----------------|-------------------|-----------|
| **Размер** | 1-10 GB | 10-500 MB |
| **Запуск** | 1-5 минут | 1-5 секунд |
| **Потребление RAM** | Фиксированное (выделяется заранее) | Динамическое (по потребности) |
| **Изоляция** | Полная (свой kernel) | Процессная (общий kernel) |
| **Накладные расходы** | Высокие | Минимальные |
| **Плотность** | 10-20 VM на сервер | 100+ контейнеров на сервер |

### Когда что использовать

**VM подходит когда:**
- Нужна полная изоляция (разные ОС, kernel-level security)
- Запускаете Windows-приложение на Linux-сервере
- Legacy-системы, требующие специфичную ОС

**Контейнеры подходят когда:**
- Микросервисная архитектура
- CI/CD пайплайны
- Быстрое масштабирование
- Разработка и тестирование
- Большинство современных веб-приложений

---

## 3. Архитектура Docker

Docker использует клиент-серверную архитектуру.

```
┌─────────────────────────────────────────────────────────────────┐
│                         Ваш компьютер                           │
│                                                                 │
│  ┌──────────────┐         REST API        ┌──────────────────┐  │
│  │              │ ────────────────────▶   │                  │  │
│  │ Docker CLI   │                         │  Docker Daemon   │  │
│  │ (клиент)     │ ◀────────────────────   │  (dockerd)       │  │
│  │              │                         │                  │  │
│  └──────────────┘                         └────────┬─────────┘  │
│        │                                           │            │
│        │ docker run nginx                          │            │
│        │ docker build .                            ▼            │
│        │ docker push ...            ┌─────────────────────────┐ │
│        │                            │      Управляет:         │ │
│                                     │  • Images (образы)      │ │
│                                     │  • Containers           │ │
│                                     │  • Networks             │ │
│                                     │  • Volumes              │ │
│                                     └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                │
                                │ docker pull / docker push
                                ▼
                    ┌───────────────────────┐
                    │    Docker Registry    │
                    │    (Docker Hub)       │
                    │                       │
                    │  nginx:latest         │
                    │  postgres:16          │
                    │  eclipse-temurin:21   │
                    └───────────────────────┘
```

### Компоненты

**Docker Client (CLI)** — это команда `docker`, которую вы вводите в терминале.
- Принимает ваши команды
- Отправляет запросы к Docker Daemon через REST API
- Отображает результаты

**Docker Daemon (dockerd)** — сервис, работающий в фоне.
- Управляет образами, контейнерами, сетями, томами
- Выполняет сборку образов
- Взаимодействует с registry для push/pull образов

**Docker Registry** — хранилище образов.
- **Docker Hub** — публичный registry по умолчанию (hub.docker.com)
- Содержит официальные образы (nginx, postgres, openjdk и т.д.)
- Можно использовать приватные registry (AWS ECR, Google GCR, Harbor)

### Как это работает вместе

Когда вы выполняете `docker run nginx`:

```
1. CLI ──────────▶ "Запусти nginx"
         │
2.       └───────▶ Daemon: "Есть ли образ nginx локально?"
                            │
3.                          ├── Нет ──▶ Скачать из Docker Hub
                            │              │
4.                          │              ▼
                            │           Registry: nginx:latest
                            │              │
5.                          │◀─────────────┘
                            │
6.                          └── Создать и запустить контейнер
                                       │
7.                                     ▼
                                  Контейнер nginx работает
```

---

## 4. Ключевые концепции

### 4.1 Docker Image (Образ)

**Образ** — это read-only шаблон, содержащий всё необходимое для запуска приложения:
- Базовая ОС (или её минимальная версия)
- Runtime (Java, Node.js, Python)
- Библиотеки и зависимости
- Код приложения
- Конфигурация

**Аналогия из Java:**
```java
// Image — это как Class
public class BlockchainNode { ... }

// Container — это как Object (экземпляр класса)
BlockchainNode node1 = new BlockchainNode();
BlockchainNode node2 = new BlockchainNode();
```

**Слои образа (Layers):**

Образ состоит из слоёв. Каждая инструкция в Dockerfile создаёт новый слой.

```
Image: my-java-app:1.0
┌─────────────────────────────┐
│ Layer 4: COPY app.jar       │  ← Ваш код (меняется часто)
├─────────────────────────────┤
│ Layer 3: RUN apt-get update │  ← Зависимости
├─────────────────────────────┤
│ Layer 2: ENV JAVA_HOME=...  │  ← Конфигурация
├─────────────────────────────┤
│ Layer 1: eclipse-temurin:21 │  ← Базовый образ (меняется редко)
└─────────────────────────────┘
```

**Почему слои важны:**
- Слои кэшируются — повторная сборка быстрее
- Слои переиспользуются — экономия места
- При изменении пересобираются только изменённые слои и слои выше

**Именование образов:**
```
registry/repository:tag

Примеры:
docker.io/library/nginx:1.25        # Полное имя
nginx:1.25                          # Сокращённо (docker.io/library/ подразумевается)
nginx:latest                        # latest — тег по умолчанию
nginx                               # То же что nginx:latest
mycompany/myapp:v1.2.3             # Ваш образ
gcr.io/my-project/myapp:latest     # Google Container Registry
```

### 4.2 Docker Container (Контейнер)

**Контейнер** — это запущенный экземпляр образа.

```
                    docker run
        Image ─────────────────────▶ Container
     (read-only)                    (read-write layer сверху)
```

**Характеристики контейнера:**
- Изолированный процесс со своим filesystem, network, process space
- Имеет тонкий read-write слой поверх образа
- Изменения внутри контейнера не влияют на образ
- После удаления контейнера изменения теряются (если не сохранены в volume)

**Жизненный цикл контейнера:**
```
Created ──▶ Running ──▶ Paused ──▶ Running ──▶ Stopped ──▶ Removed
   │            │                      │            │
   │            └──────────────────────┘            │
   │                   (unpause)                    │
   │                                                │
   └────────────────────────────────────────────────┘
                    (start/stop/rm)
```

### 4.3 Dockerfile

**Dockerfile** — текстовый файл с инструкциями для сборки образа.

```dockerfile
# Базовый образ
FROM eclipse-temurin:21-jre-alpine

# Метаданные
LABEL maintainer="developer@example.com"

# Установка зависимостей
RUN apk add --no-cache curl

# Рабочая директория
WORKDIR /app

# Копирование файлов
COPY target/app.jar app.jar

# Переменные окружения
ENV JAVA_OPTS="-Xmx512m"

# Открытие порта (документация)
EXPOSE 8080

# Команда запуска
CMD ["java", "-jar", "app.jar"]
```

### 4.4 Docker Registry

**Registry** — сервис для хранения и распространения образов.

```
┌──────────────────────────────────────────────────────────┐
│                     Docker Hub                           │
│                  (hub.docker.com)                        │
├──────────────────────────────────────────────────────────┤
│                                                          │
│   Official Images:        User Images:                   │
│   ├── nginx               ├── myuser/myapp               │
│   ├── postgres            ├── company/service            │
│   ├── redis               └── ...                        │
│   ├── eclipse-temurin                                    │
│   └── ...                                                │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

**Работа с registry:**
```bash
# Скачать образ
docker pull nginx:1.25

# Авторизоваться
docker login

# Загрузить свой образ
docker tag myapp:1.0 myuser/myapp:1.0
docker push myuser/myapp:1.0
```

---

## 5. Практика: Базовые команды Docker

### 5.1 Установка Docker

**Windows / macOS:**
1. Скачайте [Docker Desktop](https://www.docker.com/products/docker-desktop)
2. Установите и запустите
3. Docker Desktop включает Docker Engine, CLI и Docker Compose

**Linux (Ubuntu/Debian):**
```bash
# Установка через официальный скрипт
curl -fsSL https://get.docker.com | sh

# Добавление пользователя в группу docker (чтобы не использовать sudo)
sudo usermod -aG docker $USER

# Перелогиньтесь или выполните
newgrp docker
```

**Проверка установки:**
```bash
docker --version
# Docker version 24.0.7, build afdd53b

docker info
# Покажет информацию о Docker Engine
```

### 5.2 Работа с образами

```bash
# Поиск образов на Docker Hub
docker search nginx

# Скачать образ
docker pull eclipse-temurin:21-jre-alpine
# Вывод:
# 21-jre-alpine: Pulling from library/eclipse-temurin
# 31e352740f53: Pull complete
# ...
# Status: Downloaded newer image for eclipse-temurin:21-jre-alpine

# Список локальных образов
docker images
# REPOSITORY         TAG              IMAGE ID       CREATED        SIZE
# eclipse-temurin    21-jre-alpine    abc123def456   2 weeks ago    186MB
# nginx              latest           def456abc789   3 weeks ago    142MB

# Удалить образ
docker rmi nginx:latest

# Удалить все неиспользуемые образы
docker image prune
```

### 5.3 Запуск контейнеров

**Базовый запуск:**
```bash
# Запустить и сразу выполнить команду
docker run eclipse-temurin:21-jre-alpine java -version

# Вывод:
# openjdk version "21.0.1" 2023-10-17 LTS
# OpenJDK Runtime Environment Temurin-21.0.1+12 (build 21.0.1+12-LTS)
# OpenJDK 64-Bit Server VM Temurin-21.0.1+12 (build 21.0.1+12-LTS, mixed mode)
```

**Интерактивный режим:**
```bash
# -i = interactive (держать STDIN открытым)
# -t = tty (выделить псевдо-терминал)
docker run -it eclipse-temurin:21-jre-alpine sh

# Теперь вы внутри контейнера
/ # whoami
root
/ # java -version
openjdk version "21.0.1"...
/ # exit
```

**Фоновый режим (daemon):**
```bash
# -d = detached (фоновый режим)
# --name = имя контейнера
# -p = проброс портов (host:container)
docker run -d --name my-nginx -p 8080:80 nginx

# Проверить
curl http://localhost:8080
# <!DOCTYPE html>
# <html>
# <head>
# <title>Welcome to nginx!</title>
# ...
```

### 5.4 Управление контейнерами

```bash
# Список запущенных контейнеров
docker ps
# CONTAINER ID   IMAGE   COMMAND                  CREATED         STATUS         PORTS                  NAMES
# a1b2c3d4e5f6   nginx   "/docker-entrypoint.…"   2 minutes ago   Up 2 minutes   0.0.0.0:8080->80/tcp   my-nginx

# Список всех контейнеров (включая остановленные)
docker ps -a

# Логи контейнера
docker logs my-nginx

# Логи в реальном времени (follow)
docker logs -f my-nginx

# Выполнить команду в работающем контейнере
docker exec my-nginx cat /etc/nginx/nginx.conf

# Войти в контейнер интерактивно
docker exec -it my-nginx sh

# Остановить контейнер
docker stop my-nginx

# Запустить остановленный контейнер
docker start my-nginx

# Перезапустить
docker restart my-nginx

# Удалить остановленный контейнер
docker rm my-nginx

# Принудительно удалить (даже работающий)
docker rm -f my-nginx

# Удалить все остановленные контейнеры
docker container prune
```

### 5.5 Полезные флаги docker run

```bash
docker run [OPTIONS] IMAGE [COMMAND]

# Основные опции:
-d, --detach          # Запуск в фоне
-it                   # Интерактивный режим с терминалом
--name NAME           # Задать имя контейнеру
-p HOST:CONTAINER     # Проброс порта
-P                    # Проброс всех EXPOSE портов на случайные
--rm                  # Удалить контейнер после остановки
-e VAR=value          # Установить переменную окружения
-v HOST:CONTAINER     # Монтировать volume
-w /path              # Рабочая директория
--network NAME        # Подключить к сети
--restart POLICY      # Политика перезапуска (no, always, on-failure)
```

**Примеры:**
```bash
# Запустить PostgreSQL с переменными окружения
docker run -d \
  --name my-postgres \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=mydb \
  -p 5432:5432 \
  postgres:16

# Запустить с монтированием директории
docker run -d \
  --name my-nginx \
  -p 8080:80 \
  -v $(pwd)/html:/usr/share/nginx/html \
  nginx

# Запустить с автоматическим перезапуском
docker run -d \
  --name my-app \
  --restart unless-stopped \
  -p 8080:8080 \
  my-app:latest
```

---

## 6. Практика: Запуск Java в Docker

### Упражнение 1: Простой запуск

Создайте файл `Hello.java`:
```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello from Docker!");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name"));
    }
}
```

Скомпилируйте и запустите в контейнере:
```bash
# Компиляция
docker run --rm \
  -v $(pwd):/app \
  -w /app \
  eclipse-temurin:21 \
  javac Hello.java

# Запуск
docker run --rm \
  -v $(pwd):/app \
  -w /app \
  eclipse-temurin:21-jre \
  java Hello

# Вывод:
# Hello from Docker!
# Java version: 21.0.1
# OS: Linux
```

**Разбор команды:**
- `--rm` — удалить контейнер после завершения
- `-v $(pwd):/app` — смонтировать текущую директорию в /app контейнера
- `-w /app` — установить /app как рабочую директорию
- `eclipse-temurin:21` — образ с JDK для компиляции
- `eclipse-temurin:21-jre` — образ только с JRE для запуска (меньше размер)

### Упражнение 2: Веб-сервер

Запустите простой HTTP-сервер на Java:

```bash
# Создать HTML файл
echo "<h1>Hello from Docker!</h1>" > index.html

# Запустить встроенный HTTP-сервер Java (доступен с Java 18+)
docker run --rm -d \
  --name java-server \
  -v $(pwd):/app \
  -w /app \
  -p 8000:8000 \
  eclipse-temurin:21 \
  java -m jdk.httpserver -p 8000

# Проверить
curl http://localhost:8000
# <h1>Hello from Docker!</h1>

# Остановить
docker stop java-server
```

---

## 7. Типичные ошибки и решения

### Ошибка: "permission denied"
```bash
docker: permission denied while trying to connect to the Docker daemon socket
```
**Решение:** Добавьте пользователя в группу docker
```bash
sudo usermod -aG docker $USER
newgrp docker  # или перелогиньтесь
```

### Ошибка: "port is already allocated"
```bash
Error: port is already allocated
```
**Решение:** Порт занят другим процессом
```bash
# Найти что использует порт
lsof -i :8080
# или на Windows
netstat -ano | findstr :8080

# Использовать другой порт
docker run -p 8081:80 nginx
```

### Ошибка: "no space left on device"
```bash
no space left on device
```
**Решение:** Очистить неиспользуемые ресурсы Docker
```bash
# Удалить всё неиспользуемое (осторожно!)
docker system prune -a

# Более избирательно:
docker container prune  # Удалить остановленные контейнеры
docker image prune      # Удалить dangling образы
docker volume prune     # Удалить неиспользуемые volumes
```

### Ошибка: "image not found"
```bash
Unable to find image 'myapp:latest' locally
```
**Решение:** Проверьте правильность имени образа
```bash
# Поиск на Docker Hub
docker search myapp

# Или постройте образ локально
docker build -t myapp:latest .
```

### Контейнер сразу останавливается
**Причина:** Главный процесс контейнера завершается.
**Решение:** Контейнер живёт пока работает его главный процесс.
```bash
# Плохо — echo выполнится и контейнер остановится
docker run -d ubuntu echo "hello"

# Хорошо — tail -f не завершается
docker run -d ubuntu tail -f /dev/null

# Правильно — запускать реальный сервис
docker run -d nginx
```

---

## 8. Вопросы для самопроверки

1. **Какую основную проблему решает Docker?**
   <details>
   <summary>Ответ</summary>
   Проблему "It works on my machine" — Docker позволяет упаковать приложение вместе с его окружением, гарантируя одинаковую работу везде.
   </details>

2. **В чём главное отличие контейнера от виртуальной машины?**
   <details>
   <summary>Ответ</summary>
   Контейнеры разделяют ядро хост-системы и изолируют только процессы, а VM включает полную гостевую ОС со своим ядром. Поэтому контейнеры легче (MB vs GB) и быстрее запускаются (секунды vs минуты).
   </details>

3. **Что такое Docker Image и Docker Container? Как они связаны?**
   <details>
   <summary>Ответ</summary>
   Image — это read-only шаблон с приложением и его зависимостями (как Class в Java). Container — это запущенный экземпляр образа (как Object). Из одного образа можно создать много контейнеров.
   </details>

4. **Какие три основных компонента архитектуры Docker?**
   <details>
   <summary>Ответ</summary>
   Docker Client (CLI) — принимает команды пользователя. Docker Daemon (dockerd) — выполняет команды, управляет контейнерами. Docker Registry — хранит образы.
   </details>

5. **Что делает команда `docker run -d -p 8080:80 --name web nginx`?**
   <details>
   <summary>Ответ</summary>
   Запускает контейнер из образа nginx: в фоновом режиме (-d), пробрасывает порт 80 контейнера на порт 8080 хоста (-p 8080:80), с именем "web" (--name web).
   </details>

6. **Как посмотреть логи контейнера в реальном времени?**
   <details>
   <summary>Ответ</summary>
   `docker logs -f container_name` — флаг -f (follow) выводит логи в реальном времени.
   </details>

7. **Что произойдёт с данными внутри контейнера после его удаления?**
   <details>
   <summary>Ответ</summary>
   Данные будут потеряны. Контейнер имеет эфемерный (временный) слой для записи. Для сохранения данных нужно использовать volumes.
   </details>

8. **Зачем нужен флаг `--rm` при запуске контейнера?**
   <details>
   <summary>Ответ</summary>
   Флаг --rm автоматически удаляет контейнер после его остановки. Полезно для одноразовых задач (компиляция, тесты), чтобы не накапливать остановленные контейнеры.
   </details>

9. **Почему образы состоят из слоёв? Какое преимущество это даёт?**
   <details>
   <summary>Ответ</summary>
   Слои кэшируются и переиспользуются. При пересборке образа пересобираются только изменённые слои. Разные образы могут разделять общие базовые слои, экономя место на диске.
   </details>

10. **Как выполнить команду внутри работающего контейнера?**
    <details>
    <summary>Ответ</summary>
    `docker exec container_name command` — для одной команды, или `docker exec -it container_name sh` — для интерактивного входа в контейнер.
    </details>

---

## Итоги урока

Вы изучили:
- ✅ Проблему, которую решает Docker ("works on my machine")
- ✅ Разницу между контейнерами и виртуальными машинами
- ✅ Архитектуру Docker: Client, Daemon, Registry
- ✅ Ключевые концепции: Image, Container, Dockerfile, Registry
- ✅ Базовые команды для работы с образами и контейнерами
- ✅ Запуск Java-приложений в Docker

**Следующий урок:** Dockerfile для Java — научимся создавать собственные образы.
