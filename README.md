# Blockchain Study

Учебный проект для изучения основ blockchain на Java.

## Как учиться

1. **Читай теорию** в `lessons/XX-тема.md`
2. **Пиши код** в `src/main/java/...` (там заготовки с TODO)
3. **Проверяй тестами**: `./gradlew test --tests ИмяТеста`
4. **Если застрял** — смотри ветку `solutions`

## Уроки

| # | Тема | Файл теории | Что реализуешь |
|---|------|-------------|----------------|
| 1 | Block | [01-block.md](lessons/01-block.md) | `Block.java` |
| 2 | Hashing | [02-hashing.md](lessons/02-hashing.md) | `HashUtil.java` + `Block.calculateHash()` |
| 3 | Blockchain | [03-blockchain.md](lessons/03-blockchain.md) | `Blockchain.java` |
| 4 | Validation | [04-validation.md](lessons/04-validation.md) | `Blockchain.isValid()` |
| 5 | Transactions | [05-transactions.md](lessons/05-transactions.md) | `Transaction.java`, `TransactionInput.java`, `TransactionOutput.java` |
| 6 | Wallets | [06-wallets.md](lessons/06-wallets.md) | `Wallet.java` |
| 7 | Signatures | [07-signatures.md](lessons/07-signatures.md) | `TransactionInput.sign()`, `Transaction.verifySignatures()` |
| 8 | Proof-of-Work | [08-proof-of-work.md](lessons/08-proof-of-work.md) | `ProofOfWork.java`, `Block.nonce` |

## Быстрый старт

```bash
# Клонируй репозиторий
git clone https://github.com/kinyha/blockchain-study.git
cd blockchain-study

# Открой урок 1
cat lessons/01-block.md

# Реализуй Block.java, затем проверь
./gradlew test --tests BlockTest
```

## Структура проекта

```
lessons/                       # Теория (читай по порядку)
src/main/java/.../core/        # Block, Blockchain, HashUtil
src/main/java/.../transaction/ # Transaction, Input, Output
src/main/java/.../wallet/      # Wallet (криптографические ключи)
src/main/java/.../mining/      # ProofOfWork (майнинг)
src/test/java/...              # Тесты (запускай для проверки)
```

## Если застрял

Посмотри готовое решение:

```bash
# Весь файл
git show solutions:src/main/java/com/study/blockchain/core/Block.java

# Или скопируй в свой main
git checkout solutions -- src/main/java/com/study/blockchain/core/Block.java
```

## Технологии

- Java 17+
- Gradle
- JUnit 5
