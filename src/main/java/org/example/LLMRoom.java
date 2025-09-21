package org.example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LLMRoom implements IRoom {
    private final String llmName;
    private final List<ClientHandler> clients = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private LLMService llmService;

    public LLMRoom(String llmName) throws IOException, InterruptedException {
        this.llmName = llmName;
        initializeLLM();
    }

    private void initializeLLM() throws IOException, InterruptedException {
        String host = "http://localhost:11434";
        this.llmService = new LLMService(host, llmName);
    }

    public synchronized String getRoomName() {
        return llmName;
    }

    public synchronized void addClient(ClientHandler client) {
        clients.add(client);
    }

    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    public synchronized List<ClientHandler> getClients() {
        return new ArrayList<>(clients);
    }

    public synchronized List<String> getMessages() {
        return new ArrayList<>(messages);
    }

    public synchronized void addMessage(String message) {
        messages.add(message);
        try {
            String response = llmService.sendMessage(message);
            System.out.println("LLM Response: " + response);
            broadcastMessage("Bot" + ": " + response);
        } catch (Exception e) {
            broadcastMessage("Error: " + e.getMessage());
        }
    }

    public synchronized void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }
}
