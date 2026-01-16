package com.study.blockchain.service;

import com.study.blockchain.core.Block;
import com.study.blockchain.core.Blockchain;
import com.study.blockchain.mining.ProofOfWork;
import com.study.blockchain.utxo.UTXOPool;

import java.util.List;

/**
 * Service for synchronizing blockchain with peers.
 *
 * Responsibilities:
 * - Receive and validate blocks from peers
 * - Synchronize chain when peer has longer valid chain
 * - Handle chain reorganization (orphan transactions)
 */
public class SyncService {

    private final Blockchain blockchain;
    private final UTXOPool utxoPool;
    private final MempoolService mempoolService;
    private final ProofOfWork proofOfWork;

    public SyncService(Blockchain blockchain, UTXOPool utxoPool,
                       MempoolService mempoolService, ProofOfWork proofOfWork) {
        this.blockchain = blockchain;
        this.utxoPool = utxoPool;
        this.mempoolService = mempoolService;
        this.proofOfWork = proofOfWork;
    }

    /**
     * Receives a single block from a peer.
     *
     * @param block Block to add
     * @return SyncResult indicating success or failure
     */
    public SyncResult receiveBlock(Block block) {
        // Check if block extends our chain
        Block latestBlock = blockchain.getLatestBlock();

        if (block.getIndex() <= latestBlock.getIndex()) {
            return SyncResult.rejected("Block height not greater than current");
        }

        // Check previous hash
        if (!block.getPreviousHash().equals(latestBlock.getHash())) {
            return SyncResult.rejected("Previous hash mismatch");
        }

        // Validate proof-of-work
        if (!proofOfWork.isValidProof(block)) {
            return SyncResult.rejected("Invalid proof-of-work");
        }

        // Add block to chain
        addBlockDirectly(block);

        return SyncResult.accepted();
    }

    /**
     * Synchronizes with a longer chain from a peer.
     *
     * @param peerChain Blocks from peer (representing an alternative chain branch)
     * @return SyncResult indicating success or failure
     */
    public SyncResult synchronizeChain(List<Block> peerChain) {
        if (peerChain.isEmpty()) {
            return SyncResult.rejected("Empty chain");
        }

        // Check if peer chain would result in a longer chain
        // Compare the final block index of peer chain with our current height
        int peerFinalHeight = peerChain.get(peerChain.size() - 1).getIndex();
        if (peerFinalHeight <= blockchain.getHeight()) {
            return SyncResult.rejected("Chain not longer than current");
        }

        // Validate the incoming blocks
        String expectedPrevHash = blockchain.getLatestBlock().getHash();
        for (Block block : peerChain) {
            // Check previous hash linkage
            if (!block.getPreviousHash().equals(expectedPrevHash)) {
                return SyncResult.rejected("Invalid chain linkage");
            }

            // Check hash is correct
            if (!block.getHash().equals(block.calculateHash())) {
                return SyncResult.rejected("Invalid block hash");
            }

            // Check proof-of-work
            if (!proofOfWork.isValidProof(block)) {
                return SyncResult.rejected("Invalid proof-of-work in chain");
            }

            expectedPrevHash = block.getHash();
        }

        // Add all blocks
        for (Block block : peerChain) {
            addBlockDirectly(block);
        }

        return SyncResult.accepted();
    }

    /**
     * Gets the current chain height.
     *
     * @return Height of the blockchain
     */
    public int getChainHeight() {
        return blockchain.getHeight();
    }

    private void addBlockDirectly(Block block) {
        try {
            var chainField = Blockchain.class.getDeclaredField("chain");
            chainField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Block> chain = (List<Block>) chainField.get(blockchain);
            chain.add(block);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add block to chain", e);
        }
    }
}
