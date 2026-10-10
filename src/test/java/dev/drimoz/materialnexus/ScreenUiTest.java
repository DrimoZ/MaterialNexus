package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.client.ui.Regions;
import dev.drimoz.materialnexus.client.ui.ScreenSettings;
import dev.drimoz.materialnexus.client.ui.Theme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of the screen that can break without being seen (MNX-094): contrast, region widths, the settings file. */
class ScreenUiTest {
    @Test
    void everyThemeKeepsItsTextReadable() {
        for (Theme t : Theme.values()) {
            for (int ink : new int[] {t.text, t.muted, t.faint, t.ok, t.warn, t.bad, t.info}) {
                for (int ground : new int[] {t.bg, t.surface}) {
                    assertTrue(Theme.contrast(ink, ground) >= 4.5, t + ": " + Integer.toHexString(ink) + " on " + Integer.toHexString(ground));
                }
            }
            assertTrue(Theme.contrast(t.on, t.ok) >= 4.5, t + ": text on the primary button");
            assertTrue(Theme.contrast(t.text, t.sel) >= 4.5, t + ": text on a selected row");
        }
    }

    @Test
    void regionsFollowTheWidth() {
        assertEquals(new Regions(88, 148, 404, 264, true), Regions.of(640));
        assertEquals(new Regions(104, 176, 416, 264, false), Regions.of(960));
        assertEquals(new Regions(104, 176, 640, 360, false), Regions.of(1280));
        assertEquals(new Regions(104, 176, 640, 360, false), Regions.of(1920));
    }

    @Test
    void anUnreadableSettingsFileGivesTheDefaults() {
        assertEquals(Theme.PAPER, ScreenSettings.parseTheme("{\"theme\":\"paper\"}"));
        for (String bad : new String[] {"", "{}", "[]", "{\"theme\":\"neon\"}", "{\"theme\":3}", "not json"}) {
            assertEquals(Theme.GRAPHITE, ScreenSettings.parseTheme(bad), bad);
        }
    }
}
