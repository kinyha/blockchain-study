# Урок 5: Транзакции

## Введение

До сих пор наши блоки хранили просто строку `data`. Но в реальном blockchain блоки содержат **транзакции** — записи о передаче ценности от одного участника другому.

В этом уроке мы погрузимся в модель транзакций и поймём, почему она устроена именно так.

---

## Краткая история: от бартера к криптовалютам

### Бартер и его проблемы

Тысячелетия назад люди обменивались напрямую: зерно на мясо, шкуры на инструменты. Это называется **бартер**.

Проблема бартера — **двойное совпадение желаний**: чтобы обмен состоялся, нужно найти человека, который хочет именно то, что у тебя есть, и имеет именно то, что нужно тебе.

### Появление денег

Деньги решили эту проблему, став **универсальным посредником**:
- Ракушки, соль, золото → монеты → банкноты
- Любой товар можно оценить в деньгах
- Деньги можно хранить и передавать

### Банки и двойные записи

С появлением банков транзакции стали **записями в книгах**:

```
Книга банка:
┌───────────────┬────────────┬───────────┐
│ Дата          │ Описание   │ Сумма     │
├───────────────┼────────────┼───────────┤
│ 01.01.2025    │ Иван → Пётр│ +1000 ₽   │
│ 02.01.2025    │ Пётр → Анна│ +500 ₽    │
└───────────────┴────────────┴───────────┘
```

Проблема: нужно **доверять банку**. Банк может:
- Изменить записи
- Заблокировать счёт
- Обанкротиться

### Цифровые платежи

PayPal, VISA, банковские переводы — всё это централизованные системы. Быстро, удобно, но:
- Посредники берут комиссию (2-5%)
- Международные переводы идут днями
- Можно заблокировать или отменить

### Bitcoin: деньги без посредников

В 2008 году Сатоши Накамото предложил систему, где:
- **Нет центрального органа** — сеть из тысяч узлов
- **Транзакции необратимы** — записаны в blockchain навсегда
- **Криптография вместо доверия** — подписи доказывают владение

---

## Две модели транзакций в криптовалютах

### Модель 1: Account-based (Ethereum)

Работает как банковский счёт:

```
Состояние:
  Alice: 100 ETH
  Bob:   50 ETH

Транзакция: Alice → Bob, 30 ETH

Новое состояние:
  Alice: 70 ETH
  Bob:   80 ETH
```

**Плюсы:**
- Интуитивно понятно
- Простые смарт-контракты

**Минусы:**
- Нужно отслеживать глобальное состояние всех аккаунтов
- Сложнее параллелизация

### Модель 2: UTXO (Bitcoin, наш проект)

**UTXO** = Unspent Transaction Output (неизрасходованный выход транзакции).

Нет понятия "баланс счёта". Есть только **выходы транзакций**, которые можно потратить.

```
┌─────────────────────────────────────────────────────┐
│                  Transaction TX1                     │
│                                                      │
│  Inputs: (ничего — это coinbase)                    │
│                                                      │
│  Outputs:                                            │
│    [0] 50 BTC → Alice                               │
└─────────────────────────────────────────────────────┘

Теперь у Alice есть UTXO на 50 BTC.

┌─────────────────────────────────────────────────────┐
│                  Transaction TX2                     │
│                                                      │
│  Inputs:                                             │
│    ← TX1.output[0] (50 BTC от Alice)                │
│                                                      │
│  Outputs:                                            │
│    [0] 30 BTC → Bob                                 │
│    [1] 20 BTC → Alice (сдача!)                      │
└─────────────────────────────────────────────────────┘
```

**Важно:** в UTXO модели нельзя потратить "часть" выхода. Выход тратится **целиком**, а излишек возвращается как сдача.

Аналогия с наличными:
- У тебя купюра 1000 ₽ (это UTXO)
- Покупаешь кофе за 300 ₽
- Отдаёшь 1000 ₽, получаешь 700 ₽ сдачи
- Теперь у тебя UTXO на 700 ₽

**Плюсы UTXO:**
- Простая верификация (проверяем только входы транзакции)
- Лёгкая параллелизация
- Приватность (новые адреса на сдачу)

**Минусы:**
- Менее интуитивно
- Сложнее для смарт-контрактов

### Почему мы используем UTXO?

1. Это модель Bitcoin — самой надёжной криптовалюты
2. Проще понять, как работает верификация
3. Явная связь между транзакциями через inputs/outputs

---

## Анатомия транзакции Bitcoin

Реальная транзакция Bitcoin содержит:

```
Transaction
├── version: 1
├── inputs[]:
│   ├── prev_txid: "abc123..."     # ID предыдущей транзакции
│   ├── output_index: 0             # Какой выход тратим
│   ├── scriptSig: "3045..."        # Подпись владельца
│   └── sequence: 0xFFFFFFFF
├── outputs[]:
│   ├── value: 50000000             # Сумма в satoshi (0.5 BTC)
│   ├── scriptPubKey: "76a914..."   # Условие траты (обычно: владеть ключом)
├── locktime: 0
└── txid: sha256(sha256(raw_tx))    # ID этой транзакции
```

### Что такое scriptPubKey и scriptSig?

Bitcoin использует скриптовый язык для условий траты:

**scriptPubKey** (в output) — "замок":
```
OP_DUP OP_HASH160 <pubKeyHash> OP_EQUALVERIFY OP_CHECKSIG
```
Перевод: "Чтобы потратить, предъяви публичный ключ с этим хэшем и валидную подпись."

**scriptSig** (в input) — "ключ":
```
<signature> <publicKey>
```

