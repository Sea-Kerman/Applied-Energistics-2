package appeng.integration.modules.emi;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import appeng.api.config.CondenserOutput;
import appeng.api.features.P2PTunnelAttunementInternal;
import appeng.api.integrations.emi.EmiStackConverters;
import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.definitions.ItemDefinition;
import appeng.core.localization.GuiText;
import appeng.core.localization.ItemModText;
import appeng.core.localization.LocalizationEnum;
import appeng.integration.abstraction.ItemListMod;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.me.items.WirelessCraftingTermMenu;
import appeng.recipes.entropy.EntropyRecipe;
import appeng.recipes.handlers.ChargerRecipe;
import appeng.recipes.handlers.InscriberRecipe;
import appeng.recipes.transform.TransformRecipe;

@EmiEntrypoint
public class AppEngEmiPlugin implements EmiPlugin {
    static final ResourceLocation TEXTURE = AppEng.makeId("textures/guis/jei.png");

    @Override
    public void register(EmiRegistry registry) {

        ItemListMod.setAdapter(new EmiItemListModAdapter());

        EmiStackConverters.register(new EmiItemStackConverter());
        EmiStackConverters.register(new EmiFluidStackConverter());

        // Screen handling
        registry.addGenericExclusionArea(new EmiAeBaseScreenExclusionZones());
        registry.addGenericStackProvider(new EmiAeBaseScreenStackProvider());
        registry.addGenericDragDropHandler(new EmiAeBaseScreenDragDropHandler());

        // Additional Workstations
        registerWorkstations(registry);

        // Descriptions
        registerDescriptions(registry);

        // Recipe transfer
        registry.addRecipeHandler(PatternEncodingTermMenu.TYPE,
                new EmiEncodePatternHandler<>(PatternEncodingTermMenu.class));
        registry.addRecipeHandler(CraftingTermMenu.TYPE, new EmiUseCraftingRecipeHandler<>(CraftingTermMenu.class));
        registry.addRecipeHandler(WirelessCraftingTermMenu.TYPE,
                new EmiUseCraftingRecipeHandler<>(WirelessCraftingTermMenu.class));

        // Inscriber
        registry.addCategory(EmiInscriberRecipe.CATEGORY);
        registry.addWorkstation(EmiInscriberRecipe.CATEGORY, EmiStack.of(AEBlocks.INSCRIBER));
        adaptRecipeType(registry, InscriberRecipe.TYPE, EmiInscriberRecipe::new);

        // Charger
        registry.addCategory(EmiChargerRecipe.CATEGORY);
        registry.addWorkstation(EmiChargerRecipe.CATEGORY, EmiStack.of(AEBlocks.CHARGER));
        registry.addWorkstation(EmiChargerRecipe.CATEGORY, EmiStack.of(AEBlocks.CRANK));
        adaptRecipeType(registry, ChargerRecipe.TYPE, EmiChargerRecipe::new);

        // P2P attunement
        registry.addCategory(EmiP2PAttunementRecipe.CATEGORY);
        registry.addDeferredRecipes(this::registerP2PAttunements);

        // Condenser
        registry.addCategory(EmiCondenserRecipe.CATEGORY);
        registry.addWorkstation(EmiCondenserRecipe.CATEGORY, EmiStack.of(AEBlocks.CONDENSER));
        registry.addRecipe(new EmiCondenserRecipe(CondenserOutput.MATTER_BALLS));
        registry.addRecipe(new EmiCondenserRecipe(CondenserOutput.SINGULARITY));

        // Entropy Manipulator
        registry.addCategory(EmiEntropyRecipe.CATEGORY);
        registry.addWorkstation(EmiEntropyRecipe.CATEGORY, EmiStack.of(AEItems.ENTROPY_MANIPULATOR));
        adaptRecipeType(registry, EntropyRecipe.TYPE, EmiEntropyRecipe::new);

        // In-World Transformation
        registry.addCategory(EmiTransformRecipe.CATEGORY);
        adaptRecipeType(registry, TransformRecipe.TYPE, EmiTransformRecipe::new);

        // Facades
        registry.addDeferredRecipes(this::registerFacades);

        // Remove items
        if (!AEConfig.instance().isEnableFacadesInJEI()) {
            registry.removeEmiStacks(stack -> AEItems.FACADE.isSameAs(stack.getItemStack()));
        }
    }

