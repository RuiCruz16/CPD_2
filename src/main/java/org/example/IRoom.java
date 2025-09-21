package org.example;

import java.io.IOException;
import java.util.List;

public interface IRoom {

    public String getRoomName();

    public void addClient(ClientHandler client);

    public void removeClient(ClientHandler client);

    public List<ClientHandler> getClients();

    public List<String> getMessages();

    public void addMessage(String message) throws IOException, InterruptedException;

    public void broadcastMessage(String message);
}
