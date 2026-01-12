package com.study.blockchain.mining;

import com.study.blockchain.core.Block;

/**
 * Proof-of-Work майнинг.
 * Задание: lessons/08-proof-of-work.md
 */
public class ProofOfWork {

    private final int difficulty;

    public ProofOfWork(int difficulty) {
        // TODO: Сохрани difficulty
        throw new UnsupportedOperationException("Реализуй конструктор ProofOfWork");
    }

    public String getTarget() {
        // TODO: Верни строку из difficulty нулей
        // Например для difficulty=4: "0000"
        throw new UnsupportedOperationException("Реализуй getTarget");
    }

    public void mine(Block block) {
        // TODO: Перебирай nonce пока hash не начнётся с target
        // block.setNonce(0);
        // block.updateHash();
        // while (!block.getHash().startsWith(target)) {
        //     block.incrementNonce();
        //     block.updateHash();
        // }
        throw new UnsupportedOperationException("Реализуй mine");
    }

    public boolean isValidProof(Block block) {
        // TODO: Проверь что hash блока начинается с target
        throw new UnsupportedOperationException("Реализуй isValidProof");
    }

    public int getDifficulty() {
        return difficulty;
    }
}
