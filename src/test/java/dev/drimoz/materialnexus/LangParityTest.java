package dev.drimoz.materialnexus;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** CLAUDE.md: every user-visible string exists in en_us.json and fr_fr.json. */
class LangParityTest {
    @Test
    void englishAndFrenchHaveTheSameKeys() throws IOException {
        assertEquals(keys("en_us"), keys("fr_fr"));
    }

    private static Set<String> keys(String lang) throws IOException {
        try (InputStream in = LangParityTest.class.getResourceAsStream("/assets/materialnexus/lang/" + lang + ".json")) {
            assertNotNull(in, lang);
            return new TreeSet<>(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().keySet());
        }
    }
}
