/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.crafting.pattern;

import java.util.Objects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import appeng.api.stacks.AEItemKey;

/**
 * Helper functions to work with patterns, mostly related to (de)serialization.
 */
class StonecuttingPatternEncoding {
    private static final String NBT_INPUT = "in";
    // Only used to attempt to recover the recipe in case it's ID has changed
    private static final String NBT_OUTPUT = "out";
    private static final String NBT_SUBSITUTE = "substitute";
    private static final String NBT_RECIPE_ID = "recipe";

    public static AEItemKey getInput(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "Pattern must have an in tag.");
        return AEItemKey.fromTag(nbt.getCompound(NBT_INPUT));
    }

    public static AEItemKey getOutput(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "Pattern must have an out tag.");
        return AEItemKey.fromTag(nbt.getCompound(NBT_OUTPUT));
    }

    public static boolean canSubstitute(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "Pattern must have a tag.");

        return nbt.getBoolean(NBT_SUBSITUTE);
    }

    public static ResourceLocation getRecipeId(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "Pattern must have a tag.");

        return new ResourceLocation(nbt.getString(NBT_RECIPE_ID));
    }

    public static void encode(CompoundTag tag, RecipeHolder<StonecutterRecipe> recipe, AEItemKey input,
            AEItemKey output,
            boolean allowSubstitution) {
        tag.put(NBT_INPUT, input.toTag());
        tag.put(NBT_OUTPUT, output.toTag());
        tag.putBoolean(NBT_SUBSITUTE, allowSubstitution);
        tag.putString(NBT_RECIPE_ID, recipe.id().toString());
    }
}
