package com.example.slagalica.wsserver;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

        LeaderboardCycleService cycleService = new LeaderboardCycleService();
        LeaderboardCycleService.ActiveCycles cycles = cycleService.ensureCurrentCycles();
        System.out.println(
                "Active leaderboard cycles: weekly=" + cycles.getWeekly().getCycleId()
                        + ", monthly=" + cycles.getMonthly().getCycleId()
        );

        SlagalicaWebSocketServer server = new SlagalicaWebSocketServer(
                new InetSocketAddress("0.0.0.0", port),
                cycleService
        );
        server.start();
        server.awaitStartup(STARTUP_TIMEOUT_MS);
        System.out.println("Slagalica websocket server started on ws://0.0.0.0:" + port);

        ScheduledExecutorService cycleMonitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "leaderboard-cycle-monitor");
            thread.setDaemon(true);
            return thread;
        });
        cycleMonitor.scheduleWithFixedDelay(() -> {
            try {
                cycleService.ensureCurrentCycles();
            } catch (Exception exception) {
                System.err.println("Leaderboard cycle maintenance failed: " + exception.getMessage());
                exception.printStackTrace(System.err);
            }
        }, 1, 1, TimeUnit.MINUTES);

        Thread.currentThread().join();
    }
}
