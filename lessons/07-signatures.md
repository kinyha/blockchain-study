# Урок 7: Цифровые подписи транзакций

## Введение

У нас есть кошелёк с ключами и транзакции с inputs/outputs. Но как доказать, что именно владелец UTXO создал транзакцию?

Ответ — **цифровая подпись**.

---

## Зачем нужна подпись?

### Проблема без подписи

Представь: Alice имеет UTXO на 100 монет. Без подписи любой может создать транзакцию:

```
Input: Alice's UTXO (100 монет)
Output: Злоумышленник (100 монет)
```

И отправить её в сеть. Как узлы узнают, что это не Alice создала транзакцию?

### Решение: криптографическая подпись

Alice подписывает транзакцию своим **приватным ключом**. Узлы проверяют подпись **публичным ключом** Alice (который привязан к UTXO).

```
┌─────────────────────────────────────────────────────────┐
│                    Создание транзакции                   │
│                                                          │
│  1. Alice формирует TX: input → output                  │
│  2. Alice вычисляет hash(TX)                            │
│  3. Alice подписывает: sign(hash, privateKey) → sig     │
│  4. Alice добавляет sig к транзакции                    │
│  5. Alice отправляет TX в сеть                          │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    Проверка узлом                        │
│                                                          │
│  1. Узел получает TX с подписью                         │
│  2. Узел находит UTXO → получает publicKey              │
│  3. Узел вычисляет hash(TX)                             │
│  4. Узел проверяет: verify(hash, sig, publicKey)        │
│  5. Если true → транзакция валидна                      │
└─────────────────────────────────────────────────────────┘
```

---

## Как работает ECDSA подпись

### Математика (упрощённо)

**Подпись:**
1. Выбирается случайное число `k`
2. Вычисляется точка `R = k × G` на кривой
3. Берётся x-координата: `r = R.x mod n`
4. Вычисляется `s = k⁻¹ × (hash + r × privateKey) mod n`
5. Подпись = `(r, s)`

**Проверка:**
1. Вычисляется `w = s⁻¹ mod n`
2. Вычисляются `u1 = hash × w` и `u2 = r × w`
3. Вычисляется точка `P = u1 × G + u2 × publicKey`
4. Если `P.x mod n == r` → подпись валидна

### Почему это безопасно?

Без знания `privateKey` невозможно создать `(r, s)`, который пройдёт проверку. Это следует из сложности задачи дискретного логарифма на эллиптической кривой.

### Важность случайного k

**Критически важно:** `k` должен быть уникальным для каждой подписи!

В 2010 году Sony PlayStation 3 была взломана из-за повторного использования `k`. Хакеры вычислили приватный ключ Sony.

Современные реализации используют **RFC 6979** — детерминированный `k` из hash(privateKey + message). Это исключает ошибки генератора случайных чисел.

---

## Что подписывается в Bitcoin?

### SIGHASH флаги

Bitcoin позволяет подписывать **разные части** транзакции:

| Флаг | Что подписывается |
|------|-------------------|
| SIGHASH_ALL | Все inputs и outputs |
| SIGHASH_NONE | Все inputs, outputs можно менять |
| SIGHASH_SINGLE | Все inputs + один output |
| SIGHASH_ANYONECANPAY | Только свой input |

**SIGHASH_ALL** (по умолчанию) — защищает всю транзакцию.

**SIGHASH_ANYONECANPAY** — используется для краудфандинга: каждый добавляет свой input, output общий.

### Что НЕ подписывается

Сами подписи не входят в подписываемые данные (иначе курица и яйцо). Также не подписывается txid, т.к. он вычисляется после.

---

## Malleability: историческая проблема

### Что это?

До 2017 года в Bitcoin можно было **изменить подпись** транзакции, не инвалидируя её. Это меняло txid, хотя транзакция оставалась валидной.

```
Оригинальная TX: txid = abc123...
Изменённая TX:   txid = def456... (та же транзакция!)
```

### Почему это плохо?

- Биржи отслеживали транзакции по txid
- Злоумышленник изменял подпись, txid менялся
- Биржа думала, что транзакция не прошла
- Злоумышленник просил повторную отправку

