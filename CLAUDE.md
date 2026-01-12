# Blockchain Study Project

Учебный проект для изучения основ blockchain на Java.

## Структура проекта

```
lessons/           # Теория по урокам (Markdown)
src/main/java/com/study/blockchain/
├── core/          # Block, Blockchain, HashUtil
├── transaction/   # Transaction, TransactionInput, TransactionOutput
├── wallet/        # Wallet (ключи, подписи)
├── mining/        # ProofOfWork
└── network/       # Node, Message, P2P
```

## Технологии

- Java 17+
- Gradle (Kotlin DSL)
- BouncyCastle (криптография)
- Gson (JSON)
- JUnit 5 (тесты)

## Конвенции

### Язык
- Код и JavaDoc: английский
- Комментарии и документация: русский
- Термины (block, hash, nonce, UTXO): английский

### Именование
- Классы: PascalCase (`Block`, `TransactionOutput`)
- Методы и переменные: camelCase (`calculateHash`, `previousHash`)
- Константы: UPPER_SNAKE_CASE (`GENESIS_PREV_HASH`)
- Пакеты: lowercase (`com.study.blockchain.core`)

### Код-стайл
- Отступы: 4 пробела
- Максимальная длина строки: 120 символов
- Фигурные скобки: K&R style (открывающая на той же строке)

## Глоссарий

| Термин | Описание |
|--------|----------|
| Block | Единица данных в цепочке, содержит транзакции и hash |
| Hash | Криптографический отпечаток данных (SHA-256) |
| Blockchain | Цепочка блоков, связанных через previousHash |
| Transaction | Перевод средств от sender к recipient |
| UTXO | Unspent Transaction Output — неизрасходованный выход транзакции |
| Wallet | Кошелёк, хранит ключевую пару (private/public key) |
| Nonce | Число, перебираемое при mining для поиска валидного hash |
| Difficulty | Сложность mining — количество ведущих нулей в hash |
| Proof-of-Work | Алгоритм консенсуса, требующий вычислительной работы |
| Node | Узел сети, хранит копию blockchain |

## Архитектурные решения

1. **UTXO модель** (как Bitcoin) — баланс = сумма неизрасходованных выходов
2. **ECDSA** для подписей — стандарт криптовалют
3. **Longest chain rule** — при конфликте побеждает самая длинная валидная цепочка
4. **Эволюция классов** — классы усложняются с каждым уроком

## Команды

```bash
./gradlew build          # Сборка
./gradlew test           # Тесты
./gradlew run            # Запуск (после настройки main class)
```

## Текущий прогресс

См. `docs/plans/2026-01-12-blockchain-study-design.md` для полного дизайна.
