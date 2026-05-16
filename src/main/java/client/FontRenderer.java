package client;

import org.lwjgl.BufferUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

public class FontRenderer {

    private static final int FIRST_CHAR = 32;
    private static final int LAST_CHAR  = 126;
    private static final int CHAR_COUNT = LAST_CHAR - FIRST_CHAR + 1;

    private int textureId;
    private int[] charWidth = new int[128];
    private int charHeight;

    private int atlasWidth;
    private int atlasHeight;

    public FontRenderer(Font font) {
        BufferedImage atlas = buildAtlas(font);
        this.textureId = upload(atlas);
    }

    private BufferedImage buildAtlas(Font font) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g = tmp.createGraphics();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        charHeight = fm.getHeight();

        int cols = 16;
        int rows = (int) Math.ceil(CHAR_COUNT / (float) cols);

        atlasWidth = cols * 16;
        atlasHeight = rows * charHeight;

        BufferedImage img = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_4BYTE_ABGR);

        g = img.createGraphics();
        g.setFont(font);
        g.setColor(Color.WHITE);

        int x = 0;
        int y = fm.getAscent();
        int index = 0;

        for (int c = FIRST_CHAR; c <= LAST_CHAR; c++) {
            char ch = (char) c;

            charWidth[c] = fm.charWidth(ch);

            g.drawString(String.valueOf(ch), x, y);

            x += 16;
            index++;

            if (index % cols == 0) {
                x = 0;
                y += charHeight;
            }
        }

        g.dispose();
        return img;
    }

    private int upload(BufferedImage img) {
        byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        ByteBuffer buffer = BufferUtils.createByteBuffer(data.length);
        buffer.put(data).flip();

        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, img.getWidth(), img.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        return tex;
    }

    public void drawString(String text, int x, int y) {
        glBindTexture(GL_TEXTURE_2D, textureId);

        int cx = x;
        int cy = y;

        glBegin(GL_QUADS);
        for (char c : text.toCharArray()) {
            if (c < FIRST_CHAR || c > LAST_CHAR) continue;

            int index = c - FIRST_CHAR;
            int tx = (index % 16) * 16;
            int ty = (index / 16) * charHeight;

            float u0 = tx / (float) atlasWidth;
            float v0 = ty / (float) atlasHeight;
            float u1 = (tx + charWidth[c]) / (float) atlasWidth;
            float v1 = (ty + charHeight) / (float) atlasHeight;

            int w = charWidth[c];

            glTexCoord2f(u0, v0); glVertex2f(cx, cy);
            glTexCoord2f(u1, v0); glVertex2f(cx + w, cy);
            glTexCoord2f(u1, v1); glVertex2f(cx + w, cy + charHeight);
            glTexCoord2f(u0, v1); glVertex2f(cx, cy + charHeight);

            cx += w;
        }
        glEnd();
    }

    public void drawString(String text, int x, int y, boolean shadow) {
        int shadowOffsetX = 2;
        int shadowOffsetY = 2;

        if(shadow) {
            glColor4f(0f, 0f, 0f, 1f);
            drawString(text, x + shadowOffsetX, y + shadowOffsetY);
        }

        glColor4f(1f, 1f, 1f, 1f);
        drawString(text, x, y);
    }

    public void drawString(String text, int x, int y, Color color, boolean shadow) {
        int shadowOffsetX = 2;
        int shadowOffsetY = 2;

        if (shadow) {
            glColor4f(0f, 0f, 0f, 1f);
            drawString(text, x + shadowOffsetX, y + shadowOffsetY);
        }

        drawString(text, x, y, color);
    }

    public void drawString(String text, int x, int y, Color color) {
        if (color == null) color = Color.WHITE;
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;

        glColor4f(r, g, b, a);
        drawString(text, x, y);
    }

    public int getStringWidth(String text) {
        int width = 0;
        for (char c : text.toCharArray()) {
            if (c < FIRST_CHAR || c > LAST_CHAR) continue;
            width += charWidth[c];
        }
        return width;
    }
}
