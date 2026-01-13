package com.study.blockchain.ui;

import com.study.blockchain.core.Block;
import com.study.blockchain.core.Blockchain;
import com.study.blockchain.mining.ProofOfWork;
import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.transaction.TransactionInput;
import com.study.blockchain.transaction.TransactionOutput;
import com.study.blockchain.ui.service.MiningService;
import com.study.blockchain.utxo.UTXOPool;
import com.study.blockchain.wallet.Wallet;
import com.study.blockchain.network.Node;
import com.study.blockchain.network.Message;
import com.study.blockchain.network.MessageType;
import com.study.blockchain.network.PeerConnection;
import com.google.gson.Gson;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.security.PublicKey;
import java.util.*;

/**
 * Main controller that wires UI components with blockchain services.
 */
public class MainController {

    private static final int DIFFICULTY = 5;
    private static final double MINING_REWARD = 1.0;
    private static final double GENESIS_REWARD = 5.0;
    private static final Gson gson = new Gson();

    private final int port;
    private final BorderPane root;

    // Core components
    private Blockchain blockchain;
    private UTXOPool utxoPool;
    private Wallet wallet;
    private Node node;
    private ProofOfWork pow;

    // UI components
    private BlockchainView blockchainView;
    private WalletPane walletPane;
    private SendForm sendForm;
    private MiningPane miningPane;
    private PeersPane peersPane;
    private TextArea logArea;

    // Services
    private MiningService miningService;

    public MainController(int port) {
        this.port = port;
        this.root = new BorderPane();
        initializeCore();
        initializeUI();
    }

    private void initializeCore() {
        this.blockchain = new Blockchain();
        this.utxoPool = new UTXOPool();
        this.wallet = new Wallet();
        this.pow = new ProofOfWork(DIFFICULTY);
        this.node = new Node(blockchain, utxoPool);
    }

