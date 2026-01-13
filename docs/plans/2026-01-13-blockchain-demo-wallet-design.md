# Blockchain Demo Wallet — Design Document

**Дата:** 2026-01-13
**Статус:** Draft

## Обзор

JavaFX приложение-кошелёк с визуализацией blockchain. Можно запустить 2-3 экземпляра на localhost, они соединятся по TCP и будут синхронизировать блоки.

## Главный сценарий

1. Запускаешь 2 ноды (разные порты)
2. Создаёшь кошелёк на каждой
3. Майнишь genesis-блок (получаешь монеты)
4. Отправляешь монеты с ноды A на ноду B
5. Видишь как транзакция попадает в мемпул
6. Майнишь блок — видишь анимацию перебора nonce
7. Блок распространяется на вторую ноду
8. Балансы обновляются

---

## Архитектура

3 слоя:

```
┌─────────────────────────────────────┐
│  UI (JavaFX)                        │
│  - BlockchainView (цепочка блоков)  │
│  - WalletPane (баланс, адрес)       │
│  - TransactionForm (отправка)       │
│  - MiningView (анимация nonce)      │
│  - PeersPane (подключённые ноды)    │
├─────────────────────────────────────┤
│  Service Layer                      │
│  - WalletService                    │
│  - MiningService (фоновый поток)    │
│  - NetworkService (TCP)             │
├─────────────────────────────────────┤
│  Core (уже есть из уроков)          │
│  - Block, Blockchain, Transaction   │
│  - Wallet, UTXOPool, ProofOfWork    │
│  - Node, Message, PeerConnection    │
└─────────────────────────────────────┘
```

---

## UI — визуализация цепочки

Главный экран разбит на зоны:

```
┌────────────────────────────────────────────────────────┐
│  [Node: localhost:8001]  [Peers: 1]  [Start Mining ▶]  │
├──────────────────────────┬─────────────────────────────┤
│                          │  Wallet                     │
│   BLOCKCHAIN VIEW        │  ┌─────────────────────┐   │
│                          │  │ Balance: 50.0 BTC   │   │
│  ┌───┐    ┌───┐    ┌───┐ │  │ Address: a3f2...    │   │
│  │ 0 │───▶│ 1 │───▶│ 2 │ │  └─────────────────────┘   │
│  └───┘    └───┘    └───┘ │                             │
│  genesis   ...     ...   │  Send Transaction           │
│                          │  ┌─────────────────────┐   │
│                          │  │ To: [___________]   │   │
│  [Click block for info]  │  │ Amount: [____]      │   │
│                          │  │ [Send]              │   │
├──────────────────────────┴─────────────────────────────┤
│  Mining: nonce=834756  hash=0000a8f3...  ████████░ 80% │
├────────────────────────────────────────────────────────┤
│  Mempool (2 tx)  │  Peers: 192.168.1.5:8002 ✓         │
└────────────────────────────────────────────────────────┘
```

### BlockchainView (центр)

- Блоки как прямоугольники со стрелками
- Цвет: genesis=золотой, обычные=синие, новый=зелёная анимация
- По клику на блок — popup с деталями (hash, nonce, transactions)
- Горизонтальный скролл для длинных цепочек

### Анимации

- Новый блок "влетает" справа с подсветкой
- Mining: прогресс-бар + счётчик nonce в реальном времени
- Синхронизация: блоки "прилетают" от peer с иконкой стрелки

---

## Data Flow

### Сценарий: отправка монет

