package com.study.blockchain.network;

import com.google.gson.Gson;

/**
 * Сообщение P2P протокола.
 * Задание: lessons/10-network.md
 */
public class Message {

    private static final Gson gson = new Gson();

    private MessageType type;
    private String payload;
    private long timestamp;

    public Message(MessageType type, String payload) {
        // TODO: Инициализируй поля, timestamp = System.currentTimeMillis()
        throw new UnsupportedOperationException("Реализуй конструктор Message");
    }

    public String toJson() {
        // TODO: Сериализуй в JSON через Gson
        throw new UnsupportedOperationException("Реализуй toJson");
    }

    public static Message fromJson(String json) {
        // TODO: Десериализуй из JSON
        throw new UnsupportedOperationException("Реализуй fromJson");
    }

    public MessageType getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
