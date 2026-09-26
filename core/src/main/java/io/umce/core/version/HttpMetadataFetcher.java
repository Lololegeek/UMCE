package io.umce.core.version;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class HttpMetadataFetcher implements MetadataFetcher {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    @Override
    public JsonObject fetch(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "UMCE-VersionRegistry/0.1 (Minecraft metadata client)");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Metadata request failed with HTTP " + status + " for " + uri);
            }
            try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                try {
                    return new JsonParser().parse(reader).getAsJsonObject();
                } catch (JsonParseException | IllegalStateException exception) {
                    throw new IOException("Invalid JSON metadata from " + uri, exception);
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
