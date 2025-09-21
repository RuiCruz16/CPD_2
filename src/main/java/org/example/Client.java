package org.example;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Client {
    private static String serverToken = null;
    private static String currentRoom = null;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int INITIAL_RECONNECT_DELAY = 1000; // 1 second

    private static final Lock tokenLock = new ReentrantLock();
    private static final Lock reconnectLock = new ReentrantLock();

    private static Thread currentReaderThread = null;
    private static volatile boolean isReconnecting = false;
    private static volatile boolean shouldExit = false;
    private static volatile SSLSocket currentSocket = null;
    private static volatile PrintWriter currentWriter = null;
    private static volatile BufferedReader currentReader = null;
    private static final Lock socketLock = new ReentrantLock();
    private static final Scanner scanner = new Scanner(System.in);

    private static volatile boolean processingReconnect = false;

    public static void main(String[] args) throws InterruptedException {
        if (args.length != 2) {
            System.out.println("Usage: java org.example.Client <addr> <port>");
            return;
        }

        String addr = args[0];
        int port = Integer.parseInt(args[1]);

        try {
            System.setProperty("javax.net.ssl.trustStore", "truststore.jks");
            System.setProperty("javax.net.ssl.trustStorePassword", "cpdg16");

            runClientLoop(addr, port);
        } catch (Exception e) {
            System.out.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    private static void runClientLoop(String addr, int port) {
        try {
            connectToServer(addr, port);

            while (!shouldExit) {
                String command = scanner.nextLine();

                if (command.equalsIgnoreCase("exit")) {
                    shouldExit = true;
                    break;
                }

                if (command.startsWith("JOIN ")) {
                    currentRoom = command.substring(5);
                } else if (command.equals("LEAVE")) {
                    currentRoom = null;
                } else if (command.equals("SIM")) {
                    System.out.println("Simulating a connection loss...");
                    socketLock.lock();
                    try {
                        if (currentSocket != null) {
                            currentSocket.close();
                        }
                    } catch (IOException e) {
                        System.out.println("Error closing socket: " + e.getMessage());
                    } finally {
                        socketLock.unlock();
                    }
                    continue;
                }

                if (!isReconnecting) {
                    socketLock.lock();
                    try {
                        if (currentWriter != null) {
                            currentWriter.println(command);
                            currentWriter.flush();
                        } else {
                            System.out.println("Not connected to server. Attempting to reconnect...");
                            connectToServer(addr, port);
                        }
                    } finally {
                        socketLock.unlock();
                    }
                } else {
                    System.out.println("Cannot send command while reconnecting. Please wait...");
                }
            }

            closeConnection();

        } catch (Exception e) {
            System.out.println("Error in client loop: " + e.getMessage());
            if (!shouldExit) {
                handleReconnection(addr, port);
            }
        }
    }

    private static void connectToServer(String addr, int port) {
        socketLock.lock();
        try {
            closeConnection();

            SSLSocket socket = createSSLSocket(addr, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            currentSocket = socket;
            currentReader = in;
            currentWriter = out;

            startReaderThread(addr, port);

            if (serverToken != null) {
                processingReconnect = true;
                System.out.println("Reconnecting to server...");
                out.println("RECONNECT " + serverToken);
                out.flush();
            } else {
                System.out.println("Connected to server!");
            }

        } catch (IOException e) {
            System.out.println("Connection failed: " + e.getMessage());
            handleReconnection(addr, port);
        } finally {
            socketLock.unlock();
        }
    }

    private static void closeConnection() {
        try {
            if (currentSocket != null && !currentSocket.isClosed()) {
                currentSocket.close();
            }
            currentSocket = null;
            currentReader = null;
            currentWriter = null;
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }

    private static SSLSocket createSSLSocket(String addr, int port) throws IOException {
        SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(addr, port);
        socket.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        socket.startHandshake();
        return socket;
    }

    private static void startReaderThread(String addr, int port) {
        if (currentReaderThread != null && currentReaderThread.isAlive()) {
            currentReaderThread.interrupt();
        }

        currentReaderThread = Thread.startVirtualThread(() -> {
            BufferedReader localReader = currentReader;

            try {
                String line;
                while (!shouldExit && localReader != null && (line = localReader.readLine()) != null) {
                    if (processingReconnect && (line.contains("LOGIN") || line.contains("REGISTER") || line.contains("RECONNECT"))) {
                        continue;
                    }

                    if (line.startsWith("AUTH_TOKEN ")) {
                        tokenLock.lock();
                        try {
                            serverToken = line.substring(11);
                            System.out.println("Server authentication token received");
                        } finally {
                            tokenLock.unlock();
                        }
                    }
                    else if (processingReconnect && line.startsWith("Reconnected to room:")) {
                        System.out.println("Successfully reconnected to " + currentRoom);
                        processingReconnect = false;
                    }
                    else if (processingReconnect && line.startsWith("Welcome ")) {
                        System.out.println("Successfully reconnected to server");
                        processingReconnect = false;

                        if (currentRoom != null) {
                            socketLock.lock();
                            try {
                                if (currentWriter != null) {
                                    currentWriter.println("JOIN " + currentRoom);
                                    currentWriter.flush();
                                    System.out.println("Rejoining room: " + currentRoom);
                                }
                            } finally {
                                socketLock.unlock();
                            }
                        }
                    }
                    else {
                        System.out.println(line);
                    }
                }
            } catch (IOException e) {
                if (!isReconnecting && !shouldExit) {
                    System.out.println("Connection lost: " + e.getMessage());
                    handleReconnection(addr, port);
                }
            }
        });
    }

    private static void handleReconnection(String addr, int port) {
        reconnectLock.lock();
        try {
            if (isReconnecting || shouldExit) {
                return;
            }

            isReconnecting = true;

            if (serverToken == null) {
                System.out.println("No authentication token available. Please restart the client.");
                shouldExit = true;
                return;
            }

            int attempts = 0;
            int delay = INITIAL_RECONNECT_DELAY;

            while (attempts < MAX_RECONNECT_ATTEMPTS && !shouldExit) {
                try {
                    System.out.println("Attempting to reconnect... (" + (attempts + 1) + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                    Thread.sleep(delay);

                    connectToServer(addr, port);
                    isReconnecting = false;
                    return;

                } catch (Exception e) {
                    System.out.println("Reconnection failed: " + e.getMessage());
                    attempts++;
                    delay *= 2;
                }
            }

            System.out.println("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts.");
            isReconnecting = false;
        } finally {
            reconnectLock.unlock();
        }
    }
}
