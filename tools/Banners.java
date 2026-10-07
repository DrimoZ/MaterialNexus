import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/**
 * CurseForge and Modrinth art in Minecraft's font, on the game's own captures - Laser Assembly's and Portable
 * Beacons' {@code tools/Banners.java}, so the mods' pages share one look. No game version is written on any image:
 * the art stays true across ports.
 *
 * <pre>
 *   ./gradlew runUiShots -Pstore      (the captures: the dev world as a fresh pack sees it, see client/UiShots.java)
 *   java tools/Banners.java run-ui/screenshots run-ui/store-art
 * </pre>
 *
 * The screen fills the window (2000 x 1070 at GUI scale 2), so each screen image is a part of it, cut 1:1 so no
 * pixel blurs; the boxes below follow the layout at that size. The font comes from the game jar ModDevGradle already
 * built: it is not committed. CurseForge's description editor refuses any image wider than 850 px.
 */
public class Banners {

    static final int W = 850;
    static final Color INK = new Color(14, 15, 18);
    /** The screen's accent blue, as on the logo's light. */
    static final Color ACCENT = new Color(0x5A9BE6);
    static final Color SUB = new Color(0xBFD8F5);
    static final int COPPER = 0xD9803F;
    static final String TEMPLATES = "src/main/resources/assets/materialnexus/textures/item/template/";

    static BufferedImage font;
    static final int[] widths = new int[256];

    public static void main(String[] args) throws Exception {
        File shots = new File(args[0]), out = new File(args[1]);
        out.mkdirs();
        loadFont();

        banner(shots, out, "mnx_3_matrix");
        header(shots, out, "header_find", "mnx_3_matrix", 0.35, "Finds the duplicates", "Tags and item names, variants set aside");
        header(shots, out, "header_choose", "mnx_2_material", 0.3, "You choose", "Click, triage with 1-9, or rank the mods");
        header(shots, out, "header_preview", "mnx_6_preview", 0.2, "Preview, then apply", "Every tag and recipe change, before it is written");
        header(shots, out, "header_process", "mnx_5_process", 0.3, "Fills the gaps", "Machine recipes by example, items for missing forms");
        header(shots, out, "header_control", "mnx_8_home_preview", 0.2, "Always reversible", "Discard, back to default, any past state");
        header(shots, out, "header_data", "mnx_11_data", 0.3, "For pack makers", "Rules as data, edited in game, KubeJS bindings");

        createdSheet(out, "items_created", "Items it can create, tinted per material", 5);

        // Parts of the window, 1:1: x0, y0, x1, y1 in the capture.
        screen(shots, out, "screen_home", "mnx_1_home", 165, 55, 960, 350);
        screen(shots, out, "screen_priority", "mnx_1_home", 985, 55, 1690, 440);
        screen(shots, out, "screen_material", "mnx_2_material", 505, 48, 1355, 740);
        screen(shots, out, "screen_matrix", "mnx_3_matrix", 160, 48, 1010, 720);
        screen(shots, out, "screen_triage", "mnx_7_triage", 175, 60, 800, 420);
        screen(shots, out, "screen_process", "mnx_5_process", 165, 55, 1015, 660);
        screen(shots, out, "screen_preview", "mnx_6_preview", 1101, 48, 1951, 300);
        screen(shots, out, "screen_presets", "mnx_9_presets", 160, 60, 1010, 540);
        screen(shots, out, "screen_pending", "mnx_10_pending", 1101, 48, 1951, 330);
        screen(shots, out, "screen_data", "mnx_11_data", 160, 48, 1010, 720);
    }

