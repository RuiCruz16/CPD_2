package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.io.IOException;

public class LLMService {
    private final HttpClient httpClient;
    private final String host;
    private final String modelName;
    private final List<Map<String, String>> chatHistory = new ArrayList<>();

    public LLMService(String host, String modelName) throws IOException, InterruptedException {
        this.httpClient = HttpClient.newHttpClient();
        this.host = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        this.modelName = modelName;

        if (!isModelAvailable()) {
            throw new IllegalStateException("Model " + modelName + " is not installed.");
        }
    }

    private boolean isModelAvailable() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + "/api/tags"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        JSONArray models = json.getJSONArray("models");

        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.getJSONObject(i);
            String name = model.getString("name");
            if (name.replace(":latest", "").equals(modelName)) {
                return true;
            }
        }
        return false;
    }

    public String sendMessage(String message) throws IOException, InterruptedException {
        chatHistory.add(Map.of("role", "user", "content", message));

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", modelName);

        JSONArray messages = new JSONArray();
        for (Map<String, String> entry : chatHistory) {
            JSONObject messageObj = new JSONObject();
            messageObj.put("role", entry.get("role"));
            messageObj.put("content", entry.get("content"));
            messages.put(messageObj);
        }
        requestBody.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        StringBuilder fullResponse = new StringBuilder();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        String[] lines = response.body().split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            JSONObject jsonResponse = new JSONObject(line);
            JSONObject messageObj = jsonResponse.getJSONObject("message");
            String content = messageObj.getString("content");
            fullResponse.append(content);

            if (jsonResponse.getBoolean("done")) {
                break;
            }
        }

        String reply = fullResponse.toString();
        chatHistory.add(Map.of("role", "assistant", "content", reply));
        return reply;
    }

    public static List<String> listAvailableModels(String host) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + "/api/tags"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        JSONArray models = json.getJSONArray("models");

        List<String> modelNames = new ArrayList<>();
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.getJSONObject(i);
            modelNames.add(model.getString("name"));
        }

        return modelNames;
    }
}
