# Урок 10: P2P сеть

## Введение

До сих пор наш blockchain работал локально. Но настоящая сила blockchain — в **децентрализации**. Тысячи узлов хранят копию цепочки и проверяют транзакции.

В этом финальном уроке создадим простую P2P сеть.

---

## Архитектура сетей

### Централизованная (Client-Server)

```
        ┌─────────┐
        │ Server  │
        └────┬────┘
       ┌─────┼─────┐
       ▼     ▼     ▼
    Client Client Client
```

Проблемы:
- Единая точка отказа
- Цензура
- Масштабирование

### Децентрализованная (P2P)

```
    Node ←──→ Node
      ↑  ╲ ╱  ↑
      │   ╳   │
      ▼  ╱ ╲  ▼
    Node ←──→ Node
```

Каждый узел:
- И клиент, и сервер
- Хранит копию данных
- Пересылает сообщения

---

## История P2P

### Napster (1999)

Первая массовая P2P сеть. Централизованный индекс, но файлы у пользователей.

```
User → Central Index → "song.mp3 at IP:port" → User downloads from peer
```

Убит судебными исками (централизованный индекс = ответственность).

### Gnutella (2000)

Полностью децентрализованный:
```
Query: "song.mp3" → flood to all peers → responses back
```

Проблема: flooding не масштабируется.

### BitTorrent (2001)

DHT (Distributed Hash Table) + trackers:
```
Info hash → DHT → list of peers with file
```

До сих пор работает. 150+ млн пользователей.

### Bitcoin (2009)

Специализированная P2P сеть для blockchain:
- Gossip protocol для транзакций
- Block propagation
- Peer discovery

---

## Bitcoin Network

### Типы узлов

| Тип | Функции | Ресурсы |
|-----|---------|---------|
| Full Node | Валидация + хранение | ~500 ГБ, постоянный онлайн |
| Pruned Node | Валидация, частичное хранение | ~10 ГБ |
| SPV (Light) | Только headers | ~50 МБ, мобильный |
| Mining Node | Full + майнинг | Full + ASIC |

### Peer Discovery

**Начальная загрузка:**
1. DNS seeds (захардкожены в коде)
2. Подключение к известным узлам
3. Запрос списка peers (`getaddr`)
4. Сохранение в локальную базу

```
DNS: seed.bitcoin.sipa.be → [IP1, IP2, IP3, ...]
```

**После запуска:**
- Периодический `getaddr` к peers
- Обмен `addr` сообщениями
- Blacklist плохих peers

### Структура сообщений

```
┌──────────────────────────────────────┐
│            Bitcoin Message            │
├──────────────┬───────────────────────┤
│ Magic (4B)   │ 0xD9B4BEF9 (mainnet)  │
│ Command (12B)│ "version", "block"... │
│ Length (4B)  │ Payload size          │
│ Checksum (4B)│ sha256(sha256(payload))│
│ Payload      │ Данные сообщения      │
└──────────────┴───────────────────────┘
```

### Основные команды

| Команда | Описание |
|---------|----------|
| version | Handshake, обмен версиями |
| verack | Подтверждение version |
| getblocks | Запрос списка блоков |
| inv | "У меня есть эти объекты" |
| getdata | "Отправь мне эти объекты" |
| block | Полный блок |
| tx | Транзакция |
| ping/pong | Проверка связи |

---

## Gossip Protocol

### Как распространяются транзакции?

```
1. Alice создаёт TX, отправляет своему узлу
2. Узел валидирует, добавляет в mempool
3. Узел отправляет inv(TX) всем peers
4. Peers отвечают getdata(TX) если не видели
5. Узел отправляет TX
6. Peers повторяют шаги 2-5
```

**Время распространения:** ~2-5 секунд до 99% узлов.

### Оптимизации

**Compact Block Relay (BIP 152):**
```
Вместо полного блока (1-2 МБ):
- Short IDs транзакций (6 байт каждый)
- Узел реконструирует из mempool
- Запрашивает только недостающие TX
```

Экономия: ~99% bandwidth.

**Erlay (BIP 330):**
```
Вместо отправки каждому peer:
- Periodic set reconciliation
- Минимальный обмен различиями
```