    /** The top banner: the title large, the logo beside it, on a capture. */
    static void banner(File shots, File out, String shot) throws Exception {
        int h = 280;
        BufferedImage b = blur(cover(ImageIO.read(new File(shots, shot + ".png")), W, h, 0.4));
        Graphics2D g = b.createGraphics();
        g.setColor(alpha(INK, 170));
        g.fillRect(0, 0, W, h);
        g.setPaint(new GradientPaint(0, 0, alpha(INK, 235), W * 0.8f, 0, alpha(INK, 60)));
        g.fillRect(0, 0, W, h);
        // The logo is drawn at 512 for this: smoothing, unlike the pixel art, only helps it shrink.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(ImageIO.read(new File("src/main/resources/logo.png")), W - 24 - 232, 24, 232, 232, null);
        int x = 34;
        text(g, "Material Nexus", x, 52, 6, Color.WHITE);
        text(g, "One copper ingot, not five.", x + 2, 128, 3, new Color(236, 236, 240));
        text(g, "Choose, preview, apply.", x + 2, 172, 2, SUB);
        text(g, "NeoForge", x + 2, 226, 2, alpha(new Color(220, 220, 220), 220));
        g.dispose();
        ImageIO.write(b, "png", new File(out, "banner.png"));
    }

    /** A section header: a blurred slice of a capture behind a title. */
    static void header(File shots, File out, String name, String shot, double fy, String title, String subtitle) throws Exception {
        int h = 100;
        BufferedImage img = blur(cover(ImageIO.read(new File(shots, shot + ".png")), W, h, fy));
        Graphics2D g = img.createGraphics();
        g.setColor(alpha(INK, 150));
        g.fillRect(0, 0, W, h);
        g.setPaint(new GradientPaint(0, 0, alpha(INK, 210), W * 0.75f, 0, alpha(INK, 30)));
        g.fillRect(0, 0, W, h);
        g.setColor(ACCENT);
        g.fillRect(0, 0, 6, h);
        text(g, title, 28, 20, 4, Color.WHITE);
        text(g, subtitle, 30, 64, 2, SUB);
        g.dispose();
        ImageIO.write(img, "png", new File(out, name + ".png"));
    }

    /** Every template of created items, tinted copper as the game tints them, enlarged without smoothing. */
    static void createdSheet(File out, String name, String title, int cols) throws Exception {
        String lang = Files.readString(Path.of("src/main/resources/assets/materialnexus/lang/en_us.json"));
        File[] templates = new File(TEMPLATES).listFiles((d, n) -> n.endsWith(".png"));
        java.util.Arrays.sort(templates);
        int cell = W / cols, icon = 64, rowH = icon + 36, top = 64;
        int rows = (templates.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(W, top + rows * rowH + 12, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(40, 44, 52), 0, img.getHeight(), new Color(18, 19, 23)));
        g.fillRect(0, 0, W, img.getHeight());
        g.setColor(ACCENT);
        g.fillRect(0, 0, 6, img.getHeight());
        text(g, title, 28, 18, 3, Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < templates.length; i++) {
            int cx = (i % cols) * cell, cy = top + (i / cols) * rowH;
            g.setColor(new Color(255, 255, 255, 14));
            g.fillRect(cx + (cell - icon) / 2 - 8, cy - 4, icon + 16, icon + 8);
            g.drawImage(tint(ImageIO.read(templates[i]), COPPER), cx + (cell - icon) / 2, cy, icon, icon, null);
            String form = templates[i].getName().replace(".png", "");
            String label = label(lang, form);
            int scale = width(label) * 2 > cell - 12 ? 1 : 2;
            text(g, label, cx + (cell - width(label) * scale) / 2, cy + icon + 10, scale, SUB);
        }
        g.dispose();
        ImageIO.write(img, "png", new File(out, name + ".png"));
    }

