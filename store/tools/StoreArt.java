import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Play 스토어 그래픽 생성기 (JDK만 있으면 실행 가능, 추가 설치 불필요).
 *
 *   java store/tools/StoreArt.java <원본 스크린샷 폴더> <출력 images 폴더>
 *
 * 원본 폴더에 maze.png, choose.png, recipes.png, boss.png 가 있어야 함
 * (debug 빌드의 ScreenshotActivity로 촬영). 출력:
 *   icon.png (512x512), featureGraphic.png (1024x500), phoneScreenshots/1~4.png (1920x1080)
 * 보석 도형은 앱 아이콘(ic_launcher_foreground.xml)과 같은 좌표를 사용.
 */
public class StoreArt {
    static final Color BG = new Color(0x1E2333);
    static final Color GRID = new Color(0x2A3147);
    static final String FONT = "Malgun Gothic";

    // 앱 아이콘과 같은 보석 면 (108 기준 좌표) + 색
    static final float[][][] FACETS = {
        {{38, 38}, {46, 50}, {26, 50}},
        {{38, 38}, {70, 38}, {62, 50}, {46, 50}},
        {{70, 38}, {82, 50}, {62, 50}},
        {{26, 50}, {46, 50}, {54, 80}},
        {{46, 50}, {62, 50}, {54, 80}},
        {{62, 50}, {82, 50}, {54, 80}},
    };
    static final int[] FACET_COLORS = {0xC58BFF, 0xE9D2FF, 0xA866F2, 0x9A4DEB, 0x7B30D2, 0x5A1EA8};
    static final float[][] SPARKLE = {{78, 28}, {79.6f, 32.4f}, {84, 34}, {79.6f, 35.6f}, {78, 40}, {76.4f, 35.6f}, {72, 34}, {76.4f, 32.4f}};

    static final String[][] SHOTS = {
        {"maze", "보석으로 미로를 짓고, 적의 길을 늘려라"},
        {"choose", "5개 중 단 하나만 남긴다 · 합성과 특수 조합"},
        {"recipes", "연쇄 번개, 기절, 처형… 17종 특수 보석"},
        {"boss", "10웨이브마다 찾아오는 보스를 막아라"},
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: java StoreArt.java <rawShotsDir> <outImagesDir>");
            System.exit(1);
        }
        File raw = new File(args[0]);
        File out = new File(args[1]);
        File shotsOut = new File(out, "phoneScreenshots");
        shotsOut.mkdirs();
        if (!new Font(FONT, Font.BOLD, 10).canDisplay('보')) throw new IllegalStateException("한글 폰트 없음: " + FONT);