    private void registerWorkstations(EmiRegistry registry) {
        ItemStack craftingTerminal = AEParts.CRAFTING_TERMINAL.stack();
        registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(craftingTerminal));

        ItemStack wirelessCraftingTerminal = AEItems.WIRELESS_CRAFTING_TERMINAL.stack();
        registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(wirelessCraftingTerminal));
    }

    private void registerDescriptions(EmiRegistry registry) {

        addDescription(registry, AEItems.CERTUS_QUARTZ_CRYSTAL, GuiText.CertusQuartzObtain);

        if (AEConfig.instance().isSpawnPressesInMeteoritesEnabled()) {
            addDescription(registry, AEItems.LOGIC_PROCESSOR_PRESS, GuiText.inWorldCraftingPresses);
            addDescription(registry, AEItems.CALCULATION_PROCESSOR_PRESS,
                    GuiText.inWorldCraftingPresses);
            addDescription(registry, AEItems.ENGINEERING_PROCESSOR_PRESS,
                    GuiText.inWorldCraftingPresses);
            addDescription(registry, AEItems.SILICON_PRESS, GuiText.inWorldCraftingPresses);
        }

        addDescription(registry, AEBlocks.CRANK, ItemModText.CRANK_DESCRIPTION);

    }

    private void addDescription(EmiRegistry registry, ItemDefinition<?> item, LocalizationEnum... lines) {

        var info = new EmiInfoRecipe(
                List.of(EmiStack.of(item)),
                Arrays.stream(lines).<Component>map(LocalizationEnum::text).toList(),
                null);
        registry.addRecipe(info);

    }

    private static <C extends Container, T extends Recipe<C>> void adaptRecipeType(EmiRegistry registry,
            RecipeType<T> recipeType,
            Function<RecipeHolder<T>, ? extends EmiRecipe> adapter) {
        registry.getRecipeManager().getAllRecipesFor(recipeType)
                .stream()
                .map(adapter)
                .forEach(registry::addRecipe);
    }

    private void registerP2PAttunements(Consumer<EmiRecipe> recipeConsumer) {

        var all = EmiApi.getIndexStacks();
        for (var entry : P2PTunnelAttunementInternal.getApiTunnels()) {
            var inputs = all.stream().filter(stack -> entry.stackPredicate().test(stack.getItemStack()))
                    .toList();
            if (inputs.isEmpty()) {
                continue;
            }
            recipeConsumer.accept(
                    new EmiP2PAttunementRecipe(
                            EmiIngredient.of(inputs),
                            EmiStack.of(entry.tunnelType()),
                            ItemModText.P2P_API_ATTUNEMENT.text().append("\n").append(entry.description())));
        }

        for (var entry : P2PTunnelAttunementInternal.getTagTunnels().entrySet()) {
            var ingredient = EmiIngredient.of(entry.getKey());
            if (ingredient.isEmpty()) {
                continue;
            }
            recipeConsumer.accept(
                    new EmiP2PAttunementRecipe(
                            ingredient,
                            EmiStack.of(entry.getValue()),
                            ItemModText.P2P_TAG_ATTUNEMENT.text()));
        }
    }

    private void registerFacades(Consumer<EmiRecipe> recipeConsumer) {
        var generator = new EmiFacadeGenerator();
        EmiApi.getIndexStacks().stream()
                .map(generator::getRecipeFor)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(recipeConsumer);
    }
}
