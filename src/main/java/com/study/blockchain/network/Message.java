package com.study.blockchain.network;

import com.google.gson.Gson;

public class Message {

    private static final Gson gson = new Gson();

    private MessageType type;
    private String payload;
    private long timestamp;

    public Message(MessageType type, String payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static Message fromJson(String json) {
        return gson.fromJson(json, Message.class);
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