```
User clicks "Send"
       │
       ▼
┌─────────────────┐
│ WalletService   │
│ - createTx()    │──────┐
│ - signTx()      │      │
└─────────────────┘      │
       │                 │
       ▼                 ▼
┌─────────────────┐  ┌─────────────────┐
│ Mempool         │  │ NetworkService  │
│ - addTx(tx)     │  │ - broadcast(tx) │
└─────────────────┘  └─────────────────┘
       │                 │
       │                 ▼
       │            [TCP to peers]
       ▼
┌─────────────────┐
│ MiningService   │  (берёт tx из mempool)
│ - mine(block)   │
└─────────────────┘
       │
       ▼
┌─────────────────┐
│ Blockchain      │
│ - addBlock()    │
│ - updateUTXO()  │
└─────────────────┘
       │
       ▼
┌─────────────────┐
│ UI обновляется  │  (JavaFX bindings)
│ - новый блок    │
│ - новый баланс  │
└─────────────────┘
```

### Потоки

- **UI thread** — только отрисовка
- **Mining thread** — фоновый, не блокирует UI
- **Network thread** — слушает входящие соединения
- **JavaFX Properties** — автоматически обновляют UI при изменении данных

### События между компонентами

```java
// Пример: MiningService нашёл блок
miningService.onBlockMined(block -> {
    blockchain.addBlock(block);           // обновить цепочку
    utxoPool.processBlock(block);         // обновить UTXO
    networkService.broadcast(block);      // отправить peers
    Platform.runLater(() -> ui.update()); // обновить UI
});
```

---

## Сетевой протокол

### Подключение нод

```
Node A (port 8001)              Node B (port 8002)
      │                               │
      │◄────── TCP connect ───────────│
      │                               │
      │─── HANDSHAKE {nodeId, port} ──▶
      │◄── HANDSHAKE {nodeId, port} ───│
      │                               │
      │         [connected]           │
```

### Сообщения (используем существующий MessageType)

| Type | Когда | Payload |
|------|-------|---------|
| `HANDSHAKE` | При подключении | `{nodeId, port, chainHeight}` |
| `NEW_BLOCK` | Намайнили блок | Block JSON |
| `NEW_TRANSACTION` | Создали tx | Transaction JSON |
| `GET_BLOCKS` | Синхронизация | `{fromIndex}` |
| `BLOCKS` | Ответ на GET_BLOCKS | `[Block, Block, ...]` |

### Синхронизация при подключении

```
Node A (height=5)               Node B (height=3)
      │                               │
      │◄── HANDSHAKE {height=3} ──────│
      │                               │
      │  "У меня больше, отправлю"    │
      │                               │
      │─── BLOCKS [block4, block5] ───▶
      │                               │
      │         [B теперь height=5]   │
```

### Конфликты (форки)

- Простое правило: **longest chain wins**
- Если получили блок с тем же index но другим hash — сравниваем длину
- Для демо этого достаточно

---

## Структура файлов

### Новые файлы (пакет `ui`)

```
src/main/java/com/study/blockchain/
├── core/          # уже есть
├── transaction/   # уже есть
├── wallet/        # уже есть
├── mining/        # уже есть
├── network/       # уже есть
└── ui/            # НОВОЕ
    ├── App.java              # main(), запуск JavaFX
    ├── MainController.java   # связывает UI и сервисы
    ├── BlockchainView.java   # Canvas с блоками и стрелками
    ├── BlockPopup.java       # детали блока по клику
    ├── WalletPane.java       # баланс, адрес
    ├── SendForm.java         # форма отправки
    ├── MiningPane.java       # прогресс майнинга
    ├── PeersPane.java        # список подключённых нод
    └── service/
        ├── MiningService.java    # фоновый майнинг
        └── NetworkService.java   # обёртка над Node
```

### Запуск

```bash
# Терминал 1
./gradlew run --args="--port=8001"

# Терминал 2
./gradlew run --args="--port=8002 --connect=localhost:8001"
```

### Зависимости (build.gradle)

```kotlin
plugins {
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.graphics")
}
```

---

## Что НЕ делаем (YAGNI)

- Persistence (сохранение на диск) — только in-memory
- Сложная валидация транзакций — базовая проверка подписи
- Красивые стили CSS — стандартный JavaFX look
- HD-кошельки — один ключ на кошелёк
- Merkle tree — упрощённая структура блока