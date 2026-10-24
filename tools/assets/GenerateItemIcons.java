import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Creates the three flat item masks while retaining vanilla silhouettes and alpha.
 */
public final class GenerateItemIcons {
    private GenerateItemIcons() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: java GenerateItemIcons.java <Minecraft resource jar> <item texture directory>");
        }
        Path output = Path.of(args[1]);
        Files.createDirectories(output);
        try (var archive = new ZipFile(args[0])) {
            ImageIO.write(pane(read(archive, "block/glass")), "PNG", output.resolve("player_glass_pane.png").toFile());
            ImageIO.write(dye(read(archive, "item/blue_dye")), "PNG", output.resolve("player_dye.png").toFile());
            ImageIO.write(bucket(read(archive, "item/water_bucket")), "PNG",
                    output.resolve("player_liquid_bucket_overlay.png").toFile());
        }
    }

    private static BufferedImage read(ZipFile archive, String texture) throws IOException {
        var entry = archive.getEntry("assets/minecraft/textures/" + texture + ".png");
        if (entry == null) {
            throw new IllegalArgumentException("Missing vanilla texture: " + texture);
        }
        try (var stream = archive.getInputStream(entry)) {
            var image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("Unreadable vanilla texture: " + texture);
            }
            return image;
        }
    }

    private static BufferedImage pane(BufferedImage source) {
        var result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getRGB(x, y);
                int gray = Math.round(
                        (pixel >> 16 & 255) * .2126f + (pixel >> 8 & 255) * .7152f + (pixel & 255) * .0722f);
                result.setRGB(x, y, grayscale(pixel, gray));
            }
        }
        return result;
    }

    private static BufferedImage dye(BufferedImage source) {
        requireItemSize(source);
        var result = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        double min = 255;
        double max = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = source.getRGB(x, y);
                if ((pixel >>> 24) != 0) {
                    min = Math.min(min, luminance(pixel));
                    max = Math.max(max, luminance(pixel));
                }
            }
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = source.getRGB(x, y);
                if ((pixel >>> 24) != 0) {
                    int gray = (int) Math.round(150 + 105 * (luminance(pixel) - min) / Math.max(1, max - min));
                    result.setRGB(x, y, grayscale(pixel, gray));
                }
            }
        }
        return result;
    }

    private static BufferedImage bucket(BufferedImage source) {
        requireItemSize(source);
        var result = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = source.getRGB(x, y);
                int red = pixel >> 16 & 255;
                int green = pixel >> 8 & 255;
                int blue = pixel & 255;
                if ((pixel >>> 24) != 0 && blue - red > 45 && blue - green > 25) {
                    result.setRGB(x, y, grayscale(pixel, Math.min(255, 170 + blue / 3)));
                }
            }
        }
        return result;
    }

    private static void requireItemSize(BufferedImage image) {
        if (image.getWidth() != 16 || image.getHeight() != 16) {
            throw new IllegalArgumentException("Expected a 16x16 item sprite");
        }
    }

    private static int grayscale(int pixel, int gray) {
        return (pixel & 0xFF000000) | gray << 16 | gray << 8 | gray;
    }

    private static double luminance(int pixel) {
        return (pixel >> 16 & 255) * .2126 + (pixel >> 8 & 255) * .7152 + (pixel & 255) * .0722;
    }
}
