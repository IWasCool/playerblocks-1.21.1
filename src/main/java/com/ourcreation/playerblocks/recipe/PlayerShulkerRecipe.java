package com.ourcreation.playerblocks.recipe;

import com.ourcreation.playerblocks.block.PlayerShulkerBox;
import com.ourcreation.playerblocks.registry.ModContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Hex conversion must preserve everything inside the source shulker box.
 */
public final class PlayerShulkerRecipe extends CustomRecipe {

    public PlayerShulkerRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 2) {
            return false;
        }
        boolean dye = false, box = false;
        for (int i = 0; i < input.size(); i++) {
            var stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModContent.DYE.get()) && !dye) {
                dye = true;
            } else if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock &&
                    !(block.getBlock() instanceof PlayerShulkerBox) && !box) {
                box = true;
            } else {
                return false;
            }
        }
        return dye && box;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int i = 0; i < input.size(); i++) {
            var stack = input.getItem(i);
            if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) {
                return stack.transmuteCopy(ModContent.SHULKER.get(), 1);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModContent.SHULKER_RECIPE.get();
    }
}
