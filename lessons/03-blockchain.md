# Урок 3: Цепочка блоков (Blockchain)

## Введение

Мы уже знаем, что такое блок и как вычисляется его hash. Теперь соберём блоки в **цепочку** — и получим настоящий blockchain!

## Что такое Blockchain?

**Blockchain** (цепочка блоков) — это структура данных, в которой блоки связаны между собой через криптографические hash-и.

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Block 0     │     │  Block 1     │     │  Block 2     │
│  (Genesis)   │────▶│              │────▶│              │
│              │     │              │     │              │
│  hash: A1B2  │     │  prevHash:A1B2    │  prevHash:C3D4│
└──────────────┘     │  hash: C3D4  │     │  hash: E5F6  │
                     └──────────────┘     └──────────────┘
```

Каждый блок "знает" hash предыдущего блока через поле `previousHash`. Это создаёт неразрывную цепочку от самого первого блока до последнего.

## Почему это называется "цепочка"?

Аналогия с реальной цепью:
- Каждое **звено** (блок) физически соединено с предыдущим
- Нельзя **вставить** новое звено в середину, не разорвав цепь
- Нельзя **заменить** звено незаметно
- Вся цепь держится на **первом звене** (Genesis block)

В цифровой цепочке "соединение" — это криптографическая связь через hash.

## Genesis Block — начало всего

**Genesis Block** — первый блок в любом blockchain. Особенности:

1. **index = 0** — всегда первый
2. **previousHash = "0"** — нет предшественника
3. **Создаётся вручную** — не добывается (mined)
4. **Уникален** — в каждом blockchain свой Genesis

### Genesis Block в Bitcoin

Первый блок Bitcoin был создан Сатоши Накамото 3 января 2009 года. В его данных зашифровано послание:

> "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"

Это заголовок газеты The Times того дня — доказательство, что блок не мог быть создан раньше этой даты.

## Добавление новых блоков

Когда мы хотим добавить новый блок:

1. Берём hash **последнего** блока в цепочке
2. Создаём новый блок с `previousHash = hash последнего блока`
3. Вычисляем hash нового блока
4. Добавляем блок в цепочку

```java
public void addBlock(String data) {
    Block lastBlock = getLatestBlock();
    int newIndex = lastBlock.getIndex() + 1;
    String previousHash = lastBlock.getHash();

    Block newBlock = new Block(newIndex, System.currentTimeMillis(), data, previousHash);
    newBlock.updateHash();

    chain.add(newBlock);
}
```

## Визуализация добавления блока

**Шаг 1:** Текущее состояние цепочки

```
[Genesis] ──▶ [Block 1] ──▶ [Block 2]
 hash:A        hash:B        hash:C (последний)
```

**Шаг 2:** Создаём новый блок

```
Новый Block 3:
  index: 3
  previousHash: C (hash последнего блока)
  data: "новые данные"
  hash: D (вычисляем)
```

**Шаг 3:** Добавляем в цепочку

```
[Genesis] ──▶ [Block 1] ──▶ [Block 2] ──▶ [Block 3]
 hash:A        hash:B        hash:C        hash:D
```

## Структура класса Blockchain

```java
public class Blockchain {
    private List<Block> chain;

    public Blockchain() {
        this.chain = new ArrayList<>();
        // Создаём Genesis блок при инициализации
        chain.add(Block.createGenesisBlock("Genesis Block"));
        chain.get(0).updateHash();
    }

    public void addBlock(String data) { ... }

    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    public List<Block> getChain() {
        return Collections.unmodifiableList(chain);
    }

    public int size() {
        return chain.size();
    }
}
```

## Почему blockchain сложно подделать?

### Сценарий атаки

Злоумышленник хочет изменить данные в Block 1:

```
До:  [Genesis] ──▶ [Block 1: "Alice→Bob: 10"] ──▶ [Block 2]
                     hash: ABC123

После: [Genesis] ──▶ [Block 1: "Alice→Hacker: 10"] ──▶ [Block 2]
                     hash: XYZ789 (изменился!)
                                                    prevHash: ABC123 ← НЕ СОВПАДАЕТ!
```

### Что происходит?

1. Данные Block 1 изменились → hash изменился
2. Block 2.previousHash всё ещё указывает на старый hash
3. **Цепочка сломана!** Несовпадение обнаружено.

### Каскадный эффект

Чтобы подделать Block 1, нужно:
1. Изменить Block 1
2. Пересчитать hash Block 1
3. Обновить previousHash в Block 2
4. Пересчитать hash Block 2
5. Обновить previousHash в Block 3
6. ...и так далее до конца цепочки!

В реальном blockchain с Proof-of-Work каждый пересчёт занимает огромные вычислительные ресурсы (см. урок 8).

## Immutability — неизменяемость

Blockchain обеспечивает **иммутабельность** (immutability) — данные нельзя изменить после записи.

Технически данные можно изменить, но:
1. Это сразу обнаружится при валидации
2. В распределённой сети другие узлы отвергнут изменённую цепочку
3. Пересчёт всех последующих блоков экономически невыгоден

## Высота блока (Block Height)

**Block height** — это индекс блока в цепочке.

```
Genesis Block  →  height = 0
Block 1        →  height = 1
Block 2        →  height = 2
...
Block N        →  height = N
```

Block height также показывает, сколько подтверждений имеет транзакция. Транзакция в блоке с height=100 при текущей высоте цепочки 105 имеет 5 подтверждений.

## Практика

Изучи файл:
- `src/main/java/com/study/blockchain/core/Blockchain.java`

Попробуй ответить:
1. Что произойдёт, если изменить данные Genesis блока?
2. Почему Genesis блок создаётся в конструкторе?
3. Можно ли добавить блок с произвольным previousHash?

## Ключевые термины

| Термин | Описание |
|--------|----------|
| Blockchain | Цепочка блоков, связанных через hash |
| Genesis Block | Первый блок в цепочке (height = 0) |
| Block Height | Позиция блока в цепочке (индекс) |
| Immutability | Неизменяемость данных после записи |
| Chain | Список блоков, составляющих blockchain |

## Что дальше?

В следующем уроке мы научимся **валидировать** цепочку — проверять, что никто не подделал данные.
