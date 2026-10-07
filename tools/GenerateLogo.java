import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * The logo, in the family's art direction - Laser Assembly's, Portable Beacons' and Immaterial Drawers': objects
 * in the isometric view the game gives blocks in an inventory, drawn in texture pixels and lit like a block (top
 * brightest, left mid, right darkest), with a soft glow, on the same night-blue plate.
 *
 * <p>The scene is the mod at work: three copper ingots from three mods, each its own shade, send their light to
 * one stack of the copper kept, which glows where they meet. The ingots' surface is the mod's own plate template,
 * tinted as created items are, so the logo follows the textures.
 *
 * <p>512 x 512: the mod list, CurseForge's project avatar and the GitHub social preview all scale it down.
 *
 * <pre>java tools/GenerateLogo.java</pre>
 */
public final class GenerateLogo {

    private static final String TEMPLATE = "src/main/resources/assets/materialnexus/textures/item/template/plate.png";
    private static final int SIZE = 512;
    /** Screen pixels per texture pixel. */
    private static final double K = 8.2;
    private static final double COS = Math.cos(Math.toRadians(30));
    private static final double SIN = 0.5;

    private static final Color PLATE_TOP = new Color(0x2A2244);
    private static final Color PLATE_BOTTOM = new Color(0x100D1C);
    /** The screen's accent blue: the light of unification. */
    private static final Color ACCENT = new Color(0x5A9BE6);
    private static final int KEPT = 0xD9803F;
    private static final int[] OTHERS = {0xE8A867, 0xB45A36, 0xC7905A};

    private static double cx;
    private static double cy;
    private static BufferedImage surface;

    public static void main(String[] args) throws IOException {
        surface = ImageIO.read(new File(TEMPLATE));
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D plate = new RoundRectangle2D.Double(16, 16, SIZE - 32, SIZE - 32, 104, 104);
        g.setPaint(new GradientPaint(0, 16, PLATE_TOP, 0, SIZE - 16, PLATE_BOTTOM));
        g.fill(plate);
        g.setClip(plate);
        grid(g);

        // The scene's origin: the centre of the stack's base, placed so the whole scene is centred.
        cx = SIZE / 2.0;
        cy = SIZE / 2.0 + 58;

        double[] focus = {0, 9, 0};
        glow(g, project(0, 6, 0), 250, ACCENT, 70);
        shadow(g, 0, 0, 15);

        // Three alternatives about two blocks away, back to front.
        double[][] spots = {{-19, -19}, {-20, 1}, {1, -20}};
        double[][] tops = new double[3][];
        for (int i = 0; i < 3; i++) {
            shadow(g, spots[i][0], spots[i][1], 9);
            ingot(g, spots[i][0], 0, spots[i][1], OTHERS[i], false);
            tops[i] = new double[]{spots[i][0], 4.5, spots[i][1]};
            glow(g, project(tops[i][0], tops[i][1], tops[i][2]), 30, new Color(OTHERS[i]), 150);
        }

        // The kept copper: two ingots side by side, one across them.
        ingot(g, 0, 0, -3.5, KEPT, false);
        ingot(g, 0, 0, 3.5, KEPT, false);
        ingot(g, 0, 4, 0, KEPT, true);
        for (int i = 0; i < 3; i++) beam(g, tops[i], focus, new Color(OTHERS[i]));

        double[] at = project(focus[0], focus[1], focus[2]);
        glow(g, at, 120, ACCENT, 120);
        glow(g, at, 28, Color.WHITE, 210);
        sparkle(g, at, 3);

        g.setClip(null);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 255, 255, 34));
        g.draw(plate);
        g.dispose();
        ImageIO.write(out, "PNG", new File("src/main/resources/logo.png"));
        System.out.println("Logo written.");
    }

    // ------------------------------------------------------------------ the objects

    /**
     * An ingot as two boxes, the top one narrower, its surface the plate template tinted with {@code rgb}; along
     * x, or along z when {@code across} (the top of a pile).
     */
    private static void ingot(Graphics2D g, double x, double y, double z, int rgb, boolean across) {
        Texture tinted = (face, u, v) -> tint(rgb, surface.getRGB(2 + Math.floorMod(u, 12), 2 + Math.floorMod(v, 12)));
        Texture top = (face, u, v) -> face == 0 ? brighter(tinted.at(face, u, v)) : tinted.at(face, u, v);
        double l = across ? 3 : 6, w = across ? 6 : 3;
        box(g, x - l, y, z - w, x + l, y + 3, z + w, tinted);
        box(g, x - l + 1, y + 3, z - w + 1, x + l - 1, y + 4, z + w - 1, top);
    }

    private static Color tint(int rgb, int argb) {
        double f = 0.62 + 0.5 * ((argb & 0xFF) / 255.0);
        return new Color(clamp(((rgb >> 16) & 0xFF) * f), clamp(((rgb >> 8) & 0xFF) * f), clamp((rgb & 0xFF) * f));
    }

    private static Color brighter(Color c) {
        return new Color(clamp(c.getRed() * 1.12), clamp(c.getGreen() * 1.12), clamp(c.getBlue() * 1.12));
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    /** A four-point star in texture pixels, as the game draws its sparkles. */
    private static void sparkle(Graphics2D g, double[] at, int arm) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int px = (int) Math.round(K * 0.8);
        for (int i = -arm; i <= arm; i++) {
            g.setColor(Math.abs(i) <= 1 ? Color.WHITE : new Color(200, 225, 255, 230 - 30 * Math.abs(i)));
            g.fillRect((int) (at[0] + i * px - px / 2.0), (int) (at[1] - px / 2.0), px, px);
            g.fillRect((int) (at[0] - px / 2.0), (int) (at[1] + i * px - px / 2.0), px, px);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    // ------------------------------------------------------------------ drawing

    private interface Texture {
        /** @param face 0 top, 1 left (+z), 2 right (+x); u, v in texture pixels from the face's corner */
        Color at(int face, int u, int v);
    }

    /** A box drawn by its three visible faces, each in texture pixels, lit like a block. */
    private static void box(Graphics2D g, double x0, double y0, double z0, double x1, double y1, double z1, Texture texture) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (double x = x0; x < x1; x++)
            for (double y = y0; y < y1; y++) {
                g.setColor(shade(texture.at(1, (int) (x - x0), (int) (y1 - y - 1)), 0.82));
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

    /** A beam as the game draws one: a straight band in its colour round a white core, over a soft glow. */
    private static void beam(Graphics2D g, double[] from, double[] to, Color c) {
        double[] a = project(from[0], from[1], from[2]), b = project(to[0], to[1], to[2]);
        double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
        for (double t = 0; t <= 1; t += 18 / length) {
            glow(g, new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t}, 24, c, 30);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        Line2D line = new Line2D.Double(a[0], a[1], b[0], b[1]);
        g.setStroke(new BasicStroke((float) (K * 0.9), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(c);
        g.draw(line);
        g.setStroke(new BasicStroke((float) (K * 0.35), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(Color.WHITE);
        g.draw(line);
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

    private static void shadow(Graphics2D g, double x, double z, double size) {
        double[] c = project(x, 0, z);
        float r = (float) (size * K);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(c[0], c[1]), r, new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 150), new Color(0, 0, 0, 0)}));
        g.fill(new Ellipse2D.Double(c[0] - r, c[1] - r * 0.5, r * 2, r));
    }

    private static void glow(Graphics2D g, double[] at, float radius, Color c, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(at[0], at[1]), radius, new float[]{0f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(at[0] - radius, at[1] - radius, radius * 2, radius * 2));
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
