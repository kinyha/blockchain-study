# Урок 4: Валидация цепочки

## Введение

Мы построили blockchain — цепочку блоков, связанных hash-ами. Но как убедиться, что никто не подменил данные? Для этого нужна **валидация** (validation) — проверка целостности цепочки.

## Зачем нужна валидация?

Представь, что blockchain — это бухгалтерская книга, которую ведут сотни бухгалтеров одновременно. Каждый имеет свою копию. Как убедиться, что все копии одинаковые и никто не приписал себе лишний миллион?

**Валидация** позволяет:
1. Обнаружить подделку данных
2. Проверить целостность цепочки
3. Выявить повреждённые блоки
4. Отклонить невалидные цепочки от других узлов

## Что проверяем при валидации?

### Проверка 1: Hash соответствует данным

Каждый блок хранит свой hash. При валидации мы **пересчитываем** hash из данных блока и сравниваем с сохранённым.

```
Сохранённый hash:    abc123...
Пересчитанный hash:  abc123...  ✅ Совпадает — блок не изменён

Сохранённый hash:    abc123...
Пересчитанный hash:  xyz789...  ❌ Не совпадает — данные изменены!
```

```java
// Проверка hash блока
if (!block.getHash().equals(block.calculateHash())) {
    return false;  // Блок подделан!
}
```

### Проверка 2: Связь между блоками

Каждый блок хранит `previousHash` — hash предыдущего блока. Проверяем, что эта ссылка корректна.

```
Block 1.hash:        abc123...
Block 2.previousHash: abc123...  ✅ Совпадает — связь корректна

Block 1.hash:        abc123...
Block 2.previousHash: xyz789...  ❌ Не совпадает — цепочка разорвана!
```

```java
// Проверка связи между блоками
if (!currentBlock.getPreviousHash().equals(previousBlock.getHash())) {
    return false;  // Цепочка разорвана!
}
```

### Проверка 3: Genesis блок

Genesis блок — особенный. У него:
- `index = 0`
- `previousHash = "0"` (или другое фиксированное значение)

```java
Block genesis = chain.get(0);
if (!genesis.getPreviousHash().equals("0")) {
    return false;  // Genesis подделан!
}
```

## Алгоритм полной валидации

```
┌─────────────────────────────────────────────────────────────┐
│                    АЛГОРИТМ ВАЛИДАЦИИ                        │
├─────────────────────────────────────────────────────────────┤
│ 1. Проверить Genesis блок:                                  │
│    - previousHash == "0"                                    │
│    - hash == calculateHash()                                │
│                                                             │
│ 2. Для каждого блока (начиная с index=1):                   │
│    a) Проверить hash блока:                                 │
│       - block.hash == block.calculateHash()                 │
│    b) Проверить связь с предыдущим:                         │
│       - block.previousHash == previousBlock.hash            │
│                                                             │
│ 3. Если все проверки пройдены → цепочка ВАЛИДНА             │
│    Если хоть одна не пройдена → цепочка НЕВАЛИДНА           │
└─────────────────────────────────────────────────────────────┘
```

## Реализация в Java

```java
public boolean isValid() {
    // Шаг 1: Проверяем Genesis
    Block genesis = chain.get(0);
    if (!genesis.getPreviousHash().equals(Block.GENESIS_PREV_HASH)) {
        return false;
    }
    if (!genesis.getHash().equals(genesis.calculateHash())) {
        return false;
    }

    // Шаг 2: Проверяем остальные блоки
    for (int i = 1; i < chain.size(); i++) {
        Block current = chain.get(i);
        Block previous = chain.get(i - 1);

        // Проверка 2a: hash соответствует данным
        if (!current.getHash().equals(current.calculateHash())) {
            return false;
        }

        // Проверка 2b: связь с предыдущим блоком
        if (!current.getPreviousHash().equals(previous.getHash())) {
            return false;
        }
    }

    return true;  // Все проверки пройдены
}
```

## Сценарии атак и их обнаружение

### Атака 1: Изменение данных в блоке

```
До атаки:
[Genesis] → [Block 1: "Alice→Bob: 100"] → [Block 2]
              hash: ABC123

После атаки:
[Genesis] → [Block 1: "Alice→Hacker: 100"] → [Block 2]
              hash: ABC123 (старый, не пересчитан!)

Валидация:
  calculateHash() = XYZ789
  block.hash = ABC123
  XYZ789 ≠ ABC123 → ❌ ОБНАРУЖЕНО!
```

### Атака 2: Пересчёт hash после изменения данных

Умный злоумышленник пересчитал hash:

```
После атаки (с пересчётом):
[Genesis] → [Block 1: "Alice→Hacker: 100"] → [Block 2]
              hash: XYZ789 (новый)           prevHash: ABC123 (старый!)

Валидация:
  Block 2.previousHash = ABC123
  Block 1.hash = XYZ789
  ABC123 ≠ XYZ789 → ❌ ОБНАРУЖЕНО!
```

### Атака 3: Каскадный пересчёт всех hash-ей

Очень умный злоумышленник пересчитал все hash-и:

```
[Genesis] → [Block 1*] → [Block 2*] → [Block 3*]
              ↑ всё пересчитано

Валидация:
  Всё совпадает... → ✅ ПРОШЛА?!
```

**Но!** В реальном blockchain с Proof-of-Work каждый пересчёт требует огромных вычислений (урок 8). Кроме того, в распределённой сети другие узлы имеют оригинальную цепочку и отвергнут подделку.

## Расширенная валидация

### Информативный результат

Вместо простого `boolean` можно возвращать детальную информацию:

```java
public class ValidationResult {
    private final boolean valid;
    private final int invalidBlockIndex;
    private final String errorMessage;

    // getters, constructors...
}

public ValidationResult validate() {
    // ... проверки ...
    if (!currentBlock.getHash().equals(currentBlock.calculateHash())) {
        return new ValidationResult(
            false,
            i,
            "Block " + i + ": hash mismatch"
        );
    }
    // ...
}
```

### Валидация отдельного блока

```java
public boolean isValidBlock(Block block, Block previousBlock) {
    // Проверяем hash
    if (!block.getHash().equals(block.calculateHash())) {
        return false;
    }

    // Проверяем связь (если не Genesis)
    if (previousBlock != null) {
        if (!block.getPreviousHash().equals(previousBlock.getHash())) {
            return false;
        }
    }

    return true;
}
```

## Когда выполнять валидацию?

1. **При получении нового блока** — перед добавлением в цепочку
2. **При синхронизации с другим узлом** — перед принятием чужой цепочки
3. **Периодически** — для обнаружения повреждений (опционально)
4. **При запуске узла** — загрузка blockchain с диска

## Практические задания

1. Создай blockchain с 5 блоками
2. Убедись, что `isValid()` возвращает `true`
3. Вручную измени данные одного блока (через reflection или добавь setter)
4. Проверь, что `isValid()` теперь возвращает `false`

## Ключевые термины

| Термин | Описание |
|--------|----------|
| Validation | Проверка целостности blockchain |
| Integrity | Целостность — данные не изменены |
| Hash Mismatch | Несовпадение hash — признак подделки |
| Chain Break | Разрыв цепочки — previousHash не совпадает |

## Что дальше?

Мы создали базовый blockchain с валидацией. В следующих уроках добавим:
- **Транзакции** — реальные данные вместо строк
- **Кошельки** — криптографические ключи
- **Proof-of-Work** — защита от каскадного пересчёта
