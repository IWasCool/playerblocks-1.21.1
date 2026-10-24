package com.ourcreation.playerblocks.compat;

import com.ourcreation.playerblocks.PlayerBlocks;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * JEI display for the dynamic, content-preserving shulker recipe. No JEI dependency at runtime.
 */
@JeiPlugin
public final class PlayerJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID, "recipes");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var boxes = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID, "vanilla_shulker_boxes"));
        var ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(boxes), Ingredient.of(ModContent.DYE.get()));
        var example = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(ModContent.SHULKER.get()),
                ingredients);
        registration.addRecipes(RecipeTypes.CRAFTING, List.of(new RecipeHolder<CraftingRecipe>(
                ResourceLocation.fromNamespaceAndPath(PlayerBlocks.MODID, "player_shulker_box"), example)));
    }
}
