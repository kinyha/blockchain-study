# Урок 9: UTXO Pool и баланс

## Введение

Мы создали транзакции с inputs и outputs. Но как узнать, какие outputs ещё не потрачены? Как вычислить баланс кошелька?

Для этого нужен **UTXO Pool** — коллекция всех неизрасходованных выходов в системе.

---

## Что такое UTXO?

**UTXO** = Unspent Transaction Output (неизрасходованный выход транзакции).

### Жизненный цикл UTXO

```
1. Coinbase транзакция создаёт UTXO (50 BTC → Alice)
   UTXO Pool: [Alice: 50 BTC]

2. Alice отправляет Bob 30 BTC
   - Input: Alice's UTXO (50 BTC) → УДАЛЯЕТСЯ
   - Output 1: Bob (30 BTC) → ДОБАВЛЯЕТСЯ
   - Output 2: Alice (20 BTC, сдача) → ДОБАВЛЯЕТСЯ
   UTXO Pool: [Bob: 30 BTC, Alice: 20 BTC]

3. Bob отправляет Carol 10 BTC
   - Input: Bob's UTXO (30 BTC) → УДАЛЯЕТСЯ
   - Output 1: Carol (10 BTC) → ДОБАВЛЯЕТСЯ
   - Output 2: Bob (20 BTC, сдача) → ДОБАВЛЯЕТСЯ
   UTXO Pool: [Alice: 20 BTC, Carol: 10 BTC, Bob: 20 BTC]
```

### Ключевое правило

**UTXO тратится ЦЕЛИКОМ.** Нельзя потратить "часть" output — только весь сразу, с возвратом сдачи.

---

## UTXO vs Account модель

### Account модель (Ethereum)

```
Глобальное состояние:
  Alice: 100 ETH
  Bob: 50 ETH

Транзакция: Alice → Bob, 30 ETH

Новое состояние:
  Alice: 70 ETH
  Bob: 80 ETH
```

Плюсы:
- Интуитивно (как банковский счёт)
- Простые смарт-контракты

Минусы:
- Глобальное состояние всех аккаунтов
- Сложнее параллелизация
- Проблема nonce (порядок транзакций)

### UTXO модель (Bitcoin)

```
UTXO Pool:
  tx1:0 → Alice: 100 BTC
  tx2:0 → Bob: 50 BTC

Транзакция:
  Input: tx1:0 (Alice's 100 BTC)
  Outputs: Bob: 30 BTC, Alice: 70 BTC (сдача)

Новый UTXO Pool:
  tx2:0 → Bob: 50 BTC
  tx3:0 → Bob: 30 BTC
  tx3:1 → Alice: 70 BTC
```

Плюсы:
- Простая верификация (проверяем только inputs)
- Лёгкая параллелизация
- Приватность (новые адреса на сдачу)
- Нет проблемы nonce

Минусы:
- Менее интуитивно
- Сложнее смарт-контракты
- "Пыль" (dust) — множество мелких UTXO

---

## UTXO Set в Bitcoin

### Размер и хранение

Bitcoin Core хранит UTXO Set в LevelDB:

```
2024: ~80 млн UTXO, ~5 ГБ на диске
Средний UTXO: ~60 байт
```

Это единственные данные, которые **обязательно** хранить для валидации. Старые блоки можно pruning.

### Структура UTXO в Bitcoin

```
UTXO Key: txid + output_index (36 байт)
UTXO Value:
  - height: высота блока где создан
  - coinbase: флаг (coinbase outputs нельзя тратить 100 блоков)
  - amount: сумма в satoshi
  - scriptPubKey: условие траты
```

### Индексация

Для быстрого поиска UTXO индексируются по:
1. **txid:vout** — основной ключ
2. **address** — для кошельков (опционально)

---

## Баланс кошелька

### Как вычисляется?

**Баланс = сумма всех UTXO, принадлежащих адресу**

```java
public double getBalance(PublicKey owner) {
    double balance = 0;
    for (TransactionOutput utxo : utxoPool.values()) {
        if (utxo.isMine(owner)) {
            balance += utxo.getAmount();
        }
    }
    return balance;
}
```

### Важно понимать

В Bitcoin нет "баланса аккаунта". Есть только UTXO. Кошелёк сканирует blockchain и суммирует свои UTXO.

---

## Выбор UTXO для транзакции (Coin Selection)

### Проблема

Alice хочет отправить 70 BTC. У неё UTXO:
- 50 BTC
- 30 BTC
- 10 BTC

Какие выбрать?

### Стратегии

**1. Largest First**
```
Выбираем самые большие: 50 + 30 = 80
Сдача: 10 BTC
Комиссия: от суммы inputs
```

**2. Smallest First (FIFO)**
```
Выбираем самые маленькие: 10 + 30 + 50 = 90
Сдача: 20 BTC
Больше inputs → выше комиссия
```

**3. Exact Match**
```
Ищем комбинацию без сдачи
Идеально, но редко возможно
```

**4. Branch and Bound (Bitcoin Core)**
```
Оптимизирует: минимум сдачи + минимум inputs
Сложный алгоритм, лучший результат
```

