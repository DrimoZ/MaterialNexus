import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * The logo: the Nexus Terminal, in the isometric view the game gives blocks in an inventory, on Immaterial Drawers'
 * plate - so the mods' icons read as a set in a launcher (Ore Vein Tweaker's block, Portable Beacons' beacon).
 *
 * <p>It is a tool, not a block to build with: a terminal whose screen shows the mod's own grid, materials against
 * forms, in the screen's colours (orange to decide, green unified, blue pending, grey single), lit and glowing;
 * vents on its side, and a copper ingot resting on top, the material it is about. Every face is drawn in texture
 * pixels and lit like a block, except the screen, which gives light.
 *
 * <p>512 x 512: the mod list, CurseForge's project avatar and the GitHub social preview all scale it down from there.
 *
 * <pre>java tools/GenerateLogo.java</pre>
 */
public final class GenerateLogo {

    private static final int SIZE = 512;
    /** Screen pixels per texture pixel. */
    private static final double K = 12.5;
    private static final double COS = Math.cos(Math.toRadians(30));
    private static final double SIN = 0.5;
    /** The terminal's edge, in texture pixels. */
    private static final int B = 16;

    private static final Color PLATE_TOP = new Color(0x2A2244);
    private static final Color PLATE_BOTTOM = new Color(0x100D1C);
    /** The screen's accent blue. */
    private static final Color LIGHT = new Color(0x5A9BE6);

    private static final int[] METAL = {0x4C515C, 0x464B55, 0x535865, 0x41454F};
    private static final int METAL_LIGHT = 0x6A707D, METAL_DARK = 0x2C2F36, BOLT = 0x8E95A3;
    private static final int SCREEN = 0x15171E, SCREEN_LINE = 0x1C1F28;
    /** The grid's cells, as the screen draws them: to decide, unified, single, pending. */
    private static final int O = 0xE8B23A, G = 0x4CC46A, N = 0x5A5A66, P = 0x5A9BE6;
    private static final int[][] CELLS = {{G, G, O, N}, {O, G, N, G}, {N, P, G, O}};
    private static final int[] COPPER = {0xE0794F, 0xD26D46, 0xEC8C60, 0xC8633F};
    private static final int COPPER_LIGHT = 0xF4A57A;

    private static double cx;
    private static double cy;

