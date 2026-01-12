# Урок 2: Хэширование (SHA-256)

## Введение

В предыдущем уроке мы узнали, что каждый блок имеет **hash** — свой уникальный "отпечаток пальца". Теперь разберёмся, как этот отпечаток создаётся и почему он так важен для безопасности blockchain.

## Что такое хэш-функция?

**Хэш-функция** — это математический алгоритм, который превращает данные любого размера в строку фиксированной длины.

```
Вход (любой размер)          Выход (фиксированный размер)
─────────────────────        ─────────────────────────────
"Hello"               ──▶    2cf24dba5fb0a30e26e83b2ac5b9e29e...
"Hello World"         ──▶    a591a6d40bf420404a011733cfb7b190...
Файл на 10 ГБ         ──▶    7d865e959b2466918c9863afca942d0f...
```

## SHA-256: Стандарт криптовалют

**SHA-256** (Secure Hash Algorithm 256-bit) — алгоритм хэширования, используемый в Bitcoin.

Характеристики:
- **256 бит** = 64 символа в hex-представлении
- Разработан **NSA**
- Считается криптографически стойким

## Свойства криптографических хэш-функций

### 1. Детерминированность
Одинаковый вход **всегда** даёт одинаковый выход.

### 2. Лавинный эффект (Avalanche Effect)
Минимальное изменение входа **полностью** меняет hash.

```
sha256("Hello")  = 185f8db32271fe25f561a6fc938b2e26...
sha256("hello")  = 2cf24dba5fb0a30e26e83b2ac5b9e29e...  // совершенно другой!
```

### 3. Однонаправленность
По hash **невозможно** восстановить исходные данные.

### 4. Устойчивость к коллизиям
Практически **невозможно** найти два разных входа с одинаковым hash.

## Как hash защищает blockchain?

Если злоумышленник изменит данные блока — hash изменится, и цепочка сломается.

---

## Задание

### Часть 1: Реализуй HashUtil

Открой `src/main/java/com/study/blockchain/core/HashUtil.java`.

```java
public static String sha256(String input) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("SHA-256 not available", e);
    }
}
```

### Проверь HashUtil

```bash
./gradlew test --tests HashUtilTest
```

Все тесты должны пройти!

---

### Часть 2: Добавь хэширование в Block

Вернись к `Block.java` и реализуй `calculateHash()` и `updateHash()`:

```java
public String calculateHash() {
    String input = index + String.valueOf(timestamp) + data + previousHash;
    return HashUtil.sha256(input);
}

public void updateHash() {
    this.hash = calculateHash();
}
```

### Проверь Block с хэшированием

```bash
./gradlew test --tests BlockTest
```

Теперь все тесты Block должны пройти!

---

## Проверь себя

Запусти все тесты:

```bash
./gradlew test --tests HashUtilTest --tests BlockTest
```

Если застрял:
```bash
git checkout solutions -- src/main/java/com/study/blockchain/core/HashUtil.java
git checkout solutions -- src/main/java/com/study/blockchain/core/Block.java
```

---

## Ключевые термины

| Термин | Описание |
|--------|----------|
| Hash | Результат хэш-функции |
| SHA-256 | Криптографический алгоритм, 256-битный hash |
| Avalanche Effect | Малое изменение входа → полное изменение hash |
| MessageDigest | Java класс для хэширования |

## Что дальше?

В следующем уроке соберём блоки в **цепочку (Blockchain)**.
