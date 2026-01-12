# Урок 3: Цепочка блоков (Blockchain)

## Введение

Мы уже знаем, что такое блок и как вычисляется его hash. Теперь соберём блоки в **цепочку** — и получим настоящий blockchain!

## Что такое Blockchain?

**Blockchain** — это структура данных, в которой блоки связаны через криптографические hash-и.

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Block 0     │     │  Block 1     │     │  Block 2     │
│  (Genesis)   │────▶│              │────▶│              │
│  hash: A1B2  │     │  prevHash:A1B2    │  prevHash:C3D4│
└──────────────┘     │  hash: C3D4  │     │  hash: E5F6  │
                     └──────────────┘     └──────────────┘
```

## Genesis Block

Первый блок в любом blockchain:
- **index = 0**
- **previousHash = "0"**
- Создаётся при инициализации

## Добавление новых блоков

1. Берём hash последнего блока
2. Создаём новый блок с `previousHash = hash последнего`
3. Вычисляем hash нового блока
4. Добавляем в цепочку

## Почему blockchain сложно подделать?

Чтобы изменить один блок, нужно пересчитать все последующие. Каждый hash зависит от предыдущего!

---

## Задание

Открой `src/main/java/com/study/blockchain/core/Blockchain.java`.

### Шаг 1: Добавь поле chain

```java
private final List<Block> chain;
```

### Шаг 2: Реализуй конструктор

```java
public Blockchain() {
    this.chain = new ArrayList<>();
    createGenesisBlock();
}
```

### Шаг 3: Реализуй createGenesisBlock

```java
private void createGenesisBlock() {
    Block genesis = Block.createGenesisBlock(GENESIS_DATA);
    genesis.updateHash();
    chain.add(genesis);
}
```

### Шаг 4: Реализуй addBlock

```java
public Block addBlock(String data) {
    Block lastBlock = getLatestBlock();

    int newIndex = lastBlock.getIndex() + 1;
    long timestamp = System.currentTimeMillis();
    String previousHash = lastBlock.getHash();

    Block newBlock = new Block(newIndex, timestamp, data, previousHash);
    newBlock.updateHash();

    chain.add(newBlock);
    return newBlock;
}
```

### Шаг 5: Реализуй геттеры

```java
public Block getLatestBlock() {
    return chain.get(chain.size() - 1);
}

public Block getBlock(int index) {
    return chain.get(index);
}

public List<Block> getChain() {
    return Collections.unmodifiableList(chain);
}

public int size() {
    return chain.size();
}

public int getHeight() {
    return chain.size() - 1;
}
```

### Шаг 6 (пока пропусти): isValid и validateDetailed

Эти методы реализуем в уроке 4. Пока оставь `throw new UnsupportedOperationException(...)`.

---

## Проверь себя

```bash
./gradlew test --tests BlockchainTest
```

Большинство тестов должны пройти (кроме валидации).

Если застрял:
```bash
git checkout solutions -- src/main/java/com/study/blockchain/core/Blockchain.java
```

---

## Ключевые термины

| Термин | Описание |
|--------|----------|
| Blockchain | Цепочка блоков, связанных через hash |
| Genesis Block | Первый блок (index = 0) |
| Block Height | Позиция блока в цепочке |

## Что дальше?

В следующем уроке научимся **валидировать** цепочку.
