package com.example.signaling;

import org.java_websocket.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Room {
    private final String id;
    private final Map<String, WebSocket> peers = new ConcurrentHashMap<>();
    private String moderatorId;

    public Room(String id) {
        this.id = id;
    }

    public synchronized void join(String userId, WebSocket socket) {
        if (peers.isEmpty()) {
            moderatorId = userId;
        }
        peers.put(userId, socket);
    }

    public synchronized void leave(String userId) {
        peers.remove(userId);
        if (userId.equals(moderatorId) && !peers.isEmpty()) {
            moderatorId = peers.keySet().iterator().next();
        }
    }

    public String getId() { return id; }
    public String getModeratorId() { return moderatorId; }
    public Map<String, WebSocket> getPeers() { return peers; }
    public List<String> getPeerIds() { return new ArrayList<>(peers.keySet()); }
}
