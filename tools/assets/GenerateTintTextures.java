import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Deterministic neutral masks from vanilla patterns; no external image dependencies.
 */
public class GenerateTintTextures {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: java GenerateTintTextures.java <Minecraft resource jar> <output directory>");
        }
        var names = new ArrayList<>(
                List.of("white_concrete", "white_wool", "terracotta", "purpur_block", "purpur_pillar",
                        "purpur_pillar_top", "stone_bricks", "quartz_pillar", "quartz_pillar_top",
                        "chiseled_quartz_block", "quartz_bricks", "white_candle", "white_candle_lit",
                        "white_stained_glass", "white_stained_glass_pane_top", "item/spruce_door"));
        for (String color : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
            names.add(color + "_glazed_terracotta");
        }
        var output = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(output);
        try (var archive = new ZipFile(args[0])) {
            for (String name : names) {
                var entry = archive.getEntry(
                        "assets/minecraft/textures/" + (name.startsWith("item/") ? name : "block/" + name) + ".png");
                if (entry == null) {
                    throw new IllegalArgumentException("Missing vanilla texture " + name);
                }
                BufferedImage source;
                try (var stream = archive.getInputStream(entry)) {
                    source = ImageIO.read(stream);
                }
                var mask = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
                float minimum = 255, maximum = 0, total = 0;
                int count = 0;
                for (int y = 0; y < source.getHeight(); y++) {
                    for (int x = 0; x < source.getWidth(); x++) {
                        int pixel = source.getRGB(x, y);
                        if ((pixel >>> 24) == 0) {
                            continue;
                        }
                        float luminance = ((pixel >> 16) & 255) * 0.2126F + ((pixel >> 8) & 255) * 0.7152F +
                                (pixel & 255) * 0.0722F;
                        minimum = Math.min(minimum, luminance);
                        maximum = Math.max(maximum, luminance);
                        total += luminance;
                        count++;
                    }
                }
                float mean = total / Math.max(1, count);
                float contrast = Math.min(1, 20 / Math.max(1, Math.max(maximum - mean, mean - minimum)));
                for (int y = 0; y < source.getHeight(); y++) {
                    for (int x = 0; x < source.getWidth(); x++) {
                        int pixel = source.getRGB(x, y);
                        float luminance = ((pixel >> 16) & 255) * 0.2126F + ((pixel >> 8) & 255) * 0.7152F +
                                (pixel & 255) * 0.0722F;
                        // Equal mean brightness across old/new blocks; retain natural low-contrast grain.
                        // In particular, do not stretch terracotta's tiny texture variation into stone grain.
                        int gray = name.startsWith("item/") ? Math.round(
                                176 + 79 * (luminance - minimum) / Math.max(1, maximum - minimum)) : Math.round(
                                235 + (luminance - mean) * contrast);
                        gray = Math.max(0, Math.min(255, gray));
                        mask.setRGB(x, y, (pixel & 0xFF000000) | (gray << 16) | (gray << 8) | gray);
                    }
                }
                Path target = name.startsWith("item/") ? output.getParent().getParent()
                        .resolve("item/player_door.png") : output.resolve(name + ".png");
                Files.createDirectories(target.getParent());
                ImageIO.write(mask, "PNG", target.toFile());
            }
            for (String variant : List.of("normal", "normal_left", "normal_right")) {
                chestLayers(archive, output, variant);
            }
            var entry = archive.getEntry("assets/minecraft/textures/entity/shulker/shulker_white.png");
            try (var stream = archive.getInputStream(entry)) {
                var source = ImageIO.read(stream);
                var target = output.getParent().getParent().resolve("entity/player_shulker.png");
                Files.createDirectories(target.getParent());
                ImageIO.write(neutral(source), "PNG", target.toFile());
            }
        }
        System.out.println("Generated " + names.size() + " neutral vanilla-pattern masks.");
    }

    private static BufferedImage neutral(BufferedImage source) {
        double total = 0, minimum = 255, maximum = 0;
        int count = 0;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getRGB(x, y);
                if ((pixel >>> 24) == 0) {
                    continue;
                }
                double l = luminance(pixel);
                total += l;
                count++;
                minimum = Math.min(minimum, l);
                maximum = Math.max(maximum, l);
            }
        }
        double mean = total / Math.max(1, count), scale =
                Math.min(1, 20 / Math.max(1, Math.max(maximum - mean, mean - minimum)));
        var result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int p = source.getRGB(x, y), g = (int) Math.round(235 + (luminance(p) - mean) * scale);
                result.setRGB(x, y, (p & 0xFF000000) | g << 16 | g << 8 | g);
            }
        }
        return result;
    }

    private static double luminance(int p) {
        return (p >> 16 & 255) * .2126 + (p >> 8 & 255) * .7152 + (p & 255) * .0722;
    }

    private static void chestLayers(ZipFile archive, Path output, String variant) throws Exception {
        BufferedImage source;
        try (var stream = archive.getInputStream(
                archive.getEntry("assets/minecraft/textures/entity/chest/" + variant + ".png"))) {
            source = ImageIO.read(stream);
        }
        var body = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var outline = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int width = variant.equals("normal") ? 14 : 15;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int p = source.getRGB(x, y);
                if ((p >>> 24) == 0) {
                    continue;
                }
                int u = x * 64 / source.getWidth(), v = y * 64 / source.getHeight();
                int r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255;
                boolean interior = (u >= 14 && u < 14 + width && v < 14) ||
                        (u >= 14 + width && u < 14 + width * 2 && v >= 19 && v < 33);
                boolean trim = luminance(p) < 65 || Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 12;
                // The latch occupies its own vanilla UV island. Preserve its exact metal pixels.
                boolean latch = u < 6 && v < 5;
                if (interior || trim || latch) {
                    int gray = (int) Math.round(luminance(p));
                    outline.setRGB(x, y, latch ? p : (p & 0xFF000000) | gray << 16 | gray << 8 | gray);
                } else {
                    body.setRGB(x, y, p);
                }
            }
        }
        // Keep the real wood grain rather than compressing it twice into a nearly flat panel.
        double total = 0;
        int count = 0;
        for (int y = 0; y < body.getHeight(); y++) {
            for (int x = 0; x < body.getWidth(); x++) {
                int p = body.getRGB(x, y);
                if ((p >>> 24) != 0) {
                    total += luminance(p);
                    count++;
                }
            }
        }
        double mean = total / Math.max(1, count);
        for (int y = 0; y < body.getHeight(); y++) {
            for (int x = 0; x < body.getWidth(); x++) {
                int p = body.getRGB(x, y);
                if ((p >>> 24) == 0) {
                    continue;
                }
                int gray = Math.max(0, Math.min(255, (int) Math.round(235 + (luminance(p) - mean) * 1.5)));
                body.setRGB(x, y, (p & 0xFF000000) | gray << 16 | gray << 8 | gray);
            }
        }
        var target = output.getParent().getParent().resolve("entity/chest");
        Files.createDirectories(target);
        ImageIO.write(body, "PNG", target.resolve(variant + "_body.png").toFile());
        ImageIO.write(outline, "PNG", target.resolve(variant + "_outline.png").toFile());
    }
}