        ImageIO.write(icon(), "png", new File(out, "icon.png"));
        ImageIO.write(feature(), "png", new File(out, "featureGraphic.png"));
        for (int i = 0; i < SHOTS.length; i++) {
            File src = new File(raw, SHOTS[i][0] + ".png");
            ImageIO.write(screenshot(ImageIO.read(src), SHOTS[i][1]), "png", new File(shotsOut, (i + 1) + "_" + SHOTS[i][0] + ".png"));
        }
        System.out.println("done -> " + out.getAbsolutePath());
    }

    static Graphics2D g2(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    static Path2D poly(float[][] pts, float scale, float ox, float oy) {
        Path2D p = new Path2D.Float();
        for (int i = 0; i < pts.length; i++) {
            float x = ox + (pts[i][0] - 54) * scale, y = oy + (pts[i][1] - 54) * scale;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }

    /** 108 좌표계의 (54,54)를 (cx,cy)에 맞춰 scale 배로 보석을 그림 */
    static void drawGem(Graphics2D g, float scale, float cx, float cy, boolean sparkle) {
        for (int i = 0; i < FACETS.length; i++) {
            g.setColor(new Color(FACET_COLORS[i]));
            g.fill(poly(FACETS[i], scale, cx, cy));
        }
        if (sparkle) {
            g.setColor(Color.WHITE);
            g.fill(poly(SPARKLE, scale, cx, cy));
        }
    }

    /** 앱 아이콘과 같은 모양의 512 스토어 아이콘 (적응형 아이콘의 보이는 영역 72/108 기준) */
    static BufferedImage icon() {
        int s = 512;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g2(img);
        g.setColor(BG);
        g.fillRect(0, 0, s, s);
        float scale = s / 72f;
        g.setColor(GRID);
        g.setStroke(new BasicStroke(0.8f * scale));
        for (int k = 18; k < 108; k += 18) {
            float p = (k - 54) * scale + s / 2f;
            g.drawLine((int) p, 0, (int) p, s);
            g.drawLine(0, (int) p, s, (int) p);
        }
        drawGem(g, scale, s / 2f, s / 2f, true);
        g.dispose();
        return img;
    }

    static BufferedImage feature() {
        int w = 1024, h = 500;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = g2(img);
        g.setPaint(new GradientPaint(0, 0, new Color(0x2B1F4F), w, h, new Color(0x0F1320)));
        g.fillRect(0, 0, w, h);
        // 희미한 격자
        g.setColor(new Color(255, 255, 255, 14));
        for (int x = 0; x < w; x += 42) g.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += 42) g.drawLine(0, y, w, y);
        // 6색 작은 보석들
        int[] colors = {0xF2263A, 0x3373FF, 0x26D95A, 0xFFCC26, 0xB34DF2, 0xEAF4FF};
        float[][] spots = {{905, 80}, {975, 190}, {880, 330}, {985, 420}, {800, 440}, {955, 300}};
        for (int i = 0; i < colors.length; i++) smallGem(g, spots[i][0], spots[i][1], 16, new Color(colors[i]));
        // 큰 보석
        g.setColor(new Color(170, 100, 255, 40));
        g.fillOval(40, 60, 380, 380);
        drawGem(g, 6.2f, 230, 230, true);
        // 제목
        g.setColor(new Color(0xFFD866));
        g.setFont(new Font(FONT, Font.BOLD, 70));
        g.drawString("보석 타워 디펜스", 420, 235);
        g.setColor(new Color(0xE6E9F2));
        g.setFont(new Font(FONT, Font.PLAIN, 32));
        g.drawString("미로를 짓고, 보석을 합성하라", 424, 295);
        g.dispose();
        return img;
    }

    static void smallGem(Graphics2D g, float cx, float cy, float r, Color c) {
        Path2D p = new Path2D.Float();
        p.moveTo(cx, cy - r * 1.25f);
        p.lineTo(cx + r, cy);
        p.lineTo(cx, cy + r * 1.25f);
        p.lineTo(cx - r, cy);
        p.closePath();
        g.setColor(c);
        g.fill(p);
        Path2D top = new Path2D.Float();
        top.moveTo(cx, cy - r * 1.25f);
        top.lineTo(cx + r * 0.55f, cy - r * 0.3f);
        top.lineTo(cx - r * 0.55f, cy - r * 0.3f);
        top.closePath();
        g.setColor(new Color((c.getRed() + 255) / 2, (c.getGreen() + 255) / 2, (c.getBlue() + 255) / 2));
        g.fill(top);
    }

    /** 왼쪽에서 배경색(0x0E1117)만 있는 세로줄 수 = 잘라낼 빈 여백 (카메라 컷아웃 + 보드 가운데 정렬 여백) */
    static int emptyLeft(BufferedImage src) {
        for (int x = 0; x < src.getWidth() / 3; x++) {
            for (int y = 0; y < src.getHeight(); y += 4) {
                int p = src.getRGB(x, y);
                int r = (p >> 16) & 255, gg = (p >> 8) & 255, b = p & 255;
                if (Math.abs(r - 0x0E) + Math.abs(gg - 0x11) + Math.abs(b - 0x17) > 12) return Math.max(0, x - 24);
            }
        }
        return 0;
    }

    /** 1920x1080: 위쪽 캡션 + 게임 화면 (왼쪽 빈 여백은 잘라냄) */
    static BufferedImage screenshot(BufferedImage src, String caption) {
        int w = 1920, h = 1080;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = g2(img);
        g.setPaint(new GradientPaint(0, 0, new Color(0x1B1734), 0, h, new Color(0x0B0E16)));
        g.fillRect(0, 0, w, h);

        int cropX = emptyLeft(src);
        BufferedImage shot = src.getSubimage(cropX, 0, src.getWidth() - cropX, src.getHeight());
        // 캡션 자리(위쪽 170px 이상)를 남기면서 최대한 크게
        int sw = Math.min(1760, Math.round((h - 56 - 170) * shot.getWidth() / (float) shot.getHeight()));
        int sh = Math.round(sw * shot.getHeight() / (float) shot.getWidth());
        int sx = (w - sw) / 2, sy = h - sh - 56;

        g.setColor(Color.WHITE);
        g.setFont(new Font(FONT, Font.BOLD, 62));
        FontMetrics fm = g.getFontMetrics();
        int ty = (sy - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(caption, (w - fm.stringWidth(caption)) / 2, ty);

        Shape clip = new RoundRectangle2D.Float(sx, sy, sw, sh, 36, 36);
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(new RoundRectangle2D.Float(sx + 6, sy + 10, sw, sh, 36, 36));
        g.setClip(clip);
        g.drawImage(shot, sx, sy, sw, sh, null);
        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(2));
        g.draw(clip);
        g.dispose();
        return img;
    }
}
