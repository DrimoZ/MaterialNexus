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
 * The logo: one block of copper, in the isometric view the game gives blocks in an inventory, on Immaterial
 * Drawers' plate - so the mods' icons read as a set in a launcher (Ore Vein Tweaker's block, Portable Beacons'
 * beacon).
 *
 * <p>The block tells what the mod does. Its lit left face is the pack before: three copper blocks from three mods
 * stacked in bands, each its own copper (riveted and rusty, plain, pale and striped). The right face and the top are
 * the pack after: one copper, the plain one kept, with only a line of light where the bands met. Every face is drawn
 * in texture pixels and lit like a block; the textures are drawn here, not taken from any mod.
 *
 * <p>512 x 512: the mod list, CurseForge's project avatar and the GitHub social preview all scale it down from there.
 *
 * <pre>java tools/GenerateLogo.java</pre>
 */
public final class GenerateLogo {

    private static final int SIZE = 512;
    /** Screen pixels per texture pixel. */
    private static final double K = 13;
    private static final double COS = Math.cos(Math.toRadians(30));
    private static final double SIN = 0.5;
    /** The block's edge, in texture pixels. */
    private static final int B = 16;

    private static final Color PLATE_TOP = new Color(0x2A2244);
    private static final Color PLATE_BOTTOM = new Color(0x100D1C);
    /** The screen's accent blue, a little brighter to glow. */
    private static final Color LIGHT = new Color(0x6FB4FF);

    /** The copper kept: plain, as vanilla draws a block of copper. */
    private static final int[] KEPT = {0xE0794F, 0xD26D46, 0xEC8C60, 0xC8633F};
    private static final int KEPT_LIGHT = 0xF4A57A, KEPT_DARK = 0xA9502F;
    /** The other two: a riveted, rusty one and a pale, striped one. */
    private static final int[] RUSTY = {0xB25737, 0xA44C30, 0xBE6340, 0x9A452B};
    private static final int RIVET = 0xE9B08A;
    private static final int[] PALE = {0xEBA585, 0xE09878, 0xF2B597, 0xD58C6C};

    /** Where the bands of the left face start, in v (0 at the bottom). */
    private static final int LOW = 5, HIGH = 11;

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

        // The block's origin: the centre of its base, placed so the whole cube is centred.
        cx = SIZE / 2.0;
        cy = SIZE / 2.0 + B * K * 0.5;

        glow(g, cx, cy - B * K * 0.55, 250, 90);
        shadow(g);
        box(g);
        seams(g);

        g.setClip(null);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 255, 255, 34));
        g.draw(plate);
        g.dispose();
        ImageIO.write(out, "PNG", new File("src/main/resources/logo.png"));
        System.out.println("Logo written.");
    }

    // ------------------------------------------------------------------ the block's texture

    private static int noise(int u, int v, int salt) {
        int h = u * 73856093 ^ v * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5BD1E995;
        return (h ^ h >>> 15) & 0x7FFFFFFF;
    }

    private static int pick(int[] palette, int u, int v, int salt) {
        return palette[noise(u, v, salt) % palette.length];
    }

    /** A block of the kept copper: speckled, its edges lit on the top-left and shaded on the bottom-right. */
    private static Color kept(int u, int v, int salt) {
        if (u == 0 || v == B - 1) return new Color(KEPT_LIGHT);
        if (u == B - 1 || v == 0) return new Color(KEPT_DARK);
        return new Color(pick(KEPT, u, v, salt));
    }

    /** One band of the left face, as its mod draws its block of copper. */
    private static Color band(int u, int v) {
        if (v >= HIGH) {
            // Riveted plates, rusty: a dark seam under them, a rivet every five pixels.
            if (v == HIGH) return new Color(0x7E3A24);
            if (v == B - 3 && u % 5 == 2) return new Color(RIVET);
            return new Color(pick(RUSTY, u, v, 3));
        }
        if (v >= LOW) {
            // The kept copper, a seam under it and its lit top row.
            if (v == LOW) return new Color(KEPT_DARK);
            if (v == HIGH - 1 || u == 0) return new Color(KEPT_LIGHT);
            return new Color(pick(KEPT, u, v, 1));
        }
        // Pale and striped: a lighter line every other row.
        return new Color(v % 2 == 0 ? pick(PALE, u, v, 5) : PALE[2]);
    }

    /** @param face 0 top, 1 left, 2 right */
    private static Color texel(int face, int u, int v) {
        if (face == 1) return band(u, v);
        return kept(u, v, face == 0 ? 7 : 1);
    }

    // ------------------------------------------------------------------ drawing

    /** The cube by its three visible faces, each in texture pixels, lit like a block. */
    private static void box(Graphics2D g) {
        double h = B / 2.0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // Left face (z = h): u along x, v along y.
        for (int u = 0; u < B; u++) {
            for (int v = 0; v < B; v++) {
                double x = -h + u;
                g.setColor(shade(texel(1, u, v), 0.82));
                g.fill(quad(x, v, h, x + 1, v, h, x + 1, v + 1, h, x, v + 1, h));
            }
        }
        // Right face (x = h): u along -z, v along y.
        for (int u = 0; u < B; u++) {
            for (int v = 0; v < B; v++) {
                double z = h - u - 1;
                g.setColor(shade(texel(2, u, v), 0.6));
                g.fill(quad(h, v, z, h, v, z + 1, h, v + 1, z + 1, h, v + 1, z));
            }
        }
        // Top (y = B).
        for (int u = 0; u < B; u++) {
            for (int w = 0; w < B; w++) {
                double x = -h + u;
                double z = -h + w;
                g.setColor(texel(0, u, B - 1 - w));
                g.fill(quad(x, B, z, x + 1, B, z, x + 1, B, z + 1, x, B, z + 1));
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /**
     * On the right face, where the bands met on the left one: a line of light continuing each seam around the
     * corner and fading out as it goes, so the face reads as the same block made one.
     */
    private static void seams(Graphics2D g) {
        double h = B / 2.0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int v : new int[]{LOW, HIGH}) {
            for (int u = 0; u < B; u++) {
                double z = h - u - 1;
                g.setColor(new Color(LIGHT.getRed(), LIGHT.getGreen(), LIGHT.getBlue(), Math.max(0, 235 - u * 15)));
                g.fill(quad(h, v, z, h, v, z + 1, h, v + 1, z + 1, h, v + 1, z));
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // The light the seams give off where they turn the corner.
        double[] corner = project(h, B / 2.0, h);
        glow(g, corner[0], corner[1], 130, 70);
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

    private static void glow(Graphics2D g, double x, double y, float radius, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), radius, new float[]{0f, 1f},
                new Color[]{new Color(LIGHT.getRed(), LIGHT.getGreen(), LIGHT.getBlue(), alpha),
                        new Color(LIGHT.getRed(), LIGHT.getGreen(), LIGHT.getBlue(), 0)}));
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
