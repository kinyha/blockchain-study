# Дизайн: Учебный Blockchain на Java

**Дата:** 2026-01-12
**Статус:** Утверждён

## Цель проекта

Учебный проект для изучения основ blockchain с практикой на Java. Пошаговые уроки, каждый из которых объясняет одну концепцию и сопровождается кодом.

## Формат обучения

- **10 уроков** с последовательным наращиванием сложности
- Каждый урок: теория (Markdown) + код (Java) + тесты (JUnit 5)
- Текст на русском, термины на английском
- Теория в `lessons/*.md`, детали в JavaDoc

## План уроков

| # | Тема | Описание | Классы |
|---|------|----------|--------|
| 1 | Block | Что такое блок, структура данных | `Block` |
| 2 | Hashing | SHA-256, зачем нужен hash | `HashUtil` |
| 3 | Blockchain | Цепочка блоков, связь через previousHash | `Blockchain` |
| 4 | Validation | Проверка целостности цепочки | `Blockchain.isValid()` |
| 5 | Transactions | Модель транзакций, inputs/outputs | `Transaction` |
| 6 | Wallets | Генерация ключей ECDSA, адреса | `Wallet` |
| 7 | Signatures | Цифровая подпись транзакций | `Transaction.sign()` |
| 8 | Proof-of-Work | Mining, nonce, difficulty | `ProofOfWork` |
| 9 | UTXO | Unspent outputs, подсчёт баланса | `UTXOPool` |
| 10 | Network | P2P сеть, синхронизация цепочки | `Node`, `Message` |

## Структура проекта

```
block-chain-study/
├── CLAUDE.md                  # Контекст проекта для Claude Code
├── README.md                  # Описание и инструкции
├── docs/
│   └── plans/                 # Дизайн-документы
├── lessons/
│   ├── 01-block.md
│   ├── 02-hashing.md
│   └── ...
├── src/
│   ├── main/java/com/study/blockchain/
│   │   ├── core/              # Block, Blockchain, HashUtil
│   │   ├── transaction/       # Transaction, TransactionInput, TransactionOutput
│   │   ├── wallet/            # Wallet
│   │   ├── mining/            # ProofOfWork
│   │   └── network/           # Node, Message, PeerConnection
│   └── test/java/...
└── build.gradle.kts
```

## Архитектура

### Core (уроки 1-4)

```
Blockchain
├── chain: List<Block>
├── addBlock(data): void
├── isValid(): boolean
└── getLatestBlock(): Block

Block
├── index: int
├── timestamp: long
├── data: String → List<Transaction>
├── previousHash: String
├── hash: String
├── nonce: long (урок 8)
└── calculateHash(): String

HashUtil
└── sha256(input): String
```

### Транзакции и кошельки (уроки 5-7, 9)

```
Wallet
├── privateKey: PrivateKey
├── publicKey: PublicKey
├── getAddress(): String
└── sign(data): byte[]

Transaction
├── transactionId: String
├── sender: PublicKey
├── recipient: PublicKey
├── amount: double
├── signature: byte[]
├── inputs: List<TransactionInput>
├── outputs: List<TransactionOutput>
├── calculateHash(): String
└── verifySignature(): boolean

TransactionInput
└── outputId: String (ссылка на UTXO)

TransactionOutput
├── id: String
├── recipient: PublicKey
├── amount: double
└── isMine(publicKey): boolean
```

### Mining (урок 8)

```
ProofOfWork
├── difficulty: int
├── mine(block): Block
└── isValidProof(block): boolean
```

**Алгоритм:**
1. Взять данные блока + nonce
2. Вычислить hash
3. Если hash начинается с N нулей → готово
4. Иначе nonce++ и повторить

### Сеть (урок 10)

```
Node
├── blockchain: Blockchain
├── peers: List<PeerConnection>
├── start(port): void
├── connectToPeer(host, port): void
└── broadcast(message): void

Message
├── type: MessageType (BLOCK, CHAIN, TRANSACTION)
└── payload: String (JSON)
```

**Правила:**
- Longest chain rule — принимаем самую длинную валидную цепочку
- Broadcast новых блоков всем peers
- Простые сокеты, локальный запуск на разных портах

## Технологии

- **Java 17+**
- **Gradle** (Kotlin DSL)
- **BouncyCastle** — криптография (ECDSA, SHA-256)
- **Gson** — сериализация в JSON
- **JUnit 5** — тестирование

## Принятые решения

1. **Эволюция классов** — классы усложняются с каждым уроком (Block получает nonce в уроке 8)
2. **UTXO модель** — как в Bitcoin, не account-based как в Ethereum
3. **Простая сеть** — сокеты без фреймворков, peers задаются вручную
4. **Difficulty 4-5** — для учебных целей, mining за секунды
