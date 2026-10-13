package dev.drimoz.materialnexus.client.ui;

/**
 * Colour roles and sizes of the Material Nexus screen (MNX-094, docs/22). A view reads the roles of the theme in use
 * ({@link #BG}, {@link #TEXT}...) and never a raw colour; a theme is one row of values. Greys carry no tint, so item
 * icons are not judged against a coloured surround, and the slot well is mid grey so dark and light items both read.
 */
public enum Theme {
    GRAPHITE(0x1C1C1C, 0x252525, 0x2F2F2F, 0x3D3D3D, 0x383838, 0x747474, 0x7A7A7A, 0xEAEAEA, 0xABABAB, 0x8C8C8C, 0x5CC878, 0xE8B23A, 0xEA6E69, 0x6AA7F2, 0x141414),
    WORKSHOP(0x2B2B2B, 0x353535, 0x404040, 0x505050, 0x4A4A4A, 0x8A8A8A, 0x8B8B8B, 0xF0F0F0, 0xBDBDBD, 0xA3A3A3, 0x6FD389, 0xEDBE55, 0xF0827E, 0x7DB4F5, 0x141414),
    PAPER(0xF3F3F1, 0xFFFFFF, 0xE7E7E4, 0xD6D6D2, 0xD0D0CC, 0x8A8A8A, 0x8B8B8B, 0x1F1F1F, 0x565656, 0x6B6B6B, 0x1F7A3A, 0x8A5A00, 0xB3261E, 0x1F5FBF, 0xFFFFFF),
    MIDNIGHT(0x0F1115, 0x171A21, 0x1F232C, 0x1E2B40, 0x2A2F3A, 0x6B7489, 0x22222A, 0xE6E8EC, 0xA3A9B5, 0x858C9A, 0x4CC46A, 0xE8B23A, 0xE5645F, 0x5B9DF0, 0x0F1115);

    /** Opaque ARGB. Surfaces from far to near, then lines, text levels, meanings, and text on a colour fill. */
    public final int bg, surface, raised, sel, border, ctrl, well, text, muted, faint, ok, warn, bad, info, on;

    Theme(int bg, int surface, int raised, int sel, int border, int ctrl, int well, int text, int muted, int faint, int ok, int warn, int bad, int info, int on) {
        this.bg = opaque(bg); this.surface = opaque(surface); this.raised = opaque(raised); this.sel = opaque(sel);
        this.border = opaque(border); this.ctrl = opaque(ctrl); this.well = opaque(well);
        this.text = opaque(text); this.muted = opaque(muted); this.faint = opaque(faint);
        this.ok = opaque(ok); this.warn = opaque(warn); this.bad = opaque(bad); this.info = opaque(info); this.on = opaque(on);
    }

    private static int opaque(int rgb) { return 0xFF000000 | rgb; }

    /** Roles of the theme in use. {@link #ACCENT} is the main action: the kept, validating green. */
    public static int BG, SURFACE, RAISED, SEL, BORDER, CTRL, WELL, TEXT, MUTED, FAINT, OK, WARN, BAD, INFO, ON, ACCENT;

    /** Text colours inside tooltips (RGB): the vanilla tooltip is dark in every theme, so these do not follow it. */
    /** The grey inside a vanilla slot, the same in every theme: what a recipe viewer's slots and texts are made to sit on (MNX-088). */
    public static final int TRAY = 0xFF8B8B8B;
    public static final int TIP_MUTED = 0xAAAAAA, TIP_FAINT = 0x888888, TIP_INFO = 0x88AACC, TIP_BAD = 0xCC8888;

    /** Spacing is one of 2, 4, 8, 12, 16, 24; heights are these. */
    public static final int ROW_TEXT = 16, CONTROL = 18, BAR_TOP = 24, BAR_BOTTOM = 28;

    /** Height of a row with an item, and what centres its content in it: 22 and 0 when compact, 26 and 2 when comfortable. */
    public static int ROW_ITEM = 22, ROW_PAD = 0;

    public static void density(boolean comfortable) {
        ROW_ITEM = comfortable ? 26 : 22;
        ROW_PAD = comfortable ? 2 : 0;
    }

    private static Theme current;

    static { use(GRAPHITE); }

    public static Theme current() { return current; }

    public static void use(Theme theme) {
        current = theme;
        BG = theme.bg; SURFACE = theme.surface; RAISED = theme.raised; SEL = theme.sel; BORDER = theme.border; CTRL = theme.ctrl;
        WELL = theme.well; TEXT = theme.text; MUTED = theme.muted; FAINT = theme.faint;
        OK = theme.ok; WARN = theme.warn; BAD = theme.bad; INFO = theme.info; ON = theme.on; ACCENT = theme.ok;
    }

    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    /** {@code a} moved towards {@code b} by {@code t} (0 to 1), opaque: hover and tinted fills without transparency. */
    public static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** WCAG 2 contrast ratio of two opaque colours, 1 to 21. */
    public static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static double luminance(int rgb) {
        double[] c = {(rgb >> 16 & 255) / 255.0, (rgb >> 8 & 255) / 255.0, (rgb & 255) / 255.0};
        for (int i = 0; i < 3; i++) c[i] = c[i] <= 0.03928 ? c[i] / 12.92 : Math.pow((c[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }
}
