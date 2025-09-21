package org.example;

import java.io.*;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Map<String, String> credentials; // username -> hashed password
    private final Map<String, DefaultRoom> roomMap;
    private final List<ClientHandler> activeClients;
    private String username;
    private IRoom currentRoom;

    private BufferedReader in;
    private PrintWriter out;
    private final Lock credentialsLock = new ReentrantLock();
    private final Lock activeClientsLock = new ReentrantLock();
    private final Lock roomMapLock = new ReentrantLock();

    private String authToken;

    public ClientHandler(Socket socket, Map<String, String> credentials,
                         Map<String, DefaultRoom> roomMap, List<ClientHandler> activeClients) {
        this.socket = socket;
        this.credentials = credentials;
        this.roomMap = roomMap;
        this.activeClients = activeClients;

    }


    @Override
    public void run() {
        try {
            setupStreams();

            int attempts = 0;
            final int MAX_ATTEMPTS = 3;
            while (!handleAuthentication()) {
                attempts++;
                out.println("Authentication failed. Attempt " + attempts + " of " + MAX_ATTEMPTS);
                if (attempts >= MAX_ATTEMPTS) {
                    out.println("Maximum authentication attempts reached. Disconnecting...");
                    socket.close();
                    return;
                }
            }

            out.println("Welcome " + username + "!");

            String input;
            while ((input = in.readLine()) != null) {
                handleCommand(input);
            }

        } catch (IOException e) {
            System.out.println("Client disconnected: " + username + " - Error: " + e.getMessage());
            out.println("Client disconnected: " + username + " - Error: " + e.getMessage());
            e.printStackTrace();
        } catch (URISyntaxException | InterruptedException e) {
            System.out.println("Error processing LLM request: " + e.getMessage());
            e.printStackTrace();
            try {
                if (out != null) {
                    out.println("Error processing your request: " + e.getMessage());
                }
            } catch (Exception ignored) {}
        } finally {
            cleanup();
        }
    }

    private void setupStreams() throws IOException {
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        socket.setSoTimeout(120000);
    }

    private boolean handleAuthentication() throws IOException {
        out.println("LOGIN <username> <password> or REGISTER <username> <password>");
        String input = in.readLine();

        if (input == null) {
            return false;
        }

        String parts[] = input.split(" ", 3);

        if (parts[0].equals("RECONNECT") && parts.length == 2) {
            return handleReconnection(parts[1]);
        } else if ((parts[0].equals("LOGIN") || parts[0].equals("REGISTER")) && parts.length == 3) {
            return handleCredentialAuth(parts[0], parts[1], parts[2]);
        } else {
            out.println("Invalid authentication format");
            return false;
        }
    }

    private boolean handleReconnection(String token) {
        // Check if the token is valid and retrieve the username
        String username = Server.getTokenManager().getUsernameFromToken(token);
        if (username == null) {
            out.println("Invalid or expired token. Please login with credentials.");
            return false;
        }

        this.username = username;
        this.authToken = token;

        ClientHandler existingClient = Server.getUserSession(username);
        if (existingClient != null) {
            this.currentRoom = existingClient.currentRoom;

            if (currentRoom != null) {
                currentRoom.removeClient(existingClient);
                currentRoom.addClient(this);
                out.println("Reconnected to room: " + currentRoom.getRoomName());
                currentRoom.broadcastMessage("[" + username + " re-enters the room]");
            }

            existingClient.closeResources();
            Server.removeUserSession(username);
        }

        Server.registerUserSession(username, this);
        return true;
    }

    private boolean handleCredentialAuth(String authType, String username, String password) {
        if ((!this.credentials.containsKey(username) || !PasswordUtil.checkCredentials(password, this.credentials.get(username))) && authType.equals("LOGIN")) {
            out.println("Invalid username or password");
            return false;
        }

        if (authType.equals("REGISTER")) {
            credentialsLock.lock();
            try {
                if (this.credentials.containsKey(username)) {
                    out.println("Username already exists. Please choose a different username.");
                    return false;
                }

                String encryptedPassword = PasswordUtil.encryptPassword(password);
                this.credentials.put(username, encryptedPassword);

                try (BufferedWriter writer = new BufferedWriter(new FileWriter("./doc/users.txt", true))) {
                    writer.newLine();
                    writer.write(username + ":" + encryptedPassword);
                } catch (IOException e) {
                    out.println("Error saving credentials. Please try again.");
                    return false;
                }

                out.println("Registration successful.");
            } finally {
                credentialsLock.unlock(); // ALWAYS unlock in finally
            }
        }

        this.username = username;
        activeClientsLock.lock();
        try {
            for (ClientHandler client : activeClients) {
                if (client.username.equals(this.username)) {
                    out.println("Error: Username already active.");
                    return false;
                }
            }
            activeClients.add(this);
        } finally {
            activeClientsLock.unlock();
        }
        Server.registerUserSession(username, this);
        this.authToken = Server.getTokenManager().generateToken(username);
        out.println("Authentication successful.");
        out.println("AUTH_TOKEN " + authToken);
        return true;
    }

    private void handleCommand(String input) throws IOException, URISyntaxException, InterruptedException {
        System.out.println("Received command from " + username + ": " + input);
        if (input.startsWith("JOIN ")) {
            String roomName = input.substring(5);
            joinRoom(roomName);
        }
        else if (input.equals("LIST")) {
            listRooms();
        }
        else if (input.startsWith("SEND ")) {
            String message = input.substring(5);
            sendMessageToRoom(message);
        }
        else if (input.equals("LEAVE")) {
            leaveRoom();
        }
        else if (input.equals("HELP")) {
            showHelp();
        }
        else if (input.equals("QUIT")) {
            out.println("Goodbye " + username + "!");
            cleanup();
        }
        else if (input.startsWith("LLM ")) {
            String command = input.substring(4);
            if (command.equals("LIST")) {
                listLLMRooms();
            }
            else if (command.startsWith("JOIN ")) {
                String roomName = command.substring(5);
                joinLLMRoom(roomName);
            }
        }
        else {
            out.println("Unknown command: " + input +
                    ". Type HELP for a list of commands.");
        }
    }


    private void joinRoom(String roomName) {
        DefaultRoom room;
        roomMapLock.lock();
        try {
            room = roomMap.computeIfAbsent(roomName, DefaultRoom::new);
        } finally {
            roomMapLock.unlock();
        }
        if (currentRoom != null) {
            currentRoom.removeClient(this);
            currentRoom.broadcastMessage("[" + username + " leaves the room]");
            out.println("Left room: " + currentRoom.getRoomName());
        }

        currentRoom = room;
        currentRoom.addClient(this);
        room.broadcastMessage("[" + username + " enters the room]");
        out.println("Joined room: " + roomName);
    }

    private void joinLLMRoom(String llmName) throws IOException, URISyntaxException, InterruptedException {
        LLMRoom room = new LLMRoom(llmName);

        if (currentRoom != null) {
            currentRoom.removeClient(this);
            currentRoom.broadcastMessage("[" + username + " leaves the room]");
            out.println("Left room: " + currentRoom.getRoomName());
        }
        currentRoom = room;
        currentRoom.addClient(this);
        out.println("Joined chat with " + currentRoom.getRoomName());


    }

    private void listLLMRooms() {
        try {
            List<String> models = LLMService.listAvailableModels("http://localhost:11434");
            if (models.isEmpty()) {
                out.println("No models available.");
            } else {
                out.println("Available LLM models:");
                for (String model : models) {
                    out.println("- " + model);
                }
            }
        } catch (Exception e) {
            out.println("Failed to fetch models: " + e.getMessage());
        }
    }


    private void listRooms() {
        roomMapLock.lock();
        try {
            if (roomMap.isEmpty()) {
                out.println("No rooms available.");
            } else {
                out.println("Available rooms:");
                for (String roomName : roomMap.keySet()) {
                    out.println("- " + roomName);
                }
            }
        } finally {
            roomMapLock.unlock();
        }
    }


    private void sendMessageToRoom(String message) throws IOException, InterruptedException {
        if (currentRoom == null) {
            out.println("You are not in a room. Use JOIN <room_name> to join a room.");
            return;
        }
        currentRoom.broadcastMessage(username + ": " + message);
        currentRoom.addMessage(username + ": " + message);

    }

    public void sendMessage(String message) {
        out.println(message);
    }

    private void leaveRoom() {
        if (currentRoom != null) {
            currentRoom.removeClient(this);
            currentRoom.broadcastMessage("[" + username + " leaves the room]");
            out.println("Left room: " + currentRoom.getRoomName());
            currentRoom = null;
        } else {
            out.println("You are not in a room.");
        }
    }

    private void showHelp() {
        out.println("Available commands:");
        out.println("JOIN <room_name> - Join a room");
        out.println("LIST - List available rooms");
        out.println("LLM LIST - List available LLM rooms");
        out.println("LLM JOIN <llm_name> - Join a LLM room");
        out.println("SEND <message> - Send a message to the current room");
        out.println("LEAVE - Leave the current room");
        out.println("HELP - Show this help message");
        out.println("QUIT - Disconnect from the server");
    }

    private void closeResources() {
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing resources: " + e.getMessage());
        }
    }

    private void cleanup() {
        if (currentRoom != null) {
            currentRoom.removeClient(this);
            currentRoom.broadcastMessage("[" + username + " leaves the room]");
        }

        activeClientsLock.lock();
        try {
            activeClients.remove(this);
        } finally {
            activeClientsLock.unlock();
        }

        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("Error closing socket for " + username);
        }
    }

}
