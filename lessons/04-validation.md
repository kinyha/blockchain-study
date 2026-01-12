# Урок 4: Валидация цепочки

## Введение

Мы построили blockchain. Но как убедиться, что никто не подменил данные? Для этого нужна **валидация**.

## Что проверяем?

### Проверка 1: Hash соответствует данным

Пересчитываем hash и сравниваем с сохранённым:

```java
if (!block.getHash().equals(block.calculateHash())) {
    // Данные изменены!
}
```

### Проверка 2: Связь между блоками

```java
if (!currentBlock.getPreviousHash().equals(previousBlock.getHash())) {
    // Цепочка разорвана!
}
```

### Проверка 3: Genesis блок

```java
if (!genesis.getPreviousHash().equals("0")) {
    // Genesis подделан!
}
```

## Сценарии атак

1. **Изменение данных** → hash не совпадает
2. **Пересчёт hash** → previousHash следующего блока не совпадает
3. **Каскадный пересчёт** → без Proof-of-Work это возможно (урок 8 защитит)

---

## Задание

Вернись к `src/main/java/com/study/blockchain/core/Blockchain.java`.

### Реализуй validateDetailed

```java
public ValidationResult validateDetailed() {
    // Проверяем Genesis
    Block genesis = chain.get(0);
    if (!genesis.getPreviousHash().equals(Block.GENESIS_PREV_HASH)) {
        return ValidationResult.failure(0,
            "Genesis block has invalid previousHash");
    }
    if (!genesis.getHash().equals(genesis.calculateHash())) {
        return ValidationResult.failure(0,
            "Genesis block hash mismatch");
    }

    // Проверяем остальные блоки
    for (int i = 1; i < chain.size(); i++) {
        Block current = chain.get(i);
        Block previous = chain.get(i - 1);

        // Проверка hash
        if (!current.getHash().equals(current.calculateHash())) {
            return ValidationResult.failure(i,
                "Block " + i + " hash mismatch");
        }

        // Проверка связи
        if (!current.getPreviousHash().equals(previous.getHash())) {
            return ValidationResult.failure(i,
                "Block " + i + " chain break");
        }
    }

    return ValidationResult.success();
}
```

### Реализуй isValid

```java
public boolean isValid() {
    return validateDetailed().isValid();
}
```

### Реализуй toString (опционально)

```java
@Override
public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Blockchain{size=").append(size());
    sb.append(", valid=").append(isValid()).append("}");
    return sb.toString();
}
```

---

## Проверь себя

```bash
./gradlew test --tests BlockchainTest --tests ValidationTest
```

Все тесты должны пройти!

Если застрял:
```bash
git checkout solutions -- src/main/java/com/study/blockchain/core/Blockchain.java
```

---

## Итог уроков 1-4

Ты реализовал базовый blockchain:

```
✅ Block        — контейнер данных с hash
✅ HashUtil     — SHA-256 хэширование
✅ Blockchain   — цепочка блоков
✅ Validation   — проверка целостности
```

## Что дальше?

В следующих уроках добавим:
- **Транзакции** — реальные данные
- **Кошельки** — криптографические ключи
- **Proof-of-Work** — защита от каскадного пересчёта
- **Сеть** — P2P обмен блоками
