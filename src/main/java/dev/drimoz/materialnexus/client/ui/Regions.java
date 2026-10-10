package dev.drimoz.materialnexus.client.ui;

/**
 * Widths of the fixed regions of the screen for a GUI width (MNX-094, docs/22 section 5): rail, list, main table and
 * inspector. Table and inspector grow together up to their caps; past that a margin is left rather than stretching.
 * Under {@link #WIDE} the inspector does not fit beside the table and is laid over it.
 */
public record Regions(int rail, int list, int table, int inspector, boolean inspectorOverlay) {
    public static final int WIDE = 900;
    private static final int TABLE_MAX = 640, INSPECTOR_MIN = 264, INSPECTOR_MAX = 360;

    public static Regions of(int width) {
        boolean wide = width >= WIDE;
        int rail = wide ? 104 : 88;
        int list = wide ? 176 : 148;
        int left = width - rail - list;
        if (!wide) return new Regions(rail, list, Math.max(0, left), INSPECTOR_MIN, true);
        // 264 at a 960 screen, 360 at 1280: the inspector takes 96 of every 320 px gained.
        int inspector = Math.clamp(INSPECTOR_MIN + (left - 680) * 96 / 320, INSPECTOR_MIN, INSPECTOR_MAX);
        return new Regions(rail, list, Math.min(TABLE_MAX, left - inspector), inspector, false);
    }
}
