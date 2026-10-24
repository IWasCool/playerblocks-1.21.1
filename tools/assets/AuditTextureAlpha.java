import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Check transparency against original Minecraft textures, not guessed opacity thresholds.
 */
public class AuditTextureAlpha {

    public static void main(String[] args) throws Exception {
        var root = Path.of(args[1]);
        var lines = new ArrayList<String>();
        try (var zip = new ZipFile(args[0]);
             var files = Files.list(root.resolve("block/tinted"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".png")).sorted().toList()) {
                var entry = zip.getEntry("assets/minecraft/textures/block/" + file.getFileName());
                if (entry == null) {
                    throw new IllegalStateException("No vanilla source for " + file);
                }
                BufferedImage original;
                try (var stream = zip.getInputStream(entry)) {
                    original = ImageIO.read(stream);
                }
                var image = ImageIO.read(file.toFile());
                int opaque = 0, partial = 0, clear = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int alpha = image.getRGB(x, y) >>> 24;
                        if (alpha != (original.getRGB(x, y) >>> 24)) {
                            throw new IllegalStateException("Changed vanilla alpha: " + file + " " + x + "," + y);
                        }
                        if (alpha == 255) {
                            opaque++;
                        } else if (alpha == 0) {
                            clear++;
                        } else {
                            partial++;
                        }
                    }
                }
                lines.add(file.getFileName() + ": opaque=" + opaque + ", translucent=" + partial + ", clear=" + clear +
                        " (vanilla alpha preserved)");
            }
            for (String variant : List.of("normal", "normal_left", "normal_right")) {
                BufferedImage original;
                try (var stream = zip.getInputStream(
                        zip.getEntry("assets/minecraft/textures/entity/chest/" + variant + ".png"))) {
                    original = ImageIO.read(stream);
                }
                var body = ImageIO.read(root.resolve("entity/chest/" + variant + "_body.png").toFile());
                var trim = ImageIO.read(root.resolve("entity/chest/" + variant + "_outline.png").toFile());
                for (int y = 0; y < body.getHeight(); y++) {
                    for (int x = 0; x < body.getWidth(); x++) {
                        int alpha = trim.getRGB(x, y) >>> 24;
                        if (alpha == 0) {
                            alpha = body.getRGB(x, y) >>> 24;
                        }
                        if (alpha != (original.getRGB(x, y) >>> 24)) {
                            throw new IllegalStateException("Changed chest opacity " + variant);
                        }
                    }
                }
                lines.add("Chest " + variant + ": combined body/trim alpha matches vanilla at every pixel");
            }
            var dye = ImageIO.read(root.resolve("item/player_dye.png").toFile());
            if (dye.getWidth() != 16 || dye.getHeight() != 16) {
                throw new IllegalStateException("Dye must be 16x16");
            }
            var glass = ImageIO.read(root.resolve("item/player_glass_pane.png").toFile());
            BufferedImage original;
            try (var stream = zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/glass.png"))) {
                original = ImageIO.read(stream);
            }
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if ((glass.getRGB(x, y) >>> 24) != (original.getRGB(x, y) >>> 24)) {
                        throw new IllegalStateException("Changed pane sprite transparency");
                    }
                }
            }
            lines.add("Pane sprite: vanilla glass clear center and edge transparency preserved");
        }
        Files.write(Path.of(args[2]), lines);
        System.out.println("PASS: " + lines.size() + " texture/opacity checks; report " + args[2]);
    }
}