Мы упростим это до проверки ECDSA подписи (в уроке 7).

### Coinbase транзакция

Первая транзакция в каждом блоке — **coinbase**:
- Не имеет inputs (монеты создаются "из воздуха")
- Награда майнеру за найденный блок
- В Bitcoin: изначально 50 BTC, халвинг каждые 210,000 блоков

```
Block #0 (Genesis):
  Coinbase → 50 BTC → Satoshi's address

Block #210,000:
  Coinbase → 25 BTC → Miner's address

Block #840,000 (2024):
  Coinbase → 3.125 BTC → Miner's address
```

---

## Комиссии (fees)

В реальных системах:

```
Inputs total:  1.0 BTC
Outputs total: 0.999 BTC
──────────────────────
Fee:           0.001 BTC (идёт майнеру)
```

Комиссия = сумма входов - сумма выходов.

Майнеры приоритизируют транзакции с большей комиссией.

---

## Наша упрощённая модель

Для обучения мы упрощаем:

| Bitcoin | Наш проект |
|---------|-----------|
| satoshi (целые числа) | double (для простоты) |
| Script language | Простая ECDSA подпись |
| Сложный txid | SHA-256 от данных |
| Merkle tree транзакций | Список в блоке |

Структура наших классов:

```
Transaction
├── transactionId: String          # Уникальный ID (hash)
├── inputs: List<TransactionInput>  # Что тратим
├── outputs: List<TransactionOutput># Кому отправляем
└── calculateHash(): String

TransactionInput
├── transactionOutputId: String    # Ссылка на UTXO
├── UTXO: TransactionOutput        # Сам UTXO (для удобства)
└── signature: byte[]              # Подпись (урок 7)

TransactionOutput
├── id: String                     # Уникальный ID
├── recipientPublicKey: PublicKey  # Кому принадлежит
├── amount: double                 # Сумма
└── isMine(publicKey): boolean     # Проверка владения
```

---

## Жизненный цикл транзакции

1. **Создание**: Alice хочет отправить Bob 10 монет
2. **Сбор inputs**: Находим UTXO Alice на нужную сумму
3. **Формирование outputs**: Bob получает 10, Alice получает сдачу
4. **Подпись**: Alice подписывает каждый input своим приватным ключом
5. **Broadcast**: Транзакция отправляется в сеть
6. **Верификация**: Узлы проверяют подписи и наличие UTXO
7. **Включение в блок**: Майнер добавляет в следующий блок
8. **UTXO обновление**: Inputs удаляются, outputs добавляются в пул

---

## Почему транзакции нельзя подделать?

1. **Криптографическая подпись** — только владелец приватного ключа может потратить UTXO
2. **Ссылки на предыдущие транзакции** — нельзя потратить несуществующий output
3. **Консенсус сети** — тысячи узлов проверяют каждую транзакцию
4. **Необратимость blockchain** — после включения в блок изменить нельзя

---

## Задание

### Создай пакет transaction

Структура:
```
src/main/java/com/study/blockchain/transaction/
├── Transaction.java
├── TransactionInput.java
└── TransactionOutput.java
```

### Реализуй TransactionOutput

```java
package com.study.blockchain.transaction;

import java.security.PublicKey;

public class TransactionOutput {

    private String id;
    private PublicKey recipientPublicKey;
    private double amount;
    private String parentTransactionId;

    public TransactionOutput(PublicKey recipientPublicKey, double amount,
                            String parentTransactionId) {
        this.recipientPublicKey = recipientPublicKey;
        this.amount = amount;
        this.parentTransactionId = parentTransactionId;
        this.id = calculateId();
    }

    private String calculateId() {
        // Hash от: publicKey + amount + parentTransactionId
    }

    public boolean isMine(PublicKey publicKey) {
        // Сравни publicKey с recipientPublicKey
    }

    // Геттеры...
}
```

### Реализуй TransactionInput

```java
package com.study.blockchain.transaction;

public class TransactionInput {

    private String transactionOutputId;
    private TransactionOutput UTXO;
    private byte[] signature;  // Пока null, реализуем в уроке 7

    public TransactionInput(String transactionOutputId) {
        this.transactionOutputId = transactionOutputId;
    }

    // Геттеры и сеттеры...
}
```

### Реализуй Transaction

```java
package com.study.blockchain.transaction;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class Transaction {

    private String transactionId;
    private List<TransactionInput> inputs;
    private List<TransactionOutput> outputs;

    public Transaction() {
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
    }

    public String calculateHash() {
        // Собери строку из inputs и outputs, верни sha256
    }

    public double getInputsValue() {
        // Сумма всех UTXO из inputs
    }

    public double getOutputsValue() {
        // Сумма всех outputs
    }

    public void addInput(TransactionInput input) { ... }
    public void addOutput(TransactionOutput output) { ... }

    // Геттеры...
}
```

---

## Проверь себя

```bash
./gradlew test --tests TransactionTest
```

Если застрял:
```bash
git checkout solutions -- src/main/java/com/study/blockchain/transaction/
```

---

## Ключевые термины

| Термин | Описание |
|--------|----------|
| Transaction | Запись о передаче ценности |
| UTXO | Unspent Transaction Output — неизрасходованный выход |
| Input | Ссылка на UTXO, который тратится |
| Output | Новый UTXO, который создаётся |
| Coinbase | Первая транзакция в блоке, создающая новые монеты |
| Fee | Комиссия майнеру (inputs - outputs) |

---

## Что дальше?

В следующем уроке создадим **кошелёк (Wallet)** с криптографическими ключами. Это позволит подписывать транзакции и доказывать владение UTXO.
