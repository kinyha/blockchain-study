package com.study.blockchain.network;

import java.io.*;
import java.net.Socket;

/**
 * Соединение с peer узлом.
 * Задание: lessons/10-network.md
 */
public class PeerConnection {

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String peerId;

    public PeerConnection(Socket socket) {
        // TODO: Сохрани socket, создай reader/writer из streams
        throw new UnsupportedOperationException("Реализуй конструктор PeerConnection");
    }

    public void send(Message message) {
        // TODO: Отправь message.toJson() + newline
        throw new UnsupportedOperationException("Реализуй send");
    }

    public Message receive() {
        // TODO: Прочитай строку и Message.fromJson()
        throw new UnsupportedOperationException("Реализуй receive");
    }

    public void close() {
        // TODO: Закрой socket и streams
        throw new UnsupportedOperationException("Реализуй close");
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public String getRemoteAddress() {
        return socket != null ? socket.getRemoteSocketAddress().toString() : "unknown";
    }
}