### Проблема пыли (Dust)

Очень маленькие UTXO (< 546 satoshi) невыгодно тратить — комиссия больше суммы. Они "застревают".

---

## Double-Spending и UTXO

### Атака

```
Alice имеет UTXO: 100 BTC

TX1: Alice → Bob (100 BTC)    // отправляем в сеть
TX2: Alice → Carol (100 BTC)  // сразу же отправляем другую

Обе транзакции тратят один UTXO!
```

### Защита

1. **UTXO Pool** — при добавлении транзакции UTXO удаляется
2. **Mempool** — ноды отклоняют транзакции с уже потраченными inputs
3. **Подтверждения** — чем больше блоков поверх, тем безопаснее

### Атака Finney

Майнер тайно майнит блок с TX → себе, затем в магазине платит TX → продавцу, затем публикует свой блок. TX → продавцу отменяется.

Защита: ждать подтверждений (6 для крупных сумм).

---

## UTXO Commitment

### Проблема

Новый узел должен скачать весь blockchain и построить UTXO Set. Это долго.

### Решение: UTXO Snapshot

Периодически сохранять snapshot UTXO Set:
```
Block 700,000:
  UTXO Set Hash: abc123...
  Compressed size: 2 GB
```

Новый узел скачивает snapshot + блоки после него. Быстрее в 10-100 раз.

Bitcoin Core: `assumeutxo` (экспериментально в 2024).

---

## Наша реализация

### UTXOPool

```java
public class UTXOPool {
    // Ключ: outputId, Значение: TransactionOutput
    private Map<String, TransactionOutput> pool;

    public void addUTXO(TransactionOutput output) {
        pool.put(output.getId(), output);
    }

    public void removeUTXO(String outputId) {
        pool.remove(outputId);
    }

    public TransactionOutput getUTXO(String outputId) {
        return pool.get(outputId);
    }

    public boolean contains(String outputId) {
        return pool.containsKey(outputId);
    }

    public double getBalance(PublicKey owner) {
        // Сумма всех UTXO владельца
    }

    public List<TransactionOutput> getUTXOsForAddress(PublicKey owner) {
        // Все UTXO владельца (для coin selection)
    }
}
```

### Обработка транзакции

```java
public boolean processTransaction(Transaction tx, UTXOPool pool) {
    // 1. Проверяем что все inputs существуют в pool
    for (TransactionInput input : tx.getInputs()) {
        if (!pool.contains(input.getTransactionOutputId())) {
            return false; // Double-spend или несуществующий UTXO
        }
    }

    // 2. Проверяем подписи
    if (!tx.verifySignatures()) {
        return false;
    }

    // 3. Проверяем баланс (inputs >= outputs)
    if (tx.getInputsValue() < tx.getOutputsValue()) {
        return false;
    }

    // 4. Удаляем потраченные UTXO
    for (TransactionInput input : tx.getInputs()) {
        pool.removeUTXO(input.getTransactionOutputId());
    }

    // 5. Добавляем новые UTXO
    for (TransactionOutput output : tx.getOutputs()) {
        pool.addUTXO(output);
    }

    return true;
}
```

---

## Задание

### Создай пакет utxo

```
src/main/java/com/study/blockchain/utxo/
└── UTXOPool.java
```

### Реализуй UTXOPool

```java
package com.study.blockchain.utxo;

import com.study.blockchain.transaction.TransactionOutput;
import java.security.PublicKey;
import java.util.*;

public class UTXOPool {

    private Map<String, TransactionOutput> pool;

    public UTXOPool() {
        // Инициализируй HashMap
    }

    public void addUTXO(TransactionOutput output) {
        // Добавь output в pool по его id
    }

    public void removeUTXO(String outputId) {
        // Удали UTXO из pool
    }

    public TransactionOutput getUTXO(String outputId) {
        // Верни UTXO по id
    }

    public boolean contains(String outputId) {
        // Проверь наличие UTXO
    }

    public double getBalance(PublicKey owner) {
        // Сумма amount всех UTXO где isMine(owner) == true
    }

    public List<TransactionOutput> getUTXOsForAddress(PublicKey owner) {
        // Список всех UTXO владельца
    }

    public int size() {
        // Количество UTXO в pool
    }

    public Collection<TransactionOutput> getAllUTXOs() {
        // Все UTXO (для отладки)
    }
}
```

---

## Проверь себя

```bash
./gradlew test --tests UTXOPoolTest
```

Если застрял:
```bash
git checkout solutions -- src/main/java/com/study/blockchain/utxo/
```

---

## Ключевые термины

| Термин | Описание |
|--------|----------|
| UTXO | Unspent Transaction Output — неизрасходованный выход |
| UTXO Pool | Коллекция всех UTXO в системе |
| Coin Selection | Алгоритм выбора UTXO для транзакции |
| Dust | Слишком маленькие UTXO, невыгодные для траты |
| Double-Spend | Попытка потратить один UTXO дважды |
| Balance | Сумма всех UTXO адреса |

---

## Что дальше?

В финальном уроке создадим **P2P сеть** — узлы будут обмениваться блоками и синхронизировать blockchain.
