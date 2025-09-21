package org.example;

import java.util.ArrayList;
import java.util.List;

public class DefaultRoom implements IRoom{
    private String roomName;
    private List<ClientHandler> clients;
    private List<String> messages;

    public DefaultRoom(String roomName) {
        this.roomName = roomName;
        this.clients = new ArrayList<>();
        this.messages = new ArrayList<>();
    }

    public synchronized String getRoomName() {
        return roomName;
    }

    public synchronized void addClient(ClientHandler client) {
        clients.add(client);
    }

    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    public synchronized List<ClientHandler> getClients() {
        return clients;
    }

    public synchronized List<String> getMessages() {
        return messages;
    }

    public synchronized void addMessage(String message) {
        messages.add(message);
    }

    public synchronized void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }
}
