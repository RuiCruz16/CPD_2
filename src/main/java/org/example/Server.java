package org.example;

import javax.net.ssl.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;



public class Server {
    private static TokenManager tokenManager = new TokenManager();
    private static final Map<String, ClientHandler> userSessions = new HashMap<>();
    private static final Lock userSessionLock = new ReentrantLock();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java org.example.Server <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        try {
            System.setProperty("javax.net.ssl.keyStore", "keystore.jks");
            System.setProperty("javax.net.ssl.keyStorePassword", "cpdg16");

            SSLServerSocketFactory sslServerSocketFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(port);

            serverSocket.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});

            System.out.println("Secure server listening on port " + port);

            // Initialize roomMap, activeClients, and credentials
            Map<String, DefaultRoom> roomMap = new HashMap<>();
            List<ClientHandler> activeClients = new ArrayList<>();
            Map<String, String> credentials = loadCredentials();

            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                Thread.startVirtualThread(() -> new ClientHandler(clientSocket, credentials, roomMap, activeClients).run());
                Thread.startVirtualThread(() -> {
                    while (true) {
                        try {
                            Thread.sleep(360000);
                            tokenManager.cleanExpiredTokens();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static Map<String, String> loadCredentials() {
        Map<String, String> credentials = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("./doc/users.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    credentials.put(parts[0], parts[1]);
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("users.txt file not found. No credentials loaded.");
        } catch (IOException e) {
            System.out.println("Error reading users.txt: " + e.getMessage());
        }
        return credentials;
    }

    public static TokenManager getTokenManager() {
        return tokenManager;
    }

    public static void registerUserSession(String username, ClientHandler handler) {
        userSessionLock.lock();
        try {
            userSessions.put(username, handler);
        }
        finally {
            userSessionLock.unlock();
        }
    }

    public static ClientHandler getUserSession(String username) {
        userSessionLock.lock();
        try {
            return userSessions.get(username);
        } finally {
            userSessionLock.unlock();
        }
    }

    public static void removeUserSession(String username) {
        userSessionLock.lock();
        try {
            userSessions.remove(username);
        } finally {
            userSessionLock.unlock();
        }
    }


}