    private void initializeUI() {
        // Top bar
        HBox topBar = createTopBar();
        root.setTop(topBar);

        // Center - blockchain view
        blockchainView = new BlockchainView(blockchain);
        root.setCenter(blockchainView);

        // Right side - wallet and send form
        VBox rightPane = new VBox(10);
        rightPane.setPadding(new Insets(10));
        rightPane.setPrefWidth(300);

        walletPane = new WalletPane(wallet, utxoPool);
        sendForm = new SendForm(this::onSendTransaction);

        rightPane.getChildren().addAll(walletPane, sendForm);
        root.setRight(rightPane);

        // Bottom - mining and peers
        VBox bottomPane = new VBox(5);
        bottomPane.setPadding(new Insets(10));

        miningPane = new MiningPane();
        peersPane = new PeersPane(node);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(4);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");

        bottomPane.getChildren().addAll(miningPane, peersPane, logArea);
        root.setBottom(bottomPane);
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #2c3e50;");

        Label nodeLabel = new Label("Node: localhost:" + port);
        nodeLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        Button mineButton = new Button("Mine Block");
        mineButton.setOnAction(e -> startMining());

        Button connectButton = new Button("Connect...");
        connectButton.setOnAction(e -> showConnectDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBar.getChildren().addAll(nodeLabel, spacer, mineButton, connectButton);
        return topBar;
    }

    public void initialize() {
        // Set up listeners for network events
        node.setOnBlockReceived(() -> {
            Platform.runLater(() -> {
                log("Received new block from peer! Chain height: " + blockchain.getHeight());
                log("UTXO pool size: " + utxoPool.size() + ", Balance: " + utxoPool.getBalance(wallet.getPublicKey()));
                updateUI();
            });
        });

        node.setOnPeerConnected(() -> {
            Platform.runLater(() -> {
                log("Peer connected - sharing wallet info");
                // Share our wallet with newly connected peer
                node.broadcastWalletInfo(wallet.getPublicKey(), wallet.getAddress());
                updateUI();
            });
        });

        node.setOnWalletReceived(() -> {
            Platform.runLater(() -> {
                log("Received peer wallet info");
                updateUI();
            });
        });

        try {
            node.start(port);
            log("Node started on port " + port);
            log("Wallet: " + wallet.getAddress().substring(0, 20) + "...");
        } catch (IOException e) {
            log("Failed to start node: " + e.getMessage());
        }
        updateUI();
    }

    private void startMining() {
        if (miningService != null && miningService.isRunning()) {
            log("Mining already in progress");
            return;
        }

        log("Starting mining...");
        miningPane.setMining(true);

        // Calculate reward: genesis (5) + mining (1) for first block, just mining (1) for others
        Block lastBlock = blockchain.getLatestBlock();
        int newBlockIndex = lastBlock.getIndex() + 1;
        boolean isFirstBlock = lastBlock.getIndex() == 0; // Mining block #1
        double reward = isFirstBlock ? (GENESIS_REWARD + MINING_REWARD) : MINING_REWARD;
        log("Mining block #" + newBlockIndex + ", reward: " + reward + " BTC");

        // Create coinbase transaction (mining reward)
        // Use unique parentTxId to avoid UTXO ID collisions
        String coinbaseTxId = "coinbase-block-" + newBlockIndex + "-" + System.currentTimeMillis();
        Transaction coinbase = new Transaction();
        coinbase.addOutput(new TransactionOutput(
                wallet.getPublicKey(),
                reward,
                coinbaseTxId
        ));
        coinbase.finalizeTransaction();

        // Include mempool transactions
        List<Transaction> txs = node.getMempool();
        String blockData = "Coinbase: " + coinbase.getTransactionId();
        if (!txs.isEmpty()) {
            blockData += " + " + txs.size() + " transactions";
        }

        // Create new block
        Block newBlock = new Block(
                lastBlock.getIndex() + 1,
                System.currentTimeMillis(),
                blockData,
                lastBlock.getHash()
        );

        // Start mining service
        miningService = new MiningService(newBlock, pow, this::onNonceUpdate);
        miningService.setOnSucceeded(e -> {
            Platform.runLater(() -> onBlockMined(newBlock, coinbase, txs));
        });
        miningService.start();
    }

    private void onNonceUpdate(long nonce, String hash) {
        Platform.runLater(() -> miningPane.update(nonce, hash, pow.getTarget()));
    }

    private void onBlockMined(Block block, Transaction coinbase, List<Transaction> txs) {
        // Add block to chain directly (already mined with correct hash)
        node.addBlockDirectly(block);

        // Collect all outputs to broadcast
        List<Map<String, Object>> outputsForBroadcast = new ArrayList<>();

        // Update UTXO pool with coinbase reward
        for (TransactionOutput output : coinbase.getOutputs()) {
            log("Adding UTXO: id=" + output.getId().substring(0, 16) + "..., amount=" + output.getAmount());
            utxoPool.addUTXO(output);
            outputsForBroadcast.add(serializeOutput(output));
        }

        // Process mempool transactions
        for (Transaction tx : txs) {
            for (TransactionInput input : tx.getInputs()) {
                utxoPool.removeUTXO(input.getTransactionOutputId());
            }
            for (TransactionOutput output : tx.getOutputs()) {
                utxoPool.addUTXO(output);
                outputsForBroadcast.add(serializeOutput(output));
            }
        }
        node.getMempool().clear();

        // Broadcast block with all outputs to peers
        node.broadcastBlockWithOutputs(block, outputsForBroadcast);

        log("Block #" + block.getIndex() + " mined! Nonce: " + block.getNonce());
        log("Hash: " + block.getHash());
        log("UTXO pool size: " + utxoPool.size() + ", Balance: " + utxoPool.getBalance(wallet.getPublicKey()));
        miningPane.setMining(false);
        updateUI();
    }

    private Map<String, Object> serializeOutput(TransactionOutput output) {
        Map<String, Object> data = new HashMap<>();
        data.put("recipientKey", Base64.getEncoder().encodeToString(
                output.getRecipientPublicKey().getEncoded()));
        data.put("amount", output.getAmount());
        data.put("parentTxId", output.getParentTransactionId());
        return data;
    }

    private void onSendTransaction(String toAddress, double amount) {
        double available = utxoPool.getAvailableBalance(wallet.getPublicKey());
        if (amount > available) {
            log("Insufficient available balance: " + available + " < " + amount);
            return;
        }

        // Get recipient's public key from known peer wallets
        PublicKey recipientKey = node.getPeerWallets().get(toAddress);
        if (recipientKey == null) {
            log("Unknown recipient address");
            return;
        }

        // Create transaction
        Transaction tx = new Transaction();

        // Select UTXOs to spend (excludes already reserved)
        List<TransactionOutput> myUTXOs = utxoPool.getUTXOsForAddress(wallet.getPublicKey());
        double collected = 0;
        List<TransactionOutput> spentUTXOs = new ArrayList<>();

        for (TransactionOutput utxo : myUTXOs) {
            TransactionInput input = new TransactionInput(utxo.getId());
            input.setUTXO(utxo);
            tx.addInput(input);
            spentUTXOs.add(utxo);
            collected += utxo.getAmount();
            if (collected >= amount) break;
        }

        // Reserve spent UTXOs to prevent double-spend
        for (TransactionOutput utxo : spentUTXOs) {
            utxoPool.reserveUTXO(utxo.getId());
        }

        // Output to recipient
        tx.addOutput(new TransactionOutput(recipientKey, amount, "pending"));

        // Change back to ourselves
        double change = collected - amount;
        if (change > 0) {
            tx.addOutput(new TransactionOutput(wallet.getPublicKey(), change, "pending"));
        }

        // Finalize and sign
        tx.finalizeTransaction();
        tx.signAllInputs(wallet.getPrivateKey());

        // Add to mempool and broadcast
        node.broadcastTransaction(tx);

        log("Transaction created: " + amount + " BTC");
        log("TxID: " + tx.getTransactionId().substring(0, 16) + "...");
        log("Waiting for block to confirm...");

        updateUI();
    }

    private void showConnectDialog() {
        TextInputDialog dialog = new TextInputDialog("localhost:8002");
        dialog.setTitle("Connect to Peer");
        dialog.setHeaderText("Enter peer address");
        dialog.setContentText("Host:Port:");

        dialog.showAndWait().ifPresent(this::connectToPeer);
    }

    public void connectToPeer(String address) {
        try {
            String[] parts = address.split(":");
            String host = parts[0];
            int peerPort = Integer.parseInt(parts[1]);

            node.connectToPeer(host, peerPort);
            log("Connected to " + address);

            // Share our wallet info with the peer we just connected to
            node.broadcastWalletInfo(wallet.getPublicKey(), wallet.getAddress());
            log("Sent wallet info to peer");

            updateUI();
        } catch (Exception e) {
            log("Failed to connect: " + e.getMessage());
        }
    }

    private void updateUI() {
        Platform.runLater(() -> {
            blockchainView.refresh();
            walletPane.refresh();
            peersPane.refresh();
            sendForm.updatePeerWallets(node.getPeerWallets().keySet());
        });
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
        });
    }

    public void shutdown() {
        if (miningService != null) {
            miningService.cancel();
        }
        node.stop();
    }

    public Parent getRoot() {
        return root;
    }
}