    /** A grayscale template multiplied by a colour, as the game's item colour does. */
    static BufferedImage tint(BufferedImage gray, int rgb) {
        BufferedImage o = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < gray.getHeight(); y++)
            for (int x = 0; x < gray.getWidth(); x++) {
                int p = gray.getRGB(x, y), v = p & 0xFF;
                int r = ((rgb >> 16) & 0xFF) * v / 255, gr = ((rgb >> 8) & 0xFF) * v / 255, b = (rgb & 0xFF) * v / 255;
                o.setRGB(x, y, (p & 0xFF000000) | (r << 16) | (gr << 8) | b);
            }
        return o;
    }

    static String label(String lang, String form) {
        var m = java.util.regex.Pattern.compile("\"materialnexus\\.form\\." + form + "\"\\s*:\\s*\"([^\"]*)\"").matcher(lang);
        return m.find() ? m.group(1) : form;
    }

    /** One part of a capture, {@code x0, y0, x1, y1}, 1:1. */
    static void screen(File shots, File out, String name, String shot, int x0, int y0, int x1, int y1) throws Exception {
        BufferedImage capture = ImageIO.read(new File(shots, shot + ".png"));
        x1 = Math.min(capture.getWidth(), x1);
        y1 = Math.min(capture.getHeight(), y1);
        if (x1 - x0 > W) System.out.println(name + " is " + (x1 - x0) + " px wide: over CurseForge's " + W);
        ImageIO.write(capture.getSubimage(x0, y0, x1 - x0, y1 - y0), "png", new File(out, name + ".png"));
    }

    static int width(String s) {
        int w = 0;
        for (char ch : s.toCharArray()) w += widths[ch] + 1;
        return w;
    }

    // Minecraft's font: 8-pixel cells, one pixel between letters, a shadow at a quarter of the colour.

    static void loadFont() throws Exception {
        File[] jars = new File("build/moddev/artifacts").listFiles((d, n) -> n.contains("minecraft-resources") && n.endsWith(".jar"));
        if (jars == null || jars.length == 0) throw new IllegalStateException("no game jar under build/moddev/artifacts: build once first");
        try (ZipFile zip = new ZipFile(jars[0])) {
            font = ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/font/ascii.png")));
        }
        for (int c = 0; c < 256; c++) {
            int gx = (c % 16) * 8, gy = (c / 16) * 8, w = 0;
            for (int x = 7; x >= 0 && w == 0; x--)
                for (int y = 0; y < 8; y++) if ((font.getRGB(gx + x, gy + y) >>> 24) > 0) { w = x + 1; break; }
            widths[c] = c == ' ' ? 4 : w;
        }
    }

    static void text(Graphics2D g, String s, int x, int y, int scale, Color c) {
        glyphs(g, s, x + scale, y + scale, scale, new Color(c.getRed() / 4, c.getGreen() / 4, c.getBlue() / 4, c.getAlpha()));
        glyphs(g, s, x, y, scale, c);
    }

    static void glyphs(Graphics2D g, String s, int x, int y, int scale, Color c) {
        g.setColor(c);
        for (char ch : s.toCharArray()) {
            int gx = (ch % 16) * 8, gy = (ch / 16) * 8;
            for (int yy = 0; yy < 8; yy++)
                for (int xx = 0; xx < 8; xx++)
                    if ((font.getRGB(gx + xx, gy + yy) >>> 24) > 0) g.fillRect(x + xx * scale, y + yy * scale, scale, scale);
            x += (widths[ch] + 1) * scale;
        }
    }

    // Images

    static BufferedImage cover(BufferedImage src, int w, int h, double fy) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double s = Math.max(w / (double) src.getWidth(), h / (double) src.getHeight());
        int sw = (int) (src.getWidth() * s), sh = (int) (src.getHeight() * s);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, (w - sw) / 2, (int) (-(sh - h) * fy), sw, sh, null);
        g.dispose();
        return o;
    }

    static BufferedImage blur(BufferedImage src) {
        int r = 5, n = (2 * r + 1) * (2 * r + 1);
        float[] k = new float[n];
        java.util.Arrays.fill(k, 1f / n);
        BufferedImage padded = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        new ConvolveOp(new Kernel(2 * r + 1, 2 * r + 1, k), ConvolveOp.EDGE_NO_OP, null).filter(src, padded);
        return padded;
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
