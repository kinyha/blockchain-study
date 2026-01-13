package com.study.blockchain.network;

import com.study.blockchain.core.Block;
import com.study.blockchain.core.Blockchain;
import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.utxo.UTXOPool;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.study.blockchain.transaction.TransactionOutput;

public class Node {

    private static final Gson gson = new Gson();

    private final Blockchain blockchain;
    private final UTXOPool utxoPool;
    private final List<Transaction> mempool;
    private final List<PeerConnection> peers;
    private ServerSocket serverSocket;
    private final String nodeId;
    private volatile boolean running;

    // Listeners for UI updates
    private Runnable onBlockReceived;
    private Runnable onPeerConnected;
    private Runnable onWalletReceived;

    // Known peer wallets: address -> PublicKey
    private final Map<String, PublicKey> peerWallets = new ConcurrentHashMap<>();

    public Node(Blockchain blockchain, UTXOPool utxoPool) {
        this.blockchain = blockchain;
        this.utxoPool = utxoPool;
        this.mempool = new ArrayList<>();
        this.peers = new CopyOnWriteArrayList<>();
        this.nodeId = UUID.randomUUID().toString().substring(0, 8);
        this.running = false;
    }

    public void setOnBlockReceived(Runnable callback) {
        this.onBlockReceived = callback;
    }

    public void setOnPeerConnected(Runnable callback) {
        this.onPeerConnected = callback;
    }

    public void setOnWalletReceived(Runnable callback) {
        this.onWalletReceived = callback;
    }

    public Map<String, PublicKey> getPeerWallets() {
        return peerWallets;
    }

