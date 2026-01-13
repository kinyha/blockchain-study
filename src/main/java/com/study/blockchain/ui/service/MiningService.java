package com.study.blockchain.ui.service;

import com.study.blockchain.core.Block;
import com.study.blockchain.mining.ProofOfWork;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.util.function.BiConsumer;

/**
 * Background mining service that doesn't block UI.
 */
public class MiningService extends Service<Block> {

    private final Block block;
    private final ProofOfWork pow;
    private final BiConsumer<Long, String> onProgress;

    public MiningService(Block block, ProofOfWork pow, BiConsumer<Long, String> onProgress) {
        this.block = block;
        this.pow = pow;
        this.onProgress = onProgress;
    }

    @Override
    protected Task<Block> createTask() {
        return new Task<>() {
            @Override
            protected Block call() {
                String target = pow.getTarget();
                block.setNonce(0);
                block.updateHash();

                while (!block.getHash().startsWith(target)) {
                    if (isCancelled()) {
                        return null;
                    }

                    block.incrementNonce();
                    block.updateHash();

                    // Report progress every 10000 iterations
                    if (block.getNonce() % 10000 == 0) {
                        final long nonce = block.getNonce();
                        final String hash = block.getHash();
                        if (onProgress != null) {
                            onProgress.accept(nonce, hash);
                        }
                    }
                }

                // Final progress update
                if (onProgress != null) {
                    onProgress.accept(block.getNonce(), block.getHash());
                }

                return block;
            }
        };
    }
}