Экономия: ~80% bandwidth для inv сообщений.

---

## Консенсус и форки

### Longest Chain Rule

При конфликте побеждает цепочка с наибольшим accumulated difficulty:

```
      ┌─[A]─[B]─[C]    ← 3 блока
[Genesis]
      └─[X]─[Y]        ← 2 блока (orphaned)
```

### Типы форков

**Soft Fork:**
- Новые правила **строже** старых
- Старые узлы принимают новые блоки
- Обратная совместимость

Примеры: SegWit, Taproot

**Hard Fork:**
- Новые правила **мягче** или несовместимы
- Требует обновления всех узлов
- Может разделить сеть

Примеры: Bitcoin Cash, Ethereum Classic

### Реорганизация (Reorg)

```
Было:     [A]─[B]─[C]─[D]    ← tip
Стало:    [A]─[B]─[X]─[Y]─[Z] ← new tip

Транзакции из [C],[D] возвращаются в mempool.
```

Глубокие reorg (>6 блоков) — признак атаки.

---

## Безопасность P2P

### Eclipse Attack

Злоумышленник изолирует узел, контролируя все его connections:

```
      [Attacker nodes]
         ╱  │  ╲
        ▼   ▼   ▼
       [Victim node]
         │   │
         ✗   ✗  (нет связи с честной сетью)
```

**Последствия:**
- Victim видит только "злую" цепочку
- Double-spend возможен

**Защита:**
- Минимум 8 outbound connections
- Разнообразие IP ranges (/16)
- Anchor connections (постоянные надёжные peers)

### Sybil Attack

Злоумышленник создаёт множество fake identities:

```
Real network: 1000 nodes
Attacker adds: 5000 fake nodes
Result: 83% сети контролируется атакующим
```

**Защита в Bitcoin:**
- Proof-of-Work делает Sybil дорогим
- Ограничение connections per IP
- Reputation-based peer selection

### DoS Attacks

| Атака | Защита |
|-------|--------|
| Flood с inv | Rate limiting |
| Большие блоки | Checkpointing, size limits |
| CPU-intensive requests | Request throttling |
| Connection exhaustion | Max connections limit |

---

## Наша реализация

Для учебных целей создаём упрощённую сеть:
- TCP сокеты (без шифрования)
- JSON сообщения (вместо бинарного протокола)
- Локальный запуск на разных портах

### Архитектура

```
Node
├── blockchain: Blockchain
├── utxoPool: UTXOPool
├── mempool: List<Transaction>
├── peers: List<PeerConnection>
├── serverSocket: ServerSocket
│
├── start(port): void
├── connectToPeer(host, port): void
├── broadcast(message): void
├── handleMessage(message): void
└── syncBlockchain(): void

Message
├── type: MessageType
├── payload: String (JSON)
└── timestamp: long

MessageType
├── HANDSHAKE
├── NEW_BLOCK
├── NEW_TRANSACTION
├── GET_BLOCKS
├── BLOCKS
└── GET_PEERS
```

### Протокол

**Handshake:**
```json
{
  "type": "HANDSHAKE",
  "payload": {
    "version": 1,
    "height": 100,
    "nodeId": "abc123"
  }
}
```

**New Block:**
```json
{
  "type": "NEW_BLOCK",
  "payload": {
    "index": 101,
    "hash": "0000abc...",
    "previousHash": "0000def...",
    "data": "...",
    "nonce": 12345
  }
}
```

---

## Задание

### Создай пакет network

```
src/main/java/com/study/blockchain/network/
├── Node.java
├── Message.java
├── MessageType.java
└── PeerConnection.java
```

### Реализуй Message и MessageType

```java
public enum MessageType {
    HANDSHAKE,
    NEW_BLOCK,
    NEW_TRANSACTION,
    GET_BLOCKS,
    BLOCKS,
    GET_PEERS,
    PEERS
}

public class Message {
    private MessageType type;
    private String payload;
    private long timestamp;

    // Конструктор, геттеры, toJson(), fromJson()
}
```

### Реализуй PeerConnection

```java
public class PeerConnection {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String peerId;

    public PeerConnection(Socket socket) {
        // Инициализируй streams
    }

    public void send(Message message) {
        // Отправь JSON
    }

    public Message receive() {
        // Прочитай и распарси JSON
    }

    public void close() {
        // Закрой соединение
    }
}
```

