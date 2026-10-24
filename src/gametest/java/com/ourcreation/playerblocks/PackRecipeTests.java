package com.ourcreation.playerblocks;

import com.ourcreation.playerblocks.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks the final recipe registry after other mods have processed the data packs.
 */
@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class PackRecipeTests {

    @GameTest(template = "empty")
    public static void allPlayerRecipesSurvivePackLoading(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var resources = server.getResourceManager().listResources("recipe",
                id -> id.getNamespace().equals(PlayerBlocks.MODID) && id.getPath().endsWith(".json"));
        int checked = 0;
        for (var resource : resources.keySet()) {
            String path = resource.getPath();
            if (path.startsWith("recipe/create/") && !ModList.get().isLoaded("create")) {
                continue;
            }
            var id = ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID,
                    path.substring("recipe/".length(), path.length() - ".json".length()));
            helper.assertTrue(server.getRecipeManager().byKey(id).isPresent(),
                    "Player recipe loads alongside pack recipes: " + id);
            checked++;
        }
        helper.assertTrue(checked >= ModContent.allItems().length,
                "The recipe audit covers the complete player block collection");
        System.out.println("PLAYER_BLOCKS_PACK_RECIPES: PASS (" + checked + " recipes)");
        helper.succeed();
    }
}
