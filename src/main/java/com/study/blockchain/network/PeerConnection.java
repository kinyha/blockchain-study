package com.study.blockchain.network;

import java.io.*;
import java.net.Socket;

public class PeerConnection {

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String peerId;

    public PeerConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    public void send(Message message) {
        writer.println(message.toJson());
    }

    public Message receive() throws IOException {
        String json = reader.readLine();
        if (json == null) {
            return null;
        }
        return Message.fromJson(json);
    }

    public void close() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignore close errors
        }
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
