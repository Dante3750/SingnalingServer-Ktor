package com.example.signaling.model;

import com.google.gson.JsonObject;

public class SignalingMessage {
    public String type;
    public String senderId;
    public String targetId;
    public String roomId;
    public JsonObject data;

    public SignalingMessage() {}

    public SignalingMessage(String type, String senderId, String targetId, String roomId, JsonObject data) {
        this.type = type;
        this.senderId = senderId;
        this.targetId = targetId;
        this.roomId = roomId;
        this.data = data;
    }
}
