# Урок 1: Что такое Block

## Введение

Представь себе обычную бухгалтерскую книгу, в которой записываются все транзакции компании. Каждая страница — это **block** (блок). Blockchain — это такая же книга, только цифровая и с одной важной особенностью: страницы в ней нельзя вырвать или переписать.

## Что такое Block?

**Block** — это контейнер для данных в blockchain. Каждый блок содержит:

1. **index** — порядковый номер блока в цепочке (0, 1, 2, ...)
2. **timestamp** — время создания блока (Unix timestamp в миллисекундах)
3. **data** — полезная нагрузка (в будущем это будут транзакции)
4. **previousHash** — "отпечаток пальца" предыдущего блока
5. **hash** — "отпечаток пальца" текущего блока

## Аналогия с реальным миром

Представь цепочку вагонов поезда:
- Каждый вагон (block) имеет свой номер (index)
- На каждом вагоне написано время отправления (timestamp)
- Внутри вагона — груз (data)
- У каждого вагона есть сцепка с предыдущим (previousHash)
- И уникальный идентификатор вагона (hash)

Если кто-то попытается вставить поддельный вагон в середину поезда, это сразу станет заметно — сцепки не совпадут.

## Genesis Block

Самый первый блок в цепочке называется **Genesis Block** (блок генезиса). Это особенный блок:
- Его index = 0
- У него нет предыдущего блока, поэтому previousHash = "0" (или другое условное значение)
- Он создаётся вручную при инициализации blockchain

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Genesis Block  │───▶│    Block 1      │───▶│    Block 2      │
│  index: 0       │    │  index: 1       │    │  index: 2       │
│  prevHash: "0"  │    │  prevHash: abc..│    │  prevHash: def..│
│  hash: abc...   │    │  hash: def...   │    │  hash: ghi...   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Зачем нужен hash?

Hash — это криптографический "отпечаток" данных. Подробнее о нём мы поговорим в следующем уроке. Пока важно знать:

1. **Уникальность** — даже минимальное изменение данных полностью меняет hash
2. **Однонаправленность** — по hash нельзя восстановить исходные данные
3. **Фиксированная длина** — hash всегда одинаковой длины, независимо от размера данных

## Почему блоки связаны через hash?

Каждый блок хранит hash предыдущего блока. Это создаёт **цепочку** (chain):

```
Block 2.previousHash = Block 1.hash
Block 1.previousHash = Genesis.hash
```

Если злоумышленник изменит данные в Block 1:
1. Hash Block 1 изменится
2. Block 2.previousHash больше не будет совпадать с Block 1.hash
3. Подделка обнаружена!

---

## Задание

Открой файл `src/main/java/com/study/blockchain/core/Block.java` и реализуй класс.

### Шаг 1: Добавь поля

```java
private final int index;
private final long timestamp;
private String data;
private String previousHash;
private String hash;
```

### Шаг 2: Реализуй конструктор

```java
public Block(int index, long timestamp, String data, String previousHash) {
    this.index = index;
    this.timestamp = timestamp;
    this.data = data;
    this.previousHash = previousHash;
    this.hash = "";  // пока пустой, вычислим в уроке 2
}
```

### Шаг 3: Реализуй createGenesisBlock

```java
public static Block createGenesisBlock(String data) {
    return new Block(0, System.currentTimeMillis(), data, GENESIS_PREV_HASH);
}
```

### Шаг 4: Добавь геттеры

Реализуй все методы `getIndex()`, `getTimestamp()`, `getData()`, `getPreviousHash()`, `getHash()`.

### Шаг 5: Реализуй setHash и вспомогательные методы

```java
public void setHash(String hash) {
    this.hash = hash;
}

void setDataForTesting(String data) {
    this.data = data;
}

void setPreviousHashForTesting(String previousHash) {
    this.previousHash = previousHash;
}
```

### Шаг 6: Реализуй toString

```java
@Override
public String toString() {
    return "Block{" +
            "index=" + index +
            ", timestamp=" + timestamp +
            ", data='" + data + '\'' +
            ", previousHash='" + previousHash + '\'' +
            ", hash='" + hash + '\'' +
            '}';
}
```

### Шаг 7 (пока пропусти): calculateHash и updateHash

Эти методы реализуем в уроке 2, когда изучим хэширование. Пока оставь `throw new UnsupportedOperationException(...)`.

---

## Проверь себя

Запусти тесты для Block (пока пройдут только базовые):

```bash
./gradlew test --tests BlockTest
```

Если застрял — посмотри готовое решение:
```bash
git checkout solutions -- src/main/java/com/study/blockchain/core/Block.java
```

---

## Ключевые термины

| Термин | Описание |
|--------|----------|
| Block | Контейнер данных в blockchain |
| Genesis Block | Первый блок в цепочке (index = 0) |
| Hash | Криптографический отпечаток данных |
| previousHash | Связь с предыдущим блоком |
| Timestamp | Метка времени создания блока |

## Что дальше?

В следующем уроке изучим **хэширование (SHA-256)** и реализуем `calculateHash()`.