### Реализуй Node

```java
public class Node {
    private Blockchain blockchain;
    private UTXOPool utxoPool;
    private List<PeerConnection> peers;
    private ServerSocket serverSocket;
    private String nodeId;
    private boolean running;

    public Node(Blockchain blockchain, UTXOPool utxoPool) {
        // Инициализация
    }

    public void start(int port) {
        // Запусти ServerSocket в отдельном потоке
        // Принимай входящие соединения
    }

    public void connectToPeer(String host, int port) {
        // Создай исходящее соединение
        // Отправь HANDSHAKE
    }

    public void broadcast(Message message) {
        // Отправь всем peers
    }

    public void broadcastBlock(Block block) {
        // Создай NEW_BLOCK сообщение и broadcast
    }

    public void broadcastTransaction(Transaction tx) {
        // Создай NEW_TRANSACTION и broadcast
    }

    private void handleMessage(PeerConnection peer, Message message) {
        switch (message.getType()) {
            case HANDSHAKE:
                // Сохрани информацию о peer
                break;
            case NEW_BLOCK:
                // Валидируй и добавь блок
                break;
            case NEW_TRANSACTION:
                // Добавь в mempool
                break;
            // ...
        }
    }

    public void stop() {
        // Останови сервер, закрой соединения
    }
}
```

---

## Демо: локальная сеть

```java
// Терминал 1
Node node1 = new Node(new Blockchain(), new UTXOPool());
node1.start(8001);

// Терминал 2
Node node2 = new Node(new Blockchain(), new UTXOPool());
node2.start(8002);
node2.connectToPeer("localhost", 8001);

// Node1 майнит блок
Block block = node1.getBlockchain().addBlock("Data");
ProofOfWork pow = new ProofOfWork(4);
pow.mine(block);
node1.broadcastBlock(block);

// Node2 получает блок автоматически!
```

---

## Проверь себя

```bash
./gradlew test --tests NetworkTest
```

Если застрял:
```bash
git checkout solutions -- src/main/java/com/study/blockchain/network/
```

---

## Ключевые термины

| Термин | Описание |
|--------|----------|
| P2P | Peer-to-peer, децентрализованная сеть |
| Gossip Protocol | Распространение информации через peers |
| Full Node | Узел с полной копией blockchain |
| SPV | Simplified Payment Verification (лёгкий узел) |
| Mempool | Пул неподтверждённых транзакций |
| Fork | Разделение цепочки (soft/hard) |
| Reorg | Реорганизация цепочки при более длинной альтернативе |
| Eclipse Attack | Изоляция узла от честной сети |

---

## Поздравляю!

Ты завершил курс и реализовал **полноценный blockchain с нуля**:

```
✅ Block — контейнер данных
✅ Hash — криптографический отпечаток
✅ Blockchain — цепочка блоков
✅ Validation — проверка целостности
✅ Transaction — передача ценности (UTXO модель)
✅ Wallet — криптографические ключи (ECDSA)
✅ Signature — подпись транзакций
✅ Proof-of-Work — децентрализованный консенсус
✅ UTXO Pool — отслеживание балансов
✅ Network — P2P обмен блоками
```

## Что дальше?

### Улучшения нашего проекта

1. **Merkle Tree** — эффективная проверка транзакций в блоке
2. **Persistence** — сохранение blockchain в файл/БД
3. **REST API** — HTTP интерфейс к узлу
4. **Web UI** — визуализация цепочки

### Изучение реальных проектов

1. **Bitcoin Core** — эталонная реализация
2. **Ethereum** — смарт-контракты
3. **Hyperledger Fabric** — enterprise blockchain
4. **Cosmos SDK** — создание своего blockchain

### Книги

- "Mastering Bitcoin" — Andreas Antonopoulos
- "Mastering Ethereum" — Andreas Antonopoulos
- "Programming Bitcoin" — Jimmy Song

### Практика

- Contribute в open source blockchain проекты
- Создай свой токен (ERC-20)
- Напиши смарт-контракт
- Участвуй в hackathons

---

**Удачи в мире blockchain!**
