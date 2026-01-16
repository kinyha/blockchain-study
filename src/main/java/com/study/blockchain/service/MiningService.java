package com.study.blockchain.service;

import com.study.blockchain.core.Block;
import com.study.blockchain.core.Blockchain;
import com.study.blockchain.mining.ProofOfWork;
import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.transaction.TransactionOutput;
import com.study.blockchain.utxo.UTXOPool;
import com.study.blockchain.wallet.Wallet;

import java.security.PublicKey;
import java.util.List;

/**
 * Service for mining blocks and managing rewards.
 *
 * Business rules:
 * - Genesis reward: 10 coins
 * - Block reward: 1 coin
 * - Transaction fees go to the miner
 */
public class MiningService {

    public static final double GENESIS_REWARD = 10.0;
    public static final double BLOCK_REWARD = 1.0;

    private final Blockchain blockchain;
    private final UTXOPool utxoPool;
    private final ProofOfWork proofOfWork;

    private boolean genesisRewardGiven = false;

    public MiningService(Blockchain blockchain, UTXOPool utxoPool, ProofOfWork proofOfWork) {
        this.blockchain = blockchain;
        this.utxoPool = utxoPool;
        this.proofOfWork = proofOfWork;
    }

    /**
     * Mines a new block with the given transactions.
     *
     * @param miner        Wallet of the miner who will receive the reward
     * @param transactions List of transactions to include in the block
     * @return The mined block
     */
    public Block mineBlock(Wallet miner, List<Transaction> transactions) {
        // Calculate reward
        double reward = genesisRewardGiven ? BLOCK_REWARD : GENESIS_REWARD;

        // Calculate total fees from transactions
        double totalFees = calculateTotalFees(transactions);

        // Create coinbase transaction (reward + fees)
        String coinbaseTxId = createCoinbaseTransaction(miner.getPublicKey(), reward + totalFees);

        // Process transactions - update UTXO pool
        processTransactions(transactions);

        // Build block data
        String blockData = buildBlockData(coinbaseTxId, transactions);

        // Create and mine the block
        Block block = createBlock(blockData);
        proofOfWork.mine(block);

        // Add block to chain
        addBlockToChain(block);

        // Mark genesis reward as given
        genesisRewardGiven = true;

        return block;
    }

    private double calculateTotalFees(List<Transaction> transactions) {
        double totalFees = 0;
        for (Transaction tx : transactions) {
            double fee = tx.getInputsValue() - tx.getOutputsValue();
            totalFees += fee;
        }
        return totalFees;
    }

    private String createCoinbaseTransaction(PublicKey minerKey, double amount) {
        // Create coinbase output for miner
        String coinbaseTxId = "coinbase-" + System.currentTimeMillis();
        TransactionOutput coinbaseOutput = new TransactionOutput(minerKey, amount, coinbaseTxId);
        utxoPool.addUTXO(coinbaseOutput);
        return coinbaseTxId;
    }

    private void processTransactions(List<Transaction> transactions) {
        for (Transaction tx : transactions) {
            // Remove spent UTXOs (this also releases reservations)
            for (var input : tx.getInputs()) {
                utxoPool.removeUTXO(input.getTransactionOutputId());
            }

            // Add new UTXOs
            for (TransactionOutput output : tx.getOutputs()) {
                utxoPool.addUTXO(output);
            }
        }
    }

    /**
     * Releases all UTXO reservations (useful for rejected transactions).
     */
    public void releaseAllReservations(List<Transaction> transactions) {
        for (Transaction tx : transactions) {
            for (var input : tx.getInputs()) {
                utxoPool.releaseReservation(input.getTransactionOutputId());
            }
        }
    }

    private String buildBlockData(String coinbaseTxId, List<Transaction> transactions) {
        StringBuilder sb = new StringBuilder();
        sb.append("coinbase:").append(coinbaseTxId);

        for (Transaction tx : transactions) {
            sb.append(";tx:").append(tx.getTransactionId());
        }

        return sb.toString();
    }

    private Block createBlock(String data) {
        Block latestBlock = blockchain.getLatestBlock();
        int newIndex = latestBlock.getIndex() + 1;
        String previousHash = latestBlock.getHash();

        return new Block(newIndex, System.currentTimeMillis(), data, previousHash);
    }

    private void addBlockToChain(Block block) {
        // Use reflection to add block directly since Blockchain.addBlock() creates its own block
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
