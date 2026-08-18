package com.example.slagalica.wsserver;

import java.net.InetSocketAddress;

public final class ServerApp {
    private static final long STARTUP_TIMEOUT_MS = 5_000L;

    private ServerApp() {
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            port = Integer.parseInt(envPort);
        }

        SlagalicaWebSocketServer server = new SlagalicaWebSocketServer(new InetSocketAddress("0.0.0.0", port));
        server.start();
        server.awaitStartup(STARTUP_TIMEOUT_MS);
        System.out.println("Slagalica websocket server started on ws://0.0.0.0:" + port);
        Thread.currentThread().join();
    }
}
