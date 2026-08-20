package com.example.signaling;

import org.java_websocket.WebSocket;

public class Peer {
    private final String id;
    private WebSocket socket;

    public Peer(String id, WebSocket socket) {
        this.id = id;
        this.socket = socket;
    }

    public String getId() { return id; }
    public WebSocket getSocket() { return socket; }
    public void setSocket(WebSocket socket) { this.socket = socket; }
}
