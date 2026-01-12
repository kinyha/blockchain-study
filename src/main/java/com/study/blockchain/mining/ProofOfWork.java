package com.study.blockchain.mining;

import com.study.blockchain.core.Block;

public class ProofOfWork {

    private final int difficulty;

    public ProofOfWork(int difficulty) {
        this.difficulty = difficulty;
    }

    public String getTarget() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < difficulty; i++) {
            sb.append("0");
        }
        return sb.toString();
    }

    public void mine(Block block) {
        String target = getTarget();
        block.setNonce(0);
        block.updateHash();

        while (!block.getHash().startsWith(target)) {
            block.incrementNonce();
            block.updateHash();
        }
    }

    public boolean isValidProof(Block block) {
        return block.getHash().startsWith(getTarget());
    }

    public int getDifficulty() {
        return difficulty;
    }
}
