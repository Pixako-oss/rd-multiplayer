package server;

import server.client.Client;
import server.client.ClientHandler;
import server.client.TimeoutHandler;
import server.level.Level;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final Path PROPERTIES_PATH = Paths.get("server.properties");

    public static int PORT = 9090;
    public static int PLAYER_LIMIT = 50;
    public static int MAX_PER_IP = 3;

    public static Level level;

    public static final Set<Client> clients = ConcurrentHashMap.newKeySet();
    public static final ConcurrentHashMap<Client, Long> lastKeepAlive = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {

        loadProperties();

        level = new Level(256, 256, 64);
        level.save();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Saving level...");
            level.save();
        }));

        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);

        TimeoutHandler.start();

        while (true) {
            Socket clientSocket = serverSocket.accept();

            System.out.println("Client connected from: "
                    + clientSocket.getInetAddress().getHostAddress());

            new Thread(() -> ClientHandler.handle(clientSocket)).start();
        }
    }

    private static void loadProperties() {
        try {

            if (!Files.exists(PROPERTIES_PATH)) {
                createDefaultProperties();
            }

            Properties properties = new Properties();

            try (InputStream in = Files.newInputStream(PROPERTIES_PATH)) {
                properties.load(in);
            }

            PORT = Integer.parseInt(properties.getProperty("port", "9090"));
            PLAYER_LIMIT = Integer.parseInt(properties.getProperty("player_limit", "50"));
            MAX_PER_IP = Integer.parseInt(properties.getProperty("max_per_ip", "3"));

            System.out.println("Loaded server.properties");

        } catch (Exception e) {
            System.err.println("Failed to load server.properties");
            e.printStackTrace();
        }
    }

    private static void createDefaultProperties() throws IOException {

        Properties defaults = new Properties();

        defaults.setProperty("port", "9090");
        defaults.setProperty("player_limit", "50");
        defaults.setProperty("max_per_ip", "3");

        try (OutputStream out = Files.newOutputStream(PROPERTIES_PATH)) {
            defaults.store(out, "Server Properties");
        }

        System.out.println("Created default server.properties");
    }
}