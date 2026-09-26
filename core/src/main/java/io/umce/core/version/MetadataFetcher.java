package io.umce.core.version;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;

/** Injectable transport boundary for official metadata and deterministic tests. */
public interface MetadataFetcher {
    JsonObject fetch(URI uri) throws IOException;
}
