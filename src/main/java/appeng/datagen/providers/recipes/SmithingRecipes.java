package appeng.datagen.providers.recipes;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.core.AppEng;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.ItemDefinition;
import appeng.datagen.providers.tags.ConventionTags;

public class SmithingRecipes extends AE2RecipeProvider {
    public SmithingRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output);
    }

    @Override
    public void buildRecipes(RecipeOutput consumer) {
        fluixSmithing(consumer, ConventionTags.QUARTZ_AXE, AEItems.FLUIX_AXE);
        fluixSmithing(consumer, ConventionTags.QUARTZ_HOE, AEItems.FLUIX_HOE);
        fluixSmithing(consumer, ConventionTags.QUARTZ_PICK, AEItems.FLUIX_PICK);
        fluixSmithing(consumer, ConventionTags.QUARTZ_SHOVEL, AEItems.FLUIX_SHOVEL);
        fluixSmithing(consumer, ConventionTags.QUARTZ_SWORD, AEItems.FLUIX_SWORD);
    }

    private void fluixSmithing(RecipeOutput consumer, TagKey<Item> quartzTool,
            ItemDefinition<?> fluixTool) {
        SmithingTransformRecipeBuilder
                .smithing(Ingredient.of(AEItems.FLUIX_UPGRADE_SMITHING_TEMPLATE), Ingredient.of(quartzTool),
                        Ingredient.of(AEBlocks.FLUIX_BLOCK), RecipeCategory.MISC, fluixTool.asItem())
                .unlocks("has_crystals/fluix", has(ConventionTags.ALL_FLUIX))
                .save(consumer, AppEng.makeId("tools/" + getItemName(fluixTool)));
    }

    @Override
    public String getName() {
        return "AE2 Smithing Recipes";
    }
}
