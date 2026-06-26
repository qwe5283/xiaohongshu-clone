package com.xiaohongshu.post.service.impl;

import com.xiaohongshu.post.dto.TextImageDTO;
import com.xiaohongshu.post.service.TextImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 文本配图服务实现
 * <p>
 * 根据输入文本生成2:3比例的配图，使用随机预设配色方案，
 * 文本水平垂直居中，并在左上角和右下角添加装饰元素。
 */
@Service
public class TextImageServiceImpl implements TextImageService {

    private static final Logger log = LoggerFactory.getLogger(TextImageServiceImpl.class);

    /** 图片宽度 */
    private static final int WIDTH = 400;
    /** 图片高度（2:3比例） */
    private static final int HEIGHT = 600;
    /** 内边距 */
    private static final int PADDING = 40;
    /** 文本区域宽度 */
    private static final int TEXT_AREA_WIDTH = WIDTH - 2 * PADDING;
    /** 文本字体大小 */
    private static final int TEXT_FONT_SIZE = 48;
    /** 装饰元素字体大小（独立于正文） */
    private static final int DECOR_FONT_SIZE = 90;
    /** 前景色（文本颜色） */
    private static final String FOREGROUND_COLOR = "#40473F";

    /**
     * 预设配色方案：{背景色, 装饰色}
     */
    private static final String[][] COLOR_SCHEMES = {
            {"#FDFFDA", "#F8EB9C"},
            {"#CFF0FF", "#A3D4F2"},
            {"#FFE5EE", "#FCC7DB"},
            {"#D8FFD3", "#B2F1AB"},
    };

    private final Random random = new Random();

    @Override
    public byte[] generateImage(TextImageDTO dto) {
        String text = dto.getText();

        // 随机选取配色方案
        String[] scheme = COLOR_SCHEMES[random.nextInt(COLOR_SCHEMES.length)];
        Color bgColor = Color.decode(scheme[0]);
        Color decorColor = Color.decode(scheme[1]);
        Color fgColor = Color.decode(FOREGROUND_COLOR);

        // 创建画布
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        try {
            // 开启抗锯齿
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 填充背景色
            g2d.setColor(bgColor);
            g2d.fillRect(0, 0, WIDTH, HEIGHT);

            // 绘制居中文本
            drawCenteredText(g2d, text, fgColor);

            // 绘制装饰元素
            drawDecorations(g2d, fgColor, decorColor);
        } finally {
            g2d.dispose();
        }

        // 编码为PNG字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "PNG", baos);
        } catch (IOException e) {
            throw new RuntimeException("图片生成失败", e);
        }
        return baos.toByteArray();
    }

    /**
     * 绘制水平垂直居中的文本，支持自动换行
     */
    private void drawCenteredText(Graphics2D g2d, String text, Color color) {
        Font notoSansFont = loadFontByPath("/fonts/NotoSansSC-Regular.ttf", TEXT_FONT_SIZE);
        if (notoSansFont == null) {
            throw new RuntimeException("Fonts.NotoSansSC-Regular.ttf not found");
        }
        Font textFont = notoSansFont.deriveFont(Font.BOLD, (float) TEXT_FONT_SIZE);
        g2d.setFont(textFont);
        g2d.setColor(color);

        FontMetrics fm = g2d.getFontMetrics();
        int lineHeight = fm.getHeight();

        // 按宽度自动换行
        List<String> lines = wrapText(text, textFont, TEXT_AREA_WIDTH, g2d);

        // 文本块总高度
        int totalHeight = lines.size() * lineHeight;
        // 垂直居中起始Y坐标（baseline）
        int startY = (HEIGHT - totalHeight) / 2 + fm.getAscent();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineWidth = fm.stringWidth(line);
            int x = (WIDTH - lineWidth) / 2;
            int y = startY + i * lineHeight;
            g2d.drawString(line, x, y);
        }
    }

    /**
     * 绘制装饰元素：
     * - 左上角：加粗前引号 "
     * - 右下角：加粗下划线
     */
    private void drawDecorations(Graphics2D g2d, Color fgColor, Color decorColor) {
        // ---- 左上角：重加粗前引号 " ----
        Font simHeiFont = loadFontByPath("/fonts/simhei.ttf", DECOR_FONT_SIZE);
        if (simHeiFont == null) {
            throw new RuntimeException("Fonts.simhei.ttf not found");
        }
        Font quoteFont = simHeiFont.deriveFont(Font.BOLD, (float) DECOR_FONT_SIZE);
        g2d.setFont(quoteFont);
        g2d.setColor(decorColor);
        FontMetrics qfm = g2d.getFontMetrics(quoteFont);
        int quoteX = PADDING - 40;
        int quoteY = PADDING + qfm.getAscent();
        g2d.drawString("\u201C", quoteX, quoteY);

        // ---- 右下角：重加粗下划线 ----
        Stroke originalStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(decorColor);
        int lineY = HEIGHT - PADDING - 20;
        int lineX1 = WIDTH - PADDING - 90;
        int lineX2 = WIDTH - PADDING;
        g2d.drawLine(lineX1, lineY, lineX2, lineY);

        // 在下线下方加一条短线，形成类似"___"的视觉效果
        g2d.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(decorColor);
        int line2Y = lineY + 14;
        int line2X1 = WIDTH - PADDING - 60;
        int line2X2 = WIDTH - PADDING - 10;
        g2d.drawLine(line2X1, line2Y, line2X2, line2Y);

        g2d.setStroke(originalStroke);
    }

    /**
     * 按指定宽度对文本进行自动换行
     *
     * @param text     原始文本
     * @param font     绘制字体
     * @param maxWidth 每行最大宽度（像素）
     * @param g2d      Graphics2D上下文
     * @return 换行后的行列表
     */
    private List<String> wrapText(String text, Font font, int maxWidth, Graphics2D g2d) {
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();

        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String testLine = currentLine.toString() + c;
            if (fm.stringWidth(testLine) > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(String.valueOf(c));
            } else {
                currentLine.append(c);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    /**
     * 按指定 classpath 路径加载单个字体文件
     *
     * @param path classpath 路径（如 /fonts/simhei.ttf）
     * @param size 字号
     * @return 加载成功的 Font 对象，失败返回 null
     */
    private Font loadFontByPath(String path, int size) {
        try (InputStream inputStream = TextImageServiceImpl.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                log.warn("字体文件不存在: {}", path);
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, inputStream);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font.deriveFont(Font.PLAIN, (float) size);
        } catch (FontFormatException | IOException e) {
            log.warn("加载字体失败: {}", path, e);
            return null;
        }
    }
}
