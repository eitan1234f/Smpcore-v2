package dev.smpcore.installer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal Modrinth API v2 client (https://docs.modrinth.com/api/).
 * Searches every server plugin on Modrinth - Paper, Spigot, Bukkit, Purpur and Folia loaders.
 */
public final class ModrinthClient {
    public static final String API = "https://api.modrinth.com/v2";
    public static final List<String> SERVER_LOADERS = List.of("paper", "spigot", "bukkit", "purpur", "folia");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String userAgent;

    public ModrinthClient(String version) {
        this.userAgent = "eitan1234f/SmpCore/" + version + " (github.com/eitan1234f/Smpcore-v2)";
    }

    // ------------------------------------------------------------------ models

    public record Hit(String id, String slug, String title, String description, String author, long downloads, long follows,
                      List<String> categories, String updated, String iconUrl) {
    }

    public record SearchResult(List<Hit> hits, int total, int offset, int limit) {
    }

    public record FileInfo(String url, String filename, boolean primary, long size, String sha512) {
    }

    public record Dependency(String projectId, String versionId, String type) {
    }

    public record Version(String id, String projectId, String name, String number, String type, String published,
                          List<String> loaders, List<String> gameVersions, List<FileInfo> files, List<Dependency> dependencies) {
        public FileInfo primaryFile() {
            for (FileInfo f : files) {
                if (f.primary()) return f;
            }
            return files.isEmpty() ? null : files.get(0);
        }
    }

    public record Project(String id, String slug, String title, String description, long downloads, long follows,
                          List<String> categories, List<String> loaders, String sourceUrl, String issuesUrl, String updated, String license) {
    }

    // ------------------------------------------------------------------ calls

    private CompletableFuture<JsonElement> get(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET().build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            if (res.statusCode() == 429) {
                throw new IllegalStateException("Modrinth rate limit reached, try again in a minute");
            }
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("Modrinth returned HTTP " + res.statusCode());
            }
            return JsonParser.parseString(res.body());
        });
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * @param sort one of relevance, downloads, follows, newest, updated
     * @param category a Modrinth category tag or null
     * @param gameVersion only show plugins supporting this version, or null for all
     */
    public CompletableFuture<SearchResult> search(String query, String sort, String category, String gameVersion, int offset, int limit) {
        StringBuilder facets = new StringBuilder("[[");
        for (int i = 0; i < SERVER_LOADERS.size(); i++) {
            if (i > 0) facets.append(',');
            facets.append("\"categories:").append(SERVER_LOADERS.get(i)).append('"');
        }
        facets.append(']');
        if (category != null && !category.isEmpty()) {
            facets.append(",[\"categories:").append(category).append("\"]");
        }
        if (gameVersion != null && !gameVersion.isEmpty()) {
            facets.append(",[\"versions:").append(gameVersion).append("\"]");
        }
        facets.append(']');
        String url = API + "/search?query=" + enc(query == null ? "" : query)
                + "&facets=" + enc(facets.toString())
                + "&index=" + enc(sort == null ? "relevance" : sort)
                + "&offset=" + offset + "&limit=" + limit;
        return get(url).thenApply(json -> {
            JsonObject o = json.getAsJsonObject();
            List<Hit> hits = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("hits")) {
                JsonObject h = e.getAsJsonObject();
                hits.add(new Hit(str(h, "project_id"), str(h, "slug"), str(h, "title"), str(h, "description"), str(h, "author"),
                        num(h, "downloads"), num(h, "follows"), strings(h.get("display_categories") != null ? h.get("display_categories") : h.get("categories")),
                        str(h, "date_modified"), str(h, "icon_url")));
            }
            return new SearchResult(hits, (int) num(o, "total_hits"), (int) num(o, "offset"), (int) num(o, "limit"));
        });
    }

    public CompletableFuture<Project> project(String idOrSlug) {
        return get(API + "/project/" + enc(idOrSlug)).thenApply(json -> {
            JsonObject o = json.getAsJsonObject();
            JsonObject license = o.has("license") && o.get("license").isJsonObject() ? o.getAsJsonObject("license") : null;
            return new Project(str(o, "id"), str(o, "slug"), str(o, "title"), str(o, "description"), num(o, "downloads"), num(o, "followers"),
                    strings(o.get("categories")), strings(o.get("loaders")), str(o, "source_url"), str(o, "issues_url"), str(o, "updated"),
                    license == null ? "" : str(license, "id"));
        });
    }

    /** Versions of a project that run on server loaders, newest first. */
    public CompletableFuture<List<Version>> versions(String projectId, String gameVersion) {
        StringBuilder loaders = new StringBuilder("[");
        for (int i = 0; i < SERVER_LOADERS.size(); i++) {
            if (i > 0) loaders.append(',');
            loaders.append('"').append(SERVER_LOADERS.get(i)).append('"');
        }
        loaders.append(']');
        String url = API + "/project/" + enc(projectId) + "/version?loaders=" + enc(loaders.toString())
                + (gameVersion == null ? "" : "&game_versions=" + enc("[\"" + gameVersion + "\"]"));
        return get(url).thenApply(json -> {
            List<Version> out = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray()) {
                out.add(parseVersion(e.getAsJsonObject()));
            }
            return out;
        });
    }

    public CompletableFuture<Version> version(String versionId) {
        return get(API + "/version/" + enc(versionId)).thenApply(json -> parseVersion(json.getAsJsonObject()));
    }

    private static Version parseVersion(JsonObject v) {
        List<FileInfo> files = new ArrayList<>();
        for (JsonElement f : v.getAsJsonArray("files")) {
            JsonObject fo = f.getAsJsonObject();
            JsonObject hashes = fo.getAsJsonObject("hashes");
            files.add(new FileInfo(str(fo, "url"), str(fo, "filename"), fo.has("primary") && fo.get("primary").getAsBoolean(),
                    num(fo, "size"), hashes == null ? "" : str(hashes, "sha512")));
        }
        List<Dependency> deps = new ArrayList<>();
        if (v.has("dependencies") && v.get("dependencies").isJsonArray()) {
            for (JsonElement d : v.getAsJsonArray("dependencies")) {
                JsonObject dobj = d.getAsJsonObject();
                deps.add(new Dependency(str(dobj, "project_id"), str(dobj, "version_id"), str(dobj, "dependency_type")));
            }
        }
        return new Version(str(v, "id"), str(v, "project_id"), str(v, "name"), str(v, "version_number"), str(v, "version_type"),
                str(v, "date_published"), strings(v.get("loaders")), strings(v.get("game_versions")), files, deps);
    }

    /** Downloads to a temp file next to the target, verifies SHA-512, then moves it into place. */
    public CompletableFuture<Path> download(FileInfo file, Path target) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(file.url()))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofMinutes(3))
                .GET().build();
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).thenApply(res -> {
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("Download failed with HTTP " + res.statusCode());
            }
            try (InputStream in = res.body()) {
                Files.createDirectories(target.getParent());
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                if (!file.sha512().isEmpty()) {
                    String actual = sha512(temp);
                    if (!actual.equalsIgnoreCase(file.sha512())) {
                        Files.deleteIfExists(temp);
                        throw new IllegalStateException("Checksum mismatch for " + file.filename());
                    }
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                return target;
            } catch (IOException e) {
                throw new IllegalStateException("Could not save " + file.filename() + ": " + e.getMessage(), e);
            }
        });
    }

    private static String sha512(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    // ------------------------------------------------------------------ json helpers

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static long num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsLong();
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e instanceof JsonArray arr) {
            for (JsonElement x : arr) out.add(x.getAsString());
        }
        return out;
    }
}
