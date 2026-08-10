package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small OpenAI-compatible client used by DevGram AI chat. */
public final class DevGramAiClient {
    public interface Callback { void done(String result, Throwable error); }

    private DevGramAiClient() {}

    public static boolean isConfigured() {
        return !MessagesController.getGlobalMainSettings().getString("dg_aiKey", "").trim().isEmpty();
    }

    public static void generate(String prompt, Callback callback) {
        new Thread(() -> {
            String result = null;
            Throwable error = null;
            HttpURLConnection connection = null;
            try {
                String endpoint = MessagesController.getGlobalMainSettings().getString(
                        "dg_aiEndpoint", "https://api.openai.com/v1/chat/completions");
                String model = MessagesController.getGlobalMainSettings().getString("dg_aiModel", "gpt-4o-mini");
                String key = MessagesController.getGlobalMainSettings().getString("dg_aiKey", "");
                if (key.trim().isEmpty()) throw new IllegalStateException("Сначала укажите API-ключ");
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + key);
                connection.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("temperature", 0.7);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "user").put("content", prompt));
                body.put("messages", messages);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + response);
                result = new JSONObject(response.toString()).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content");
            } catch (Throwable t) {
                error = t;
            } finally {
                if (connection != null) connection.disconnect();
            }
            String finalResult = result;
            Throwable finalError = error;
            AndroidUtilities.runOnUIThread(() -> callback.done(finalResult, finalError));
        }, "DevGramAI").start();
    }
}
