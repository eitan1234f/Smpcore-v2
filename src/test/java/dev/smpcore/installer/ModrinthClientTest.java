package dev.smpcore.installer;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthClientTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = ModrinthClientTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesSearchResults() throws IOException {
        ModrinthClient.SearchResult r = ModrinthClient.parseSearch(JsonParser.parseString(resource("modrinth-search.json")));
        assertEquals(1234, r.total());
        assertEquals(2, r.hits().size());
        ModrinthClient.Hit first = r.hits().get(0);
        assertEquals("AANobbMI", first.id());
        assertEquals("Sodium-but-a-plugin", first.title());
        assertEquals(1_500_000L, first.downloads());
        assertTrue(first.categories().contains("utility"));
    }

    @Test
    void parsesVersionsAndPicksPrimaryFile() throws IOException {
        var arr = JsonParser.parseString(resource("modrinth-versions.json")).getAsJsonArray();
        ModrinthClient.Version beta = ModrinthClient.parseVersion(arr.get(0).getAsJsonObject());
        ModrinthClient.Version release = ModrinthClient.parseVersion(arr.get(1).getAsJsonObject());
        assertEquals("beta", beta.type());
        assertEquals("plugin-2.0.0.jar", release.primaryFile().filename());
        assertFalse(release.primaryFile().sha512().isEmpty());
        assertEquals(1, release.dependencies().size());
        assertEquals("required", release.dependencies().get(0).type());

        // newest release wins over a newer beta
        assertEquals(release.id(), PluginInstaller.pickBest(List.of(beta, release)).id());
        assertEquals(beta.id(), PluginInstaller.pickBest(List.of(beta)).id());
    }
}
