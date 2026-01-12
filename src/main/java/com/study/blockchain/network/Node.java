package com.study.blockchain.network;

import com.study.blockchain.core.Block;
import com.study.blockchain.core.Blockchain;
import com.study.blockchain.transaction.Transaction;
import com.study.blockchain.utxo.UTXOPool;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Узел P2P сети.
 * Задание: lessons/10-network.md
 */
public class Node {

    private final Blockchain blockchain;
    private final UTXOPool utxoPool;
    private final List<Transaction> mempool;
    private final List<PeerConnection> peers;
    private ServerSocket serverSocket;
    private final String nodeId;
    private volatile boolean running;

    public Node(Blockchain blockchain, UTXOPool utxoPool) {
        // TODO: Инициализируй поля
        // nodeId = UUID.randomUUID().toString().substring(0, 8)
        throw new UnsupportedOperationException("Реализуй конструктор Node");
    }

    public void start(int port) throws IOException {
        // TODO: Создай ServerSocket на порту
        // Запусти поток для приёма входящих соединений
        throw new UnsupportedOperationException("Реализуй start");
    }

    public void connectToPeer(String host, int port) throws IOException {
        // TODO: Создай Socket, PeerConnection
        // Отправь HANDSHAKE
        // Добавь в peers
        throw new UnsupportedOperationException("Реализуй connectToPeer");
    }

    public void broadcast(Message message) {
        // TODO: Отправь сообщение всем peers
        throw new UnsupportedOperationException("Реализуй broadcast");
    }

    public void broadcastBlock(Block block) {
        // TODO: Создай NEW_BLOCK сообщение с JSON блока
        // broadcast()
        throw new UnsupportedOperationException("Реализуй broadcastBlock");
    }

    public void broadcastTransaction(Transaction tx) {
        // TODO: Создай NEW_TRANSACTION сообщение
        // Добавь в mempool
        // broadcast()
        throw new UnsupportedOperationException("Реализуй broadcastTransaction");
    }

    public void handleMessage(PeerConnection peer, Message message) {
        // TODO: switch по message.getType()
        // HANDSHAKE: сохрани peerId
        // NEW_BLOCK: валидируй и добавь в blockchain
        // NEW_TRANSACTION: валидируй и добавь в mempool
        throw new UnsupportedOperationException("Реализуй handleMessage");
    }

    public void stop() {
        // TODO: running = false
        // Закрой serverSocket и все peer connections
        throw new UnsupportedOperationException("Реализуй stop");
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
