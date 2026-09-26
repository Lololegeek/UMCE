package io.umce.core.version;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.umce.api.version.MinecraftRelease;
import io.umce.api.version.ReleaseType;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads stable releases from Mojang's official launcher manifest. The manifest
 * index is cheap to refresh; per-version details are fetched only on demand.
 */
public final class MinecraftVersionRegistry {
    public static final URI OFFICIAL_MANIFEST = URI.create(
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

    private final URI manifestUri;
    private final MetadataFetcher fetcher;
    private final VersionSupportCatalog supportCatalog;
    private Map<String, ManifestEntry> entries = Collections.emptyMap();
    private List<MinecraftRelease> releases = Collections.emptyList();

    public MinecraftVersionRegistry() {
        this(OFFICIAL_MANIFEST, new HttpMetadataFetcher(), VersionSupportCatalog.fromClasspath());
    }

    public MinecraftVersionRegistry(URI manifestUri, MetadataFetcher fetcher) {
        this(manifestUri, fetcher, VersionSupportCatalog.fromClasspath());
    }

    public MinecraftVersionRegistry(URI manifestUri, MetadataFetcher fetcher, VersionSupportCatalog supportCatalog) {
        if (manifestUri == null) throw new IllegalArgumentException("manifestUri must not be null");
        if (fetcher == null) throw new IllegalArgumentException("fetcher must not be null");
        if (supportCatalog == null) throw new IllegalArgumentException("supportCatalog must not be null");
        this.manifestUri = manifestUri;
        this.fetcher = fetcher;
        this.supportCatalog = supportCatalog;
    }

    /** Refreshes the index and returns every stable release listed by Mojang. */
    public synchronized List<MinecraftRelease> refresh() throws IOException {
        JsonObject root = fetcher.fetch(manifestUri);
        JsonArray versions = requiredArray(root, "versions", manifestUri.toString());
        Map<String, ManifestEntry> nextEntries = new LinkedHashMap<String, ManifestEntry>();
        List<MinecraftRelease> nextReleases = new ArrayList<MinecraftRelease>();

        for (JsonElement element : versions) {
            if (!element.isJsonObject()) throw new IOException("Version manifest contains a non-object entry");
            JsonObject json = element.getAsJsonObject();
            String id = requiredString(json, "id", "version entry");
            String type = requiredString(json, "type", id);
            URI profileUri = parseUri(requiredString(json, "url", id), id);
            Instant releaseDate = parseInstant(optionalString(json, "releaseTime"), id);
            ManifestEntry entry = new ManifestEntry(id, type, profileUri, releaseDate);
            if (nextEntries.put(id, entry) != null) throw new IOException("Duplicate Minecraft version id: " + id);
            if ("release".equals(type)) {
                MinecraftRelease.Builder builder = MinecraftRelease.builder(id)
                        .releaseType(ReleaseType.RELEASE)
                        .releaseDate(releaseDate);
                supportCatalog.applyTo(builder, id);
                nextReleases.add(builder.build());
            }
        }

        this.entries = Collections.unmodifiableMap(nextEntries);
        this.releases = Collections.unmodifiableList(nextReleases);
        return this.releases;
    }

    public synchronized List<MinecraftRelease> getReleases() {
        return releases;
    }

    /** Fetches official per-version metadata for one indexed stable release. */
    public synchronized MinecraftRelease getDetails(String versionId) throws IOException {
        ManifestEntry entry = entries.get(versionId);
        if (entry == null || !"release".equals(entry.type)) {
            throw new IllegalArgumentException("Stable release is not present in the refreshed official manifest: " + versionId);
        }

        JsonObject profile = fetcher.fetch(entry.profileUri);
        String profileId = requiredString(profile, "id", versionId + " profile");
        if (!versionId.equals(profileId)) {
            throw new IOException("Official metadata id mismatch: requested " + versionId + " but received " + profileId);
        }
        MinecraftRelease.Builder builder = MinecraftRelease.builder(entry.id)
                .releaseType(ReleaseType.RELEASE)
                .releaseDate(entry.releaseDate);

        JsonObject javaVersion = objectOrNull(profile, "javaVersion");
        if (javaVersion != null && hasPrimitive(javaVersion, "majorVersion")) {
            builder.requiredJavaVersion(positiveInteger(javaVersion.get("majorVersion"), versionId + " Java version"));
        }

        JsonObject downloads = objectOrNull(profile, "downloads");
        JsonObject server = downloads == null ? null : objectOrNull(downloads, "server");
        if (server != null && hasPrimitive(server, "url")) {
            try {
                builder.serverJar(new URL(server.get("url").getAsString()));
            } catch (MalformedURLException | IllegalStateException exception) {
                throw new IOException("Invalid server download URL in " + versionId + " metadata", exception);
            }
        }

        // Protocol, data version, mappings, and loader matrix are not published
        // in these Mojang fields. Keep them absent until a verified source exists.
        supportCatalog.applyTo(builder, versionId);
        return builder.build();
    }

    private static JsonArray requiredArray(JsonObject object, String key, String source) throws IOException {
        if (!hasPrimitive(object, key) && (object == null || !object.has(key) || !object.get(key).isJsonArray())) {
            throw new IOException("Missing " + key + " array in " + source);
        }
        return object.getAsJsonArray(key);
    }

    private static String requiredString(JsonObject object, String key, String source) throws IOException {
        String value = optionalString(object, key);
        if (value == null || value.trim().isEmpty()) throw new IOException("Missing " + key + " in " + source);
        return value;
    }

    private static String optionalString(JsonObject object, String key) {
        if (object == null || !hasPrimitive(object, key)) return null;
        JsonElement value = object.get(key);
        return value.getAsString();
    }

    private static boolean hasPrimitive(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive();
    }

    private static JsonObject objectOrNull(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonObject()) return null;
        return object.getAsJsonObject(key);
    }

    private static URI parseUri(String value, String versionId) throws IOException {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("HTTPS required");
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid official metadata URL for " + versionId, exception);
        }
    }

    private static Instant parseInstant(String value, String versionId) throws IOException {
        if (value == null) throw new IOException("Missing releaseTime for " + versionId);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IOException("Invalid releaseTime for " + versionId, exception);
        }
    }

    private static int positiveInteger(JsonElement element, String field) throws IOException {
        try {
            int value = element.getAsInt();
            if (value < 1) throw new NumberFormatException("must be positive");
            return value;
        } catch (NumberFormatException | UnsupportedOperationException exception) {
            throw new IOException("Invalid " + field, exception);
        }
    }

    private static final class ManifestEntry {
        private final String id;
        private final String type;
        private final URI profileUri;
        private final Instant releaseDate;

        private ManifestEntry(String id, String type, URI profileUri, Instant releaseDate) {
            this.id = id;
            this.type = type;
            this.profileUri = profileUri;
            this.releaseDate = releaseDate;
        }
    }
}
