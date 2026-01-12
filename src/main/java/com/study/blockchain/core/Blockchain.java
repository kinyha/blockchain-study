package com.study.blockchain.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Blockchain — цепочка блоков, связанных криптографическими hash-ами.
 * <p>
 * ЗАДАНИЕ (Урок 3):
 * Реализуй этот класс согласно инструкциям в lessons/03-blockchain.md
 * <p>
 * После реализации запусти тесты:
 * ./gradlew test --tests BlockchainTest
 */
public class Blockchain {

    private static final String GENESIS_DATA = "Genesis Block";

    private List<Block> chain;

    public Blockchain() {
        this.chain = new ArrayList<>();
        createGenesisBlock();
    }

    private void createGenesisBlock() {
        Block genesis = Block.createGenesisBlock(GENESIS_DATA);
        genesis.updateHash();
        chain.add(genesis);
    }

    public Block addBlock(String data) {
        Block lastBlock = getLatestBlock();

        int newIndex = lastBlock.getIndex() + 1;
        String previousHash = lastBlock.getHash();

        Block block = new Block(newIndex, System.currentTimeMillis(), data, previousHash);
        block.updateHash();

        chain.add(block);
        return block;
    }


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

    /**
     * TODO (Урок 4): Проверяет валидность цепочки.
     */
    public boolean isValid() {
        return validateDetailed().isValid();
    }

    public ValidationResult validateDetailed() {
        Block genesis = chain.get(0);
        if (!genesis.getPreviousHash().equals(Block.GENESIS_PREV_HASH)) {
            return ValidationResult.failure(0,
                    "Genesis block has invalid previousHash");
        }
        if (!genesis.getHash().equals(genesis.calculateHash())) {
            return ValidationResult.failure(0,
                    "Genesis block hash mismatch");
        }

        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);

            if (!current.getHash().equals(current.calculateHash())) {
                return ValidationResult.failure(i,
                        "Block " + i + " hash mismatch");
            }

            if (!current.getPreviousHash().equals(previous.getHash())) {
                return ValidationResult.failure(i,
                        "Block " + i + " chain break");
            }
        }
        return ValidationResult.success();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Blockchain {\n");
        sb.append("  size: ").append(size()).append("\n");
        sb.append("  height: ").append(getHeight()).append("\n");
        sb.append("  valid: ").append(isValid()).append("\n");
        sb.append("  blocks:\n");
        for (Block block : chain) {
            sb.append("    ").append(block).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
