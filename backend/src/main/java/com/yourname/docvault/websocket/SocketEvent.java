package com.yourname.docvault.websocket;

public record SocketEvent(String type, Object payload) {
    public static SocketEvent of(String type, Object payload) {
        return new SocketEvent(type, payload);
    }
}
