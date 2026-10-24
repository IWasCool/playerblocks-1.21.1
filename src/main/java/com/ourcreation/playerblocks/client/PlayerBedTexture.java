package com.ourcreation.playerblocks.client;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Tints vanilla blanket UVs while preserving the pillow on its top, sides, and head edge.
 */
public final class PlayerBedTexture {

    private PlayerBedTexture() {
    }

    public static void tint(NativeImage pixels, int rgb) {
        int width = pixels.getWidth(), height = pixels.getHeight();
        int minimum = 255, maximum = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels.getPixelRGBA(x, y);
                if (!blanket(x, y, width, height, pixel)) {
                    continue;
                }
                int gray = pixel & 255;
                minimum = Math.min(minimum, gray);
                maximum = Math.max(maximum, gray);
            }
        }
        int range = Math.max(1, maximum - minimum);
        int red = rgb >>> 16 & 255, green = rgb >>> 8 & 255, blue = rgb & 255;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels.getPixelRGBA(x, y);
                if (!blanket(x, y, width, height, pixel)) {
                    continue;
                }
                // White bed cloth has very subtle shading. Expand that native pattern so it
                // remains visible on colored blankets, with highlights at the exact hex color.
                int gray = 176 + ((pixel & 255) - minimum) * 79 / range;
                pixels.setPixelRGBA(x, y, (pixel & 0xFF000000) | (red * gray / 255) | ((green * gray / 255) << 8) |
                        ((blue * gray / 255) << 16));
            }
        }
    }

    private static boolean blanket(int x, int y, int width, int height, int pixel) {
        int u = x * 64 / width, v = y * 64 / height;
        if ((pixel >>> 24) == 0 || u >= 44 || v >= 44) {
            return false;
        }
        // Head main cube: north/top (6..22), west (0..6), east (22..28), down/head end (6..22, 0..6).
        boolean pillow = (v >= 6 && v < 14 && u < 28) || (v < 6 && u >= 6 && u < 22);
        if (pillow) {
            return false;
        }
        int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
        // Keep vanilla brown wooden frame and legs untinted.
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 12;
    }
}