    public static void main(String[] args) throws IOException {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D plate = new RoundRectangle2D.Double(16, 16, SIZE - 32, SIZE - 32, 104, 104);
        g.setPaint(new GradientPaint(0, 16, PLATE_TOP, 0, SIZE - 16, PLATE_BOTTOM));
        g.fill(plate);
        g.setClip(plate);
        grid(g);

        // The terminal's origin: the centre of its base, placed so the terminal and its ingot are centred.
        cx = SIZE / 2.0;
        cy = SIZE / 2.0 + B * K * 0.42;

        glow(g, cx, cy - B * K * 0.55, 250, LIGHT, 80);
        shadow(g);
        double h = B / 2.0;
        box(g, -h, 0, -h, h, B, h, GenerateLogo::terminal, true);
        // The screen's light, in front of it.
        double[] screen = project(0, B / 2.0, h);
        glow(g, screen[0], screen[1], 170, LIGHT, 105);
        // A copper ingot resting on top, across the terminal.
        box(g, -5, B, -3, 5, B + 2, 3, (face, u, v) -> copper(u, v, face), false);
        box(g, -4, B + 2, -2, 4, B + 3, 2, (face, u, v) -> face == 0 ? new Color(COPPER_LIGHT) : copper(u, v, face), false);

        g.setClip(null);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 255, 255, 34));
        g.draw(plate);
        g.dispose();
        ImageIO.write(out, "PNG", new File("src/main/resources/logo.png"));
        System.out.println("Logo written.");
    }

    // ------------------------------------------------------------------ the textures

    private static int noise(int u, int v, int salt) {
        int h = u * 73856093 ^ v * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5BD1E995;
        return (h ^ h >>> 15) & 0x7FFFFFFF;
    }

    private static int pick(int[] palette, int u, int v, int salt) {
        return palette[noise(u, v, salt) % palette.length];
    }

    /** Brushed metal with a lit top-left edge, a shaded bottom-right one and a bolt in each corner. */
    private static Color metal(int u, int v, int salt) {
        if ((u == 1 || u == B - 2) && (v == 1 || v == B - 2)) return new Color(BOLT);
        if (u == 0 || v == 0) return new Color(METAL_LIGHT);
        if (u == B - 1 || v == B - 1) return new Color(METAL_DARK);
        return new Color(pick(METAL, u, v, salt));
    }

    /** @param face 0 top, 1 left (the screen), 2 right (the vents); v from the top of the face */
    private static Color terminal(int face, int u, int v) {
        if (face == 1) return screen(u, v);
        if (face == 2 && u >= 4 && u <= 11 && v >= 4 && v <= 11) return new Color(v % 2 == 0 ? METAL_DARK : METAL_LIGHT);
        return metal(u, v, face);
    }

    /** The screen: a bezel, a title bar, and the grid of materials against forms. */
    private static Color screen(int u, int v) {
        if (u < 2 || u > 13 || v < 2 || v > 13) return metal(u, v, 1);
        // The title bar: the accent, then dimmer.
        if (v == 3 && u >= 3 && u <= 12) return u <= 8 ? LIGHT : new Color(0x34507A);
        int row = (v - 5) / 3, col = (u - 3) / 3;
        boolean inCell = v >= 5 && (v - 5) % 3 < 2 && u >= 3 && (u - 3) % 3 < 2 && row < 3 && col < 4;
        if (inCell) return new Color(CELLS[row][col]);
        return new Color(v % 2 == 0 ? SCREEN : SCREEN_LINE);
    }

    private static boolean isScreen(int face, int u, int v) {
        return face == 1 && u >= 2 && u <= 13 && v >= 2 && v <= 13;
    }

    private static Color copper(int u, int v, int face) {
        if (face == 1 && v == 0) return new Color(COPPER_LIGHT);
        return new Color(pick(COPPER, u, v, 11 + face));
    }

    // ------------------------------------------------------------------ drawing

    private interface Texture {
        /** @param face 0 top, 1 left (+z), 2 right (+x); u, v in texture pixels from the face's top-left corner */
        Color at(int face, int u, int v);
    }

    /** A box by its three visible faces, in texture pixels, lit like a block; the screen gives light, so is not shaded. */
    private static void box(Graphics2D g, double x0, double y0, double z0, double x1, double y1, double z1, Texture texture,
                            boolean withScreen) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (double x = x0; x < x1; x++)
            for (double y = y0; y < y1; y++) {
                int u = (int) (x - x0), v = (int) (y1 - y - 1);
                Color c = texture.at(1, u, v);
                g.setColor(withScreen && isScreen(1, u, v) ? c : shade(c, 0.82));
                g.fill(quad(x, y, z1, x + 1, y, z1, x + 1, y + 1, z1, x, y + 1, z1));
            }
        for (double z = z0; z < z1; z++)
            for (double y = y0; y < y1; y++) {
                g.setColor(shade(texture.at(2, (int) (z1 - z - 1), (int) (y1 - y - 1)), 0.62));
                g.fill(quad(x1, y, z, x1, y, z + 1, x1, y + 1, z + 1, x1, y + 1, z));
            }
        for (double x = x0; x < x1; x++)
            for (double z = z0; z < z1; z++) {
                g.setColor(texture.at(0, (int) (x - x0), (int) (z - z0)));
                g.fill(quad(x, y1, z, x + 1, y1, z, x + 1, y1, z + 1, x, y1, z + 1));
            }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static double[] project(double x, double y, double z) {
        return new double[]{cx + (x - z) * K * COS, cy + (x + z) * K * SIN - y * K};
    }

    private static Polygon quad(double... xyz) {
        Polygon p = new Polygon();
        for (int i = 0; i < xyz.length; i += 3) {
            double[] s = project(xyz[i], xyz[i + 1], xyz[i + 2]);
            p.addPoint((int) Math.round(s[0]), (int) Math.round(s[1]));
        }
        return p;
    }

    private static void shadow(Graphics2D g) {
        double[] c = project(0, 0, 0);
        float r = (float) (13 * K);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(c[0], c[1] + K * 2), r, new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 150), new Color(0, 0, 0, 0)}));
        g.fill(new Ellipse2D.Double(c[0] - r, c[1] + K * 2 - r * 0.5, r * 2, r));
    }

    private static void glow(Graphics2D g, double x, double y, float radius, Color c, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), radius, new float[]{0f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
    }

    private static void grid(Graphics2D g) {
        g.setColor(new Color(255, 255, 255, 12));
        for (int i = 16; i < SIZE; i += 32) {
            g.drawLine(i, 0, i, SIZE);
            g.drawLine(0, i, SIZE, i);
        }
    }

    private static Color shade(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }

    private GenerateLogo() {}
}
