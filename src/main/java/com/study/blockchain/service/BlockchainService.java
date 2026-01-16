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
 * High-level blockchain service integrating all components.
 *
 * This is the main entry point for blockchain operations, coordinating:
 * - Mining operations
 * - Transaction creation and validation
 * - Wallet balance queries
 * - Chain synchronization
 */
public class BlockchainService {

    private final Blockchain blockchain;
    private final UTXOPool utxoPool;
    private final ProofOfWork proofOfWork;

    private final TransactionService transactionService;
    private final MiningService miningService;
    private final MempoolService mempoolService;
    private final WalletService walletService;
    private final SyncService syncService;

    /**
     * Creates a new BlockchainService with specified mining difficulty.
     *
     * @param difficulty Mining difficulty (number of leading zeros in hash)
     */
    public BlockchainService(int difficulty) {
        this.blockchain = new Blockchain();
        this.utxoPool = new UTXOPool();
        this.proofOfWork = new ProofOfWork(difficulty);

        this.transactionService = new TransactionService(utxoPool);
        this.miningService = new MiningService(blockchain, utxoPool, proofOfWork);
        this.mempoolService = new MempoolService(utxoPool);
        this.walletService = new WalletService(utxoPool);
        this.syncService = new SyncService(blockchain, utxoPool, mempoolService, proofOfWork);
    }

    // === Mining Operations ===

    /**
     * Mines a new block with pending transactions.
     *
     * @param miner Wallet to receive mining reward
     * @return The mined block
     */
    public Block mineBlock(Wallet miner) {
        List<Transaction> transactions = mempoolService.getPendingTransactions();
        Block block = miningService.mineBlock(miner, transactions);

        // Remove mined transactions from mempool
        mempoolService.removeTransactions(transactions);

        return block;
    }

    // === Transaction Operations ===

    /**
     * Creates and broadcasts a new transaction.
     *
     * @param sender    Sender wallet
     * @param recipient Recipient public key
     * @param amount    Amount to send
     * @return TransactionResult with success status
     */
    public TransactionResult createTransaction(Wallet sender, PublicKey recipient, double amount) {
        TransactionResult result = transactionService.createTransaction(sender, recipient, amount);

        if (result.isSuccess()) {
            mempoolService.addTransaction(result.getTransaction());
        }

        return result;
    }

    /**
     * Gets the number of pending transactions in mempool.
     *
     * @return Count of pending transactions
     */
    public int getPendingTransactionsCount() {
        return mempoolService.size();
    }

    /**
     * Gets all pending transactions.
     *
     * @return List of pending transactions
     */
    public List<Transaction> getPendingTransactions() {
        return mempoolService.getPendingTransactions();
    }

    // === Wallet Operations ===

    /**
     * Gets the balance of a wallet.
     *
     * @param wallet Wallet to check
     * @return Balance in coins
     */
    public double getBalance(Wallet wallet) {
        return walletService.getBalance(wallet);
    }

    /**
     * Gets the UTXOs owned by a wallet.
     *
     * @param wallet Wallet to check
     * @return List of UTXOs
     */
    public List<TransactionOutput> getUtxos(Wallet wallet) {
        return walletService.getUtxos(wallet);
    }

    /**
     * Gets the address of a wallet.
     *
     * @param wallet Wallet
     * @return Address string
     */
    public String getWalletAddress(Wallet wallet) {
        return walletService.getAddress(wallet);
    }

    // === Chain Query Operations ===

    /**
     * Gets the latest block in the chain.
     *
     * @return Latest block
     */
    public Block getLatestBlock() {
        return blockchain.getLatestBlock();
    }

    /**
     * Gets a block by its index.
     *
     * @param index Block index
     * @return Block or null if not found
     */
    public Block getBlock(int index) {
        if (index < 0 || index >= blockchain.size()) {
            return null;
        }
        return blockchain.getBlock(index);
    }

    /**
     * Gets all blocks in the chain.
     *
     * @return List of all blocks
     */
    public List<Block> getBlocks() {
        return blockchain.getChain();
    }

    /**
     * Gets the current blockchain size.
     *
     * @return Number of blocks
     */
    public int getBlockchainSize() {
        return blockchain.size();
    }

    /**
     * Gets the current chain height (index of latest block).
     *
     * @return Chain height
     */
    public int getChainHeight() {
        return blockchain.getHeight();
    }

    /**
     * Validates the entire blockchain.
     *
     * @return true if chain is valid
     */
    public boolean isChainValid() {
        return blockchain.isValid();
    }

    // === Sync Operations ===

    /**
     * Receives a block from a peer.
     *
     * @param block Block to add
     * @return SyncResult indicating success or failure
     */
    public SyncResult receiveBlock(Block block) {
        return syncService.receiveBlock(block);
    }

    /**
     * Synchronizes with a peer's chain.
     *
     * @param peerChain Blocks from peer
     * @return SyncResult indicating success or failure
     */
    public SyncResult synchronizeChain(List<Block> peerChain) {
        return syncService.synchronizeChain(peerChain);
    }

    // === Access to underlying services (for advanced usage) ===

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public UTXOPool getUtxoPool() {
        return utxoPool;
    }

    public MempoolService getMempoolService() {
        return mempoolService;
    }

    public SyncService getSyncService() {
        return syncService;
    }
}