    public void broadcastWalletInfo(PublicKey publicKey, String address) {
        String pubKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        String payload = gson.toJson(Map.of(
            "address", address,
            "publicKey", pubKeyBase64
        ));
        broadcast(new Message(MessageType.WALLET_INFO, payload));

        // Also broadcast all known peer wallets
        for (Map.Entry<String, PublicKey> entry : peerWallets.entrySet()) {
            String peerPayload = gson.toJson(Map.of(
                "address", entry.getKey(),
                "publicKey", Base64.getEncoder().encodeToString(entry.getValue().getEncoded())
            ));
            broadcast(new Message(MessageType.WALLET_INFO, peerPayload));
        }
    }

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    PeerConnection peer = new PeerConnection(clientSocket);
                    peers.add(peer);
                    startListening(peer);
                } catch (IOException e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void connectToPeer(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        PeerConnection peer = new PeerConnection(socket);
        peers.add(peer);

        // Send handshake
        String handshakePayload = gson.toJson(Map.of(
            "nodeId", nodeId,
            "height", blockchain.getHeight(),
            "version", 1
        ));
        peer.send(new Message(MessageType.HANDSHAKE, handshakePayload));

        startListening(peer);
    }

    private void startListening(PeerConnection peer) {
        Thread listenThread = new Thread(() -> {
            try {
                while (running && peer.isConnected()) {
                    Message message = peer.receive();
                    if (message != null) {
                        handleMessage(peer, message);
                    }
                }
            } catch (IOException e) {
                // Connection closed
            } finally {
                peers.remove(peer);
                peer.close();
            }
        });
        listenThread.setDaemon(true);
        listenThread.start();
    }

    public void broadcast(Message message) {
        for (PeerConnection peer : peers) {
            if (peer.isConnected()) {
                peer.send(message);
            }
        }
    }

    public void broadcastBlock(Block block) {
        broadcastBlockWithOutputs(block, List.of());
    }

    public void broadcastBlockWithOutputs(Block block, List<Map<String, Object>> outputs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("index", block.getIndex());
        payload.put("timestamp", block.getTimestamp());
        payload.put("data", block.getData());
        payload.put("previousHash", block.getPreviousHash());
        payload.put("hash", block.getHash());
        payload.put("nonce", block.getNonce());
        payload.put("outputs", outputs);

        broadcast(new Message(MessageType.NEW_BLOCK, gson.toJson(payload)));
    }

    public void broadcastTransaction(Transaction tx) {
        mempool.add(tx);
        String payload = gson.toJson(Map.of(
            "transactionId", tx.getTransactionId()
        ));
        broadcast(new Message(MessageType.NEW_TRANSACTION, payload));
    }

    public void handleMessage(PeerConnection peer, Message message) {
        switch (message.getType()) {
            case HANDSHAKE:
                Map<String, Object> handshake = gson.fromJson(message.getPayload(), Map.class);
                String peerId = (String) handshake.get("nodeId");
                int peerHeight = ((Number) handshake.get("height")).intValue();

                boolean firstHandshake = peer.getPeerId() == null;
                if (firstHandshake) {
                    peer.setPeerId(peerId);

                    // Send our handshake back
                    String ourHandshake = gson.toJson(Map.of(
                        "nodeId", nodeId,
                        "height", blockchain.getHeight(),
                        "version", 1
                    ));
                    peer.send(new Message(MessageType.HANDSHAKE, ourHandshake));

                    if (onPeerConnected != null) {
                        onPeerConnected.run();
                    }
                }

                // Always check if we need blocks (both sides of connection)
                if (peerHeight > blockchain.getHeight()) {
                    peer.send(new Message(MessageType.GET_BLOCKS,
                        gson.toJson(Map.of("fromIndex", blockchain.getHeight()))));
                }
                break;

            case NEW_BLOCK:
                handleNewBlock(message.getPayload());
                break;

            case NEW_TRANSACTION:
                // TODO: Parse and validate transaction
                break;

            case WALLET_INFO:
                handleWalletInfo(message.getPayload());
                break;

            case GET_BLOCKS:
                handleGetBlocks(peer, message.getPayload());
                break;

            case BLOCKS:
                handleBlocks(message.getPayload());
                break;

            case GET_PEERS:
                // Send peer list
                break;

            default:
                break;
        }
    }

    private void handleNewBlock(String payload) {
        try {
            Map<String, Object> blockData = gson.fromJson(payload, Map.class);

            int index = ((Number) blockData.get("index")).intValue();
            long timestamp = ((Number) blockData.get("timestamp")).longValue();
            String data = (String) blockData.get("data");
            String previousHash = (String) blockData.get("previousHash");
            String hash = (String) blockData.get("hash");
            long nonce = ((Number) blockData.get("nonce")).longValue();

            // Verify this block connects to our chain
            Block lastBlock = blockchain.getLatestBlock();
            System.out.println("[Node] handleNewBlock: received block #" + index + ", our lastBlock #" + lastBlock.getIndex());
            if (index != lastBlock.getIndex() + 1) {
                // Block doesn't connect - might need sync
                System.out.println("[Node] handleNewBlock: SKIPPING - block doesn't connect");
                return;
            }
            if (!previousHash.equals(lastBlock.getHash())) {
                // Chain mismatch
                return;
            }

            // Create and add the block
            Block newBlock = new Block(index, timestamp, data, previousHash);
            newBlock.setNonce(nonce);
            newBlock.setHash(hash);

            // Verify hash is correct
            if (!newBlock.calculateHash().equals(hash)) {
                return; // Invalid hash
            }

            // Add to our chain
            addBlockDirectly(newBlock);

            // Process transaction outputs from the block
            processBlockOutputs(blockData);

            // Relay block to other peers (propagate through network)
            broadcast(new Message(MessageType.NEW_BLOCK, payload));

            // Notify UI
            if (onBlockReceived != null) {
                onBlockReceived.run();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void processBlockOutputs(Map<String, Object> blockData) {
        try {
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) blockData.get("outputs");
            if (outputs == null || outputs.isEmpty()) {
                return;
            }

            KeyFactory keyFactory = KeyFactory.getInstance("EC");

            for (Map<String, Object> outputData : outputs) {
                String recipientKeyBase64 = (String) outputData.get("recipientKey");
                double amount = ((Number) outputData.get("amount")).doubleValue();
                String parentTxId = (String) outputData.get("parentTxId");

                byte[] keyBytes = Base64.getDecoder().decode(recipientKeyBase64);
                PublicKey recipientKey = keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));

                TransactionOutput output = new TransactionOutput(recipientKey, amount, parentTxId);
                System.out.println("[Node] processBlockOutputs: adding UTXO id=" + output.getId().substring(0, 16) + ", amount=" + amount);
                utxoPool.addUTXO(output);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleWalletInfo(String payload) {
        try {
            Map<String, Object> data = gson.fromJson(payload, Map.class);
            String address = (String) data.get("address");
            String pubKeyBase64 = (String) data.get("publicKey");

            // Skip if we already know this wallet
            if (peerWallets.containsKey(address)) {
                return;
            }

            byte[] pubKeyBytes = Base64.getDecoder().decode(pubKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(pubKeyBytes));

            peerWallets.put(address, publicKey);

            // Relay wallet info to other peers
            broadcast(new Message(MessageType.WALLET_INFO, payload));

            if (onWalletReceived != null) {
                onWalletReceived.run();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleGetBlocks(PeerConnection peer, String payload) {
        try {
            Map<String, Object> request = gson.fromJson(payload, Map.class);
            int fromIndex = ((Number) request.get("fromIndex")).intValue();

            List<Block> chain = blockchain.getChain();
            List<Map<String, Object>> blocksData = new ArrayList<>();

            for (int i = fromIndex + 1; i < chain.size(); i++) {
                Block block = chain.get(i);
                Map<String, Object> blockData = new HashMap<>();
                blockData.put("index", block.getIndex());
                blockData.put("timestamp", block.getTimestamp());
                blockData.put("data", block.getData());
                blockData.put("previousHash", block.getPreviousHash());
                blockData.put("hash", block.getHash());
                blockData.put("nonce", block.getNonce());
                blocksData.add(blockData);
            }

            // Also send UTXO pool data
            List<Map<String, Object>> utxoData = new ArrayList<>();
            for (TransactionOutput utxo : utxoPool.getAllUTXOs()) {
                Map<String, Object> data = new HashMap<>();
                data.put("recipientKey", Base64.getEncoder().encodeToString(
                    utxo.getRecipientPublicKey().getEncoded()));
                data.put("amount", utxo.getAmount());
                data.put("parentTxId", utxo.getParentTransactionId());
                utxoData.add(data);
            }

            peer.send(new Message(MessageType.BLOCKS, gson.toJson(Map.of(
                "blocks", blocksData,
                "utxos", utxoData
            ))));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleBlocks(String payload) {
        try {
            Map<String, Object> data = gson.fromJson(payload, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blocksData = (List<Map<String, Object>>) data.get("blocks");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> utxoData = (List<Map<String, Object>>) data.get("utxos");

            // Add blocks
            for (Map<String, Object> blockData : blocksData) {
                int index = ((Number) blockData.get("index")).intValue();
                long timestamp = ((Number) blockData.get("timestamp")).longValue();
                String blockDataStr = (String) blockData.get("data");
                String previousHash = (String) blockData.get("previousHash");
                String hash = (String) blockData.get("hash");
                long nonce = ((Number) blockData.get("nonce")).longValue();

                Block lastBlock = blockchain.getLatestBlock();
                if (index != lastBlock.getIndex() + 1) continue;
                if (!previousHash.equals(lastBlock.getHash())) continue;

                Block newBlock = new Block(index, timestamp, blockDataStr, previousHash);
                newBlock.setNonce(nonce);
                newBlock.setHash(hash);

                if (!newBlock.calculateHash().equals(hash)) continue;

                addBlockDirectly(newBlock);
            }

            // Sync UTXOs
            if (utxoData != null) {
                KeyFactory keyFactory = KeyFactory.getInstance("EC");
                for (Map<String, Object> utxo : utxoData) {
                    String recipientKeyBase64 = (String) utxo.get("recipientKey");
                    double amount = ((Number) utxo.get("amount")).doubleValue();
                    String parentTxId = (String) utxo.get("parentTxId");

                    byte[] keyBytes = Base64.getDecoder().decode(recipientKeyBase64);
                    PublicKey recipientKey = keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));

                    TransactionOutput output = new TransactionOutput(recipientKey, amount, parentTxId);
                    utxoPool.addUTXO(output);
                }
            }

            if (onBlockReceived != null) {
                onBlockReceived.run();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addBlockDirectly(Block block) {
        // Access the internal chain list via reflection or add a method to Blockchain
        // For now, we'll use a workaround by adding through the existing method
        // and then updating the hash/nonce
        blockchain.getChain(); // This returns unmodifiable list

        // We need to add a method to Blockchain to add pre-mined blocks
        // For now, let's use reflection as a workaround
        try {
            java.lang.reflect.Field chainField = Blockchain.class.getDeclaredField("chain");
            chainField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Block> chain = (List<Block>) chainField.get(blockchain);
            chain.add(block);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
        for (PeerConnection peer : peers) {
            peer.close();
        }
        peers.clear();
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public UTXOPool getUtxoPool() {
        return utxoPool;
    }

    public List<Transaction> getMempool() {
        return mempool;
    }

    public List<PeerConnection> getPeers() {
        return peers;
    }

    public String getNodeId() {
        return nodeId;
    }

    public boolean isRunning() {
        return running;
    }
}
