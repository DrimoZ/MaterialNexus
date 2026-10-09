import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * The logo: one emblem. Three ribbons - copper, gold, tin - interlock into a hexagonal ring, each lying over the
 * next, around a green gem in the screen's "unified" colour: the materials are what binds the nexus together. Flat
 * shapes on the dark plate the other mods share, so it still reads at the 32 px of a launcher list.
 *
 * <p>512 x 512: the mod list, CurseForge's project avatar and the GitHub social preview all scale it down from there.
 *
 * <pre>java tools/GenerateLogo.java</pre>
 */
public final class GenerateLogo {

    private static final int SIZE = 512;
    private static final double C = SIZE / 2.0;
    private static final Color PLATE_TOP = new Color(0x2A2244);
    private static final Color PLATE_BOTTOM = new Color(0x100D1C);
    private static final Color UNIFIED = new Color(0x3FB862);
    private static final Color UNIFIED_LIGHT = new Color(0x9CF5B4);
    /** Copper, gold, tin: {light, base}. */
    private static final Color[][] RIBBONS = {
            {new Color(0xF4A57A), new Color(0xC95E38)},
            {new Color(0xFBD878), new Color(0xD99A22)},
            {new Color(0xDCE6F0), new Color(0x9AABBD)}};

    /** The ring's outer and inner radii, and how far a seam slants along its side. */
    private static final double OUT = 196, IN = 142, SLANT = 0.12;
    private static final double GEM = 110;

    public static void main(String[] args) throws IOException {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        RoundRectangle2D plate = new RoundRectangle2D.Double(16, 16, SIZE - 32, SIZE - 32, 104, 104);
        g.setPaint(new GradientPaint(0, 16, PLATE_TOP, 0, SIZE - 16, PLATE_BOTTOM));
        g.fill(plate);
        g.setClip(plate);
        glow(g, 250, UNIFIED, 60);

        // The ribbons, each from the middle of one side over two corners to the middle of the side two further on.
        // The first is drawn again at the end, clipped to its start, so every ribbon lies over the one before it.
        Shape[] ribbons = new Shape[3];
        for (int i = 0; i < 3; i++) ribbons[i] = ribbon(2 * i);
        for (int i = 0; i < 3; i++) paint(g, ribbons[i], i, ribbons[(i + 2) % 3]);
        Shape clip = g.getClip();
        double[] seam = point((OUT + IN) / 2, 0, 0.5);
        g.clip(new Ellipse2D.Double(seam[0] - 70, seam[1] - 70, 140, 140));
        paint(g, ribbons[0], 0, ribbons[2]);
        g.setClip(clip);

        // The gem, table cut: six bevels lit from the top left around a flat table.
        g.setPaint(new GradientPaint(0, (float) (C - GEM), UNIFIED_LIGHT, 0, (float) (C + GEM), UNIFIED));
        g.fill(hexagon(GEM));
        int[] light = {70, 40, -30, -60, -40, 50};
        for (int k = 0; k < 6; k++) {
            g.setColor(light[k] > 0 ? new Color(255, 255, 255, light[k]) : new Color(0, 0, 0, -light[k]));
            g.fill(bevel(k));
        }
        g.setPaint(new GradientPaint(0, (float) (C - GEM * 0.55), new Color(0xD8FFE2), 0, (float) (C + GEM * 0.55), UNIFIED_LIGHT));
        g.fill(hexagon(GEM * 0.55));

        g.setClip(null);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 255, 255, 34));
        g.draw(plate);
        g.dispose();
        ImageIO.write(out, "PNG", new File("src/main/resources/logo.png"));
        System.out.println("Logo written.");
    }

    /** A ribbon, lit on its outer edge, with a seam shadow along its start where it lies over the one before. */
    private static void paint(Graphics2D g, Shape ribbon, int i, Shape under) {
        var b = ribbon.getBounds2D();
        double dx = b.getCenterX() - C, dy = b.getCenterY() - C;
        g.setPaint(new GradientPaint((float) (C + dx * 1.6), (float) (C + dy * 1.6), RIBBONS[i][0],
                (float) C, (float) C, RIBBONS[i][1]));
        g.fill(ribbon);
        double[] o = point(OUT, 2 * i, 0.5 + SLANT), n = point(IN, 2 * i, 0.5 - SLANT);
        Shape before = g.getClip();
        g.clip(under);
        g.setStroke(new BasicStroke(18f));
        g.setColor(new Color(0, 0, 0, 85));
        g.draw(new Line2D.Double(o[0], o[1], n[0], n[1]));
        g.setClip(before);
    }

    /** The ribbon from side k (corners k to k+1) to side k+2, both ends cut on a slant. */
    private static Shape ribbon(int k) {
        Path2D p = new Path2D.Double();
        double[] a = point(OUT, k, 0.5 + SLANT);
        p.moveTo(a[0], a[1]);
        for (int j = 1; j <= 2; j++) p.lineTo(vertex(OUT, k + j)[0], vertex(OUT, k + j)[1]);
        double[] b = point(OUT, k + 2, 0.5 + SLANT);
        p.lineTo(b[0], b[1]);
        double[] c = point(IN, k + 2, 0.5 - SLANT);
        p.lineTo(c[0], c[1]);
        for (int j = 2; j >= 1; j--) p.lineTo(vertex(IN, k + j)[0], vertex(IN, k + j)[1]);
        double[] d = point(IN, k, 0.5 - SLANT);
        p.lineTo(d[0], d[1]);
        p.closePath();
        return p;
    }

    /** The gem's bevel along side k, between its outline and its table. */
    private static Path2D bevel(int k) {
        double t = GEM * 0.55;
        Path2D p = new Path2D.Double();
        p.moveTo(vertex(GEM, k)[0], vertex(GEM, k)[1]);
        p.lineTo(vertex(GEM, k + 1)[0], vertex(GEM, k + 1)[1]);
        p.lineTo(vertex(t, k + 1)[0], vertex(t, k + 1)[1]);
        p.lineTo(vertex(t, k)[0], vertex(t, k)[1]);
        p.closePath();
        return p;
    }

    /** Corner k of a pointy-top hexagon of radius r. */
    private static double[] vertex(double r, int k) {
        double a = Math.toRadians(60 * k - 90);
        return new double[]{C + r * Math.cos(a), C + r * Math.sin(a)};
    }

    /** The point at fraction t along side k, from corner k to corner k+1. */
    private static double[] point(double r, int k, double t) {
        double[] a = vertex(r, k), b = vertex(r, k + 1);
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    private static Path2D hexagon(double r) {
        Path2D p = new Path2D.Double();
        for (int k = 0; k < 6; k++) {
            double[] v = vertex(r, k);
            if (k == 0) p.moveTo(v[0], v[1]); else p.lineTo(v[0], v[1]);
        }
        p.closePath();
        return p;
    }

    private static void glow(Graphics2D g, float r, Color c, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(C, C), r, new float[]{0f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(C - r, C - r, 2 * r, 2 * r));
    }
}
