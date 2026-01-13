# Blockchain Study

Учебный блокчейн на Java с GUI. Майнинг, транзакции, P2P сеть — всё работает.

## Быстрый запуск

```bash
# Запуск ноды на порту 8001
./gradlew run --args="--port=8001"

# Вторая нода на 8002, подключается к первой
./gradlew run --args="--port=8002 --connect=localhost:8001"
```

**Требования:** Java 21+, Gradle

## Что умеет

- **Кошелёк** — генерация ECDSA ключей, баланс через UTXO
- **Майнинг** — Proof-of-Work с визуализацией перебора nonce
- **Транзакции** — отправка монет между кошельками с подписями
- **P2P сеть** — синхронизация блоков и транзакций между нодами
- **GUI** — JavaFX интерфейс для всего вышеперечисленного

## Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                        Node (P2P)                           │
│  ┌─────────┐  ┌──────────┐  ┌─────────┐  ┌───────────────┐  │
│  │ Wallet  │  │Blockchain│  │UTXOPool │  │   Mempool     │  │
│  │ (keys)  │  │ (blocks) │  │(balance)│  │(pending txs)  │  │
│  └─────────┘  └──────────┘  └─────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────────┘
         │              │            │              │
         ▼              ▼            ▼              ▼
    Подписи        Валидация    Coin Selection   Broadcast
    ECDSA          хешей        для транзакций   peers
```

## Структура проекта

```
src/main/java/com/study/blockchain/
├── core/          # Block, Blockchain, HashUtil (SHA-256)
├── transaction/   # Transaction, Input, Output
├── wallet/        # Wallet (ECDSA ключи)
├── mining/        # ProofOfWork
├── utxo/          # UTXOPool (UTXO модель как в Bitcoin)
├── network/       # Node, Message, PeerConnection (TCP сокеты)
└── ui/            # JavaFX GUI

lessons/           # Теория по каждой теме (10 уроков)
```

## Уроки

| # | Тема | Что делаем |
|---|------|------------|
| 1 | Block | Структура блока: index, timestamp, data, hash |
| 2 | Hashing | SHA-256, связь блоков через previousHash |
| 3 | Blockchain | Цепочка блоков, genesis block |
| 4 | Validation | Проверка целостности цепочки |
| 5 | Transactions | Inputs, Outputs, передача ценности |
| 6 | Wallets | ECDSA ключи, публичный/приватный |
| 7 | Signatures | Подпись и верификация транзакций |
| 8 | Proof-of-Work | Майнинг, nonce, difficulty |
| 9 | UTXO Pool | Баланс = сумма неизрасходованных выходов |
| 10 | Network | P2P, синхронизация, mempool |

```bash
# Читай теорию
cat lessons/01-block.md

# Запускай тесты
./gradlew test --tests BlockTest
```

## Команды

```bash
# Сборка
./gradlew build

# Все тесты (127 штук)
./gradlew test

# Конкретный тест
./gradlew test --tests WalletTest

# Запуск GUI
./gradlew run --args="--port=8001"
```

## IntelliJ IDEA

**Gradle конфигурация:**
- Run: `run`
- Arguments: `--args="--port=8001"`

**Для второй ноды:** `--args="--port=8002 --connect=localhost:8001"`

## Как работает

### UTXO модель

Баланс — не число в базе, а сумма "непотраченных выходов":

```
Блок 1: coinbase → Alice получает 50 BTC (UTXO #1)
Блок 2: Alice → Bob 30 BTC
         - Input: тратим UTXO #1 (50 BTC)
         - Output #1: Bob получает 30 BTC
         - Output #2: Alice получает 20 BTC (сдача)

Alice balance: 20 BTC (один UTXO)
Bob balance: 30 BTC (один UTXO)
```

### Proof-of-Work

Ищем nonce, чтобы hash начинался с N нулей:

```
difficulty = 5 → hash должен начинаться с "00000..."
nonce = 0 → hash = "a1b2c3..." ✗
nonce = 1 → hash = "ff00ab..." ✗
...
nonce = 847291 → hash = "00000d7..." ✓
```

### P2P сеть

```
Node A (8001) ←──TCP──→ Node B (8002)
     │                       │
     └── broadcast block ────┘
     └── broadcast tx ───────┘
     └── sync chain ─────────┘
```

## Глоссарий

| Термин | Что это |
|--------|---------|
| Block | Контейнер с данными и хешем |
| Hash | SHA-256 отпечаток данных |
| Nonce | Число для PoW майнинга |
| UTXO | Unspent Transaction Output |
| Coinbase | Первая транзакция блока (награда майнеру) |
| Mempool | Очередь неподтверждённых транзакций |
| Difficulty | Сколько нулей нужно в начале хеша |

## Зависимости

- **BouncyCastle** — криптография (ECDSA, SHA-256)
- **Gson** — JSON сериализация для P2P
- **JavaFX** — GUI
- **JUnit 5** — тесты

## License

MIT
