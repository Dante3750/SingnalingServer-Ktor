package com.example.signaling;

import com.example.signaling.model.SignalingMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;
import java.util.Collection;

public class SignalingServer extends WebSocketServer {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public SignalingServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println(">>> New Connection: " + conn.getRemoteSocketAddress());
        // Send a dummy room-info to keep the client happy
        JsonObject data = new JsonObject();
        data.addProperty("yourId", "user-" + conn.hashCode());
        conn.send(gson.toJson(new SignalingMessage("room-info", "server", "", "test-room", data)));
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            System.out.println("LOG: Received -> " + message);
            // Broadcast it to everyone else!
            Collection<WebSocket> conns = getConnections();
            int count = 0;
            for (WebSocket client : conns) {
                if (client != conn && client.isOpen()) {
                    client.send(message);
                    count++;
                }
            }
            System.out.println("    Relayed to " + count + " peers");
        } catch (Exception e) {
            System.err.println("!!! Error: " + e.getMessage());
        }
    }

    @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) { System.out.println("<<< Disconnected"); }
    @Override public void onError(WebSocket conn, Exception ex) { ex.printStackTrace(); }
    @Override public void onStart() { System.out.println("SERVER RUNNING ON PORT 8887 - READY FOR CALLS"); }

    public static void main(String[] args) {
        new SignalingServer(8887).start();
    }
}
