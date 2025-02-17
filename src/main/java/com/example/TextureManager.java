package com.example;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import javax.imageio.ImageIO;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class TextureManager {
    private Map<String, Integer> textures;
    private int screenWidth;
    private int screenHeight;

    public TextureManager(Map<String, Integer> textures, int screenWidth, int screenHeight) {
        this.textures = textures;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public void loadTexture(String textureName, String resourcePath) {
        BufferedImage image;
        try (InputStream resourceStream = getClass().getResourceAsStream(resourcePath)) {
            if (resourceStream == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            image = ImageIO.read(resourceStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load texture: " + resourcePath, e);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        // flip the image vertically by reversing the rows
        ByteBuffer buffer = memAlloc(width * height * 4);

        for (int y = height - 1; y >= 0; y--) { // start from the last row
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // red
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // green
                buffer.put((byte) (pixel & 0xFF));         // blue
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // alpha
            }
        }

        buffer.flip();

        int textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        memFree(buffer);

        textures.put(textureName, textureID);
    }

    public void drawOverlay(String textureName, int w, int h, int xP, int yP) {
        Integer textureID = textures.get(textureName);
        if (textureID == null) {
            throw new RuntimeException("Texture not found: " + textureName);
        }

        glPushAttrib(GL_ENABLE_BIT | GL_TRANSFORM_BIT | GL_VIEWPORT_BIT); // save

        // setup for 2D rendering
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // set up orthographic projection matching the screen size
        glViewport(0, 0, screenWidth, screenHeight);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glBindTexture(GL_TEXTURE_2D, textureID);

        // draw the textured quad
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(xP, yP + h);
        glTexCoord2f(1, 0); glVertex2f(xP + w, yP + h);
        glTexCoord2f(1, 1); glVertex2f(xP + w, yP);
        glTexCoord2f(0, 1); glVertex2f(xP, yP);
        glEnd();

        glPopAttrib(); // restore
    }
}
