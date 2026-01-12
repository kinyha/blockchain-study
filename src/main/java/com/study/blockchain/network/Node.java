package com.study.blockchain.network;

import com.study.blockchain.core.Block;
import com.study.blockchain.core.Blockchain;
import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.utxo.UTXOPool;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Node {

    private static final Gson gson = new Gson();

    private final Blockchain blockchain;
    private final UTXOPool utxoPool;
    private final List<Transaction> mempool;
    private final List<PeerConnection> peers;
    private ServerSocket serverSocket;
    private final String nodeId;
    private volatile boolean running;

    public Node(Blockchain blockchain, UTXOPool utxoPool) {
        this.blockchain = blockchain;
        this.utxoPool = utxoPool;
        this.mempool = new ArrayList<>();
        this.peers = new CopyOnWriteArrayList<>();
        this.nodeId = UUID.randomUUID().toString().substring(0, 8);
        this.running = false;
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
        String payload = gson.toJson(Map.of(
            "index", block.getIndex(),
            "timestamp", block.getTimestamp(),
            "data", block.getData(),
            "previousHash", block.getPreviousHash(),
            "hash", block.getHash(),
            "nonce", block.getNonce()
        ));
        broadcast(new Message(MessageType.NEW_BLOCK, payload));
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
                peer.setPeerId((String) handshake.get("nodeId"));
                break;

            case NEW_BLOCK:
                // In a real implementation, we would:
                // 1. Parse block from JSON
                // 2. Validate block
                // 3. Add to blockchain if valid
                break;

            case NEW_TRANSACTION:
                // In a real implementation, we would:
                // 1. Parse transaction from JSON
                // 2. Validate signatures
                // 3. Add to mempool if valid
                break;

            case GET_BLOCKS:
                // Send blocks to peer
                break;

            case GET_PEERS:
                // Send peer list
                break;

            default:
                break;
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