Mt. Gox потеряла миллионы из-за этой атаки.

### Как починили?

**SegWit (Segregated Witness)** — подписи вынесены в отдельную структуру и не влияют на txid.

```
До SegWit:  txid = hash(inputs + outputs + signatures)
SegWit:     txid = hash(inputs + outputs)
            wtxid = hash(inputs + outputs + witness)
```

---

## Мультиподпись (Multisig)

### Концепция M-of-N

Требуется M подписей из N возможных ключей:

```
2-of-3 Multisig:
  Ключи: Alice, Bob, Carol
  Требуется: любые 2 подписи из 3
```

### Применения

1. **Корпоративный контроль** — 3-of-5 руководителей
2. **Escrow** — 2-of-3 (покупатель, продавец, арбитр)
3. **Backup** — 2-of-2 (основной ключ + backup)
4. **Наследование** — таймлок + мультисиг

### В Bitcoin

```
OP_2 <PubKey1> <PubKey2> <PubKey3> OP_3 OP_CHECKMULTISIG
```

Адреса multisig начинаются с `3...` (P2SH).

---

## Schnorr подписи: будущее Bitcoin

### Преимущества над ECDSA

1. **Агрегация подписей** — множество подписей объединяются в одну
2. **Меньший размер** — экономия места в блоке
3. **Batch verification** — быстрее проверять много подписей
4. **Доказуемая безопасность** — математически строже

### Taproot (Bitcoin 2021)

Использует Schnorr для:
- Обычные транзакции выглядят одинаково
- Multisig неотличим от single-sig (приватность)
- Сложные контракты скрыты до исполнения

---

## Наша реализация

### Что подписываем?

Для простоты подписываем **весь input целиком**:
```java
String dataToSign = input.getTransactionOutputId();
```

В реальном Bitcoin подписывается сериализованная транзакция с SIGHASH.

### Обновления классов

**TransactionInput** — добавляем подпись:
```java
private byte[] signature;

public void sign(PrivateKey key) {
    // Подписываем transactionOutputId
}
```

**Transaction** — проверка всех подписей:
```java
public boolean verifySignatures() {
    // Для каждого input проверяем подпись
    // publicKey берём из UTXO.recipientPublicKey
}
```

---

## Задание

### Обнови TransactionInput

```java
public void sign(PrivateKey privateKey) {
    // Подпиши transactionOutputId приватным ключом
    // Signature sig = Signature.getInstance("SHA256withECDSA");
    // sig.initSign(privateKey);
    // sig.update(transactionOutputId.getBytes());
    // this.signature = sig.sign();
}

public boolean verifySignature(PublicKey publicKey) {
    // Проверь подпись публичным ключом
}
```

### Обнови Transaction

```java
public boolean verifySignatures() {
    for (TransactionInput input : inputs) {
        if (input.getUTXO() == null) return false;

        PublicKey publicKey = input.getUTXO().getRecipientPublicKey();
        if (!input.verifySignature(publicKey)) {
            return false;
        }
    }
    return true;
}

public void signAllInputs(PrivateKey privateKey) {
    for (TransactionInput input : inputs) {
        input.sign(privateKey);
    }
}
```

---

## Проверь себя

```bash
./gradlew test --tests SignatureTest
```

Если застрял:
```bash
git checkout solutions -- src/main/java/com/study/blockchain/transaction/
```

---

## Ключевые термины

| Термин | Описание |
|--------|----------|
| Digital Signature | Криптографическое доказательство авторства |
| SIGHASH | Флаг, определяющий что подписывается |
| Malleability | Возможность изменить подпись без инвалидации |
| SegWit | Segregated Witness — решение malleability |
| Multisig | Транзакция, требующая M-of-N подписей |
| Schnorr | Улучшенный алгоритм подписи (Taproot) |

---

## Что дальше?

Транзакции подписаны, но кто решает, какие транзакции попадут в блок? В следующем уроке — **Proof-of-Work**: майнинг, сложность, и гонка за право создать блок.
