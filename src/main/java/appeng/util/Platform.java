
/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.util;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.authlib.GameProfile;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.config.SortOrder;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.DimensionalBlockPos;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.hooks.VisualStateSaving;
import appeng.hooks.ticking.TickHandler;
import appeng.util.helpers.P2PHelper;
import appeng.util.prioritylist.IPartitionList;

public class Platform {

    @VisibleForTesting
    public static ThreadGroup serverThreadGroup = SidedThreadGroups.SERVER;

    private static final P2PHelper P2P_HELPER = new P2PHelper();

    public static final Direction[] DIRECTIONS_WITH_NULL = new Direction[] { Direction.DOWN, Direction.UP,
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null };

    /**
     * Class of the Create Ponder Level. Enables {@link VisualStateSaving} if a block entity is attached to a Ponder
     * level.
     */
    @Nullable
    private static final Class<?> ponderLevelClass = findPonderLevelClass(
            "com.simibubi.create.foundation.ponder.PonderWorld");

    // This hack is used to allow tests and the guidebook to provide a recipe manager before the client loads a world
    public static RecipeManager fallbackClientRecipeManager;
    public static RegistryAccess fallbackClientRegistryAccess;

    public static RegistryAccess getClientRegistryAccess() {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.registryAccess();
        }
        return Objects.requireNonNull(Platform.fallbackClientRegistryAccess);
    }

    public static RecipeManager getClientRecipeManager() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return minecraft.level.getRecipeManager();
        }

        return fallbackClientRecipeManager;
    }

    private static Class<?> findPonderLevelClass(String className) {
        if (!hasClientClasses()) {
            return null; // Don't attempt this on a dedicated server
        }

        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            AELog.warn("Unable to find class %s. Integration with PonderJS disabled.", className);
            return null;
        }
    }

    public static P2PHelper p2p() {
        return P2P_HELPER;
    }

    /**
     * This displays the value for encoded longs ( double *100 )
     *
     * @param n      to be formatted long value
     * @param isRate if true it adds a /t to the formatted string
     * @return formatted long value
     */
    public static String formatPowerLong(long n, boolean isRate) {
        return formatPower((double) n / 100, isRate);
    }

    public static String formatPower(double p, boolean isRate) {
        var displayUnits = AEConfig.instance().getSelectedPowerUnit();
        p = PowerUnits.AE.convertTo(displayUnits, p);

        final String[] preFixes = { "k", "M", "G", "T", "P", "T", "P", "E", "Z", "Y" };
        var unitName = displayUnits.getSymbolName();

        String level = "";
        int offset = 0;
        while (p > 1000 && offset < preFixes.length) {
            p /= 1000;
            level = preFixes[offset];
            offset++;
        }

        final DecimalFormat df = new DecimalFormat("#.##");
        return df.format(p) + ' ' + level + unitName + (isRate ? "/t" : "");
    }

    public static String formatTimeMeasurement(long nanos) {
        if (nanos <= 0) {
            return "0 ns";
        } else if (nanos < 1000) {
            return "<1 µs";
        } else if (nanos <= 1000 * 1000) {
            final long ms = TimeUnit.MICROSECONDS.convert(nanos, TimeUnit.NANOSECONDS);
            return ms + "µs";
        }

        final long ms = TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS);
        return ms + "ms";
    }

    public static Direction crossProduct(Direction forward, Direction up) {
        final int west_x = forward.getStepY() * up.getStepZ() - forward.getStepZ() * up.getStepY();
        final int west_y = forward.getStepZ() * up.getStepX() - forward.getStepX() * up.getStepZ();
        final int west_z = forward.getStepX() * up.getStepY() - forward.getStepY() * up.getStepX();

        return switch (west_x + west_y * 2 + west_z * 3) {
            case 1 -> Direction.EAST;
            case -1 -> Direction.WEST;
            case 2 -> Direction.UP;
            case -2 -> Direction.DOWN;
            case 3 -> Direction.SOUTH;
            case -3 -> Direction.NORTH;
            default ->

                // something is better then nothing?
                Direction.NORTH;
        };

    }

    /**
     * @return True if client-side classes (such as Renderers) are available.
     */
    public static boolean hasClientClasses() {
        // The null check is for tests
        return FMLEnvironment.dist == null || FMLEnvironment.dist.isClient();
    }

    /*
     * returns true if the code is on the client.
     */
    public static boolean isClient() {
        return Thread.currentThread().getThreadGroup() != SidedThreadGroups.SERVER;
    }

    public static boolean hasPermissions(DimensionalBlockPos dc, Player player) {
        if (!dc.isInWorld(player.level())) {
            return false;
        }
        return player.level().mayInteract(player, dc.getPos());
    }

    /*
     * Generates Item entities in the level similar to how items are generally dropped.
     */
    public static void spawnDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        if (!level.isClientSide()) {
            for (var i : drops) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), i);
            }
        }
    }

    /*
     * returns true if the code is on the server.
     */
    public static boolean isServer() {
        return Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER;
    }

    /**
     * Throws an exception if the current thread is not one of the server threads.
     */
    public static void assertServerThread() {
        if (Thread.currentThread().getThreadGroup() != serverThreadGroup) {
            throw new UnsupportedOperationException(
                    "This code can only be called server-side and this is most likely a bug.");
        }
    }

    public static String formatModName(String modId) {
        return "" + ChatFormatting.BLUE + ChatFormatting.ITALIC + getModName(modId);
    }

    @Nullable
    public static String getModName(String modId) {
        return ModList.get().getModContainerById(modId).map(mc -> mc.getModInfo().getDisplayName())
                .orElse(modId);
    }

    public static String getDescriptionId(Fluid fluid) {
        return fluid.defaultFluidState().createLegacyBlock().getBlock().getDescriptionId();
    }

    public static String getDescriptionId(FluidStack fluid) {
        return fluid.getDisplayName().getString();
    }

    public static Component getFluidDisplayName(Fluid fluid, @Nullable CompoundTag tag) {
        var fluidStack = new FluidStack(fluid, 1);
        fluidStack.setTag(tag);
        return fluidStack.getDisplayName();
    }

    // tag copy is not necessary, as the tag is not modified.
    // and this itemStack is not reachable
    public static Component getItemDisplayName(Item item, @Nullable CompoundTag tag) {
        var itemStack = new ItemStack(item);
        itemStack.setTag(tag);
        return itemStack.getHoverName();
    }

    public static boolean isChargeable(ItemStack i) {
        if (i.isEmpty()) {
            return false;
        }
        if (i.getItem() instanceof IAEItemPowerStorage powerStorage) {
            return powerStorage.getAEMaxPower(i) > 0 &&
                    powerStorage.getPowerFlow(i) != AccessRestriction.READ;
        }
        return false;
    }

    public static Player getFakePlayer(ServerLevel level, @Nullable UUID playerUuid) {
        Objects.requireNonNull(level);

        if (playerUuid == null) {
            playerUuid = FakePlayer.DEFAULT_UUID;
        }

        return FakePlayer.get(level, new GameProfile(playerUuid, "[AE2]"));
    }

    public static Direction rotateAround(Direction forward, Direction axis) {
        if (forward.getAxis() == axis.getAxis()) {
            return forward;
        }
        var newForward = forward.getNormal().cross(axis.getNormal());
        return Objects.requireNonNull(Direction.fromDelta(newForward.getX(), newForward.getY(), newForward.getZ()));
    }

    public static void configurePlayer(Player player, Direction side, BlockEntity blockEntity) {
        float pitch = 0.0f;
        float yaw = 0.0f;
        switch (side) {
            case DOWN, UP -> pitch = 90.0f;
            case EAST -> yaw = -90.0f;
            case NORTH -> yaw = 180.0f;
            case SOUTH -> yaw = 0.0f;
            case WEST -> yaw = 90.0f;
            default -> {
            }
        }

        player.moveTo(blockEntity.getBlockPos().getX() + 0.5, blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5,
                yaw, pitch);
    }

    public static ItemStack extractItemsByRecipe(IEnergySource energySrc,
            IActionSource mySrc,
            MEStorage src,
            Level level,
            Recipe<CraftingContainer> r,
            ItemStack output,
            CraftingContainer ci,
            ItemStack providedTemplate,
            int slot,
            KeyCounter items,
            Actionable realForFake,
            IPartitionList filter) {
        if (energySrc.extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.9) {
            if (providedTemplate == null) {
                return ItemStack.EMPTY;
            }

            var ae_req = AEItemKey.of(providedTemplate);

            if (filter == null || filter.isListed(ae_req)) {
                var extracted = src.extract(ae_req, 1, realForFake, mySrc);
                if (extracted > 0) {
                    energySrc.extractAEPower(1, realForFake, PowerMultiplier.CONFIG);
                    return ae_req.toStack();
                }
            }

            var checkFuzzy = providedTemplate.hasTag() || providedTemplate.isDamageableItem();

            if (items != null && checkFuzzy) {
                for (var x : items) {
                    if (x.getKey() instanceof AEItemKey itemKey) {
                        if (providedTemplate.getItem() == itemKey.getItem() && !itemKey.matches(output)) {
                            ci.setItem(slot, itemKey.toStack());
                            if (r.matches(ci, level)
                                    && ItemStack.matches(r.assemble(ci, level.registryAccess()), output)) {
                                if (filter == null || filter.isListed(itemKey)) {
                                    var ex = src.extract(itemKey, 1, realForFake, mySrc);
                                    if (ex > 0) {
                                        energySrc.extractAEPower(1, realForFake, PowerMultiplier.CONFIG);
                                        return itemKey.toStack();
                                    }
                                }
                            }
                            ci.setItem(slot, providedTemplate);
                        }
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void notifyBlocksOfNeighbors(Level level, BlockPos pos) {
        if (level != null && !level.isClientSide) {
            TickHandler.instance().addCallable(level, new BlockUpdate(pos));
        }
    }

    public static boolean isSortOrderAvailable(SortOrder order) {
        return true;
    }

    /**
     * Retrieves a BlockEntity from a given position, but only if that particular BlockEntity would be in a state where
     * it would be ticked by the chunk.
     * <p/>
     * This method also doesn't return a block entity on the client-side.
     */
    @Nullable
    public static BlockEntity getTickingBlockEntity(@Nullable Level level, BlockPos pos) {
        if (!areBlockEntitiesTicking(level, pos)) {
            return null;
        }

        return level.getBlockEntity(pos);
    }

    /**
     * Checks that the chunk at the given position in the given level is in a state where block entities would tick.
     * This means that it must both be fully loaded, and close enough to a ticking ticket.
     */
    public static boolean areBlockEntitiesTicking(@Nullable Level level, BlockPos pos) {
        return areBlockEntitiesTicking(level, ChunkPos.asLong(pos));
    }

    public static boolean areBlockEntitiesTicking(@Nullable Level level, long chunkPos) {
        // isPositionTicking checks both that the chunk is loaded, and that it's in ticking range...
        return level instanceof ServerLevel serverLevel && serverLevel.getChunkSource().isPositionTicking(chunkPos);
    }

    /**
     * Create a full packet of the chunks data with lighting.
     */
    public static Packet<?> getFullChunkPacket(LevelChunk c) {
        return new ClientboundLevelChunkWithLightPacket(c, c.getLevel().getLightEngine(), null, null);
    }

    public static ItemStack getInsertionRemainder(ItemStack original, long inserted) {
        if (inserted >= original.getCount()) {
            return ItemStack.EMPTY;
        } else {
            return copyStackWithSize(original, (int) (original.getCount() - inserted));
        }
    }

    public static ItemStack copyStackWithSize(ItemStack itemStack, int size) {
        if (size <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = itemStack.copy();
        copy.setCount(size);
        return copy;
    }

    /**
     * Send an update packet for the block entity at the given position to the player immediately, if they're a server
     * player.
     */
    public static void sendImmediateBlockEntityUpdate(Player player, BlockPos pos) {
        if (player instanceof ServerPlayer serverPlayer) {
            var be = player.level().getBlockEntity(pos);
            if (be != null) {
                var packet = be.getUpdatePacket();
                if (packet != null) {
                    serverPlayer.connection.send(packet);
                }
            }
        }
    }

    /**
     * Checks if the given level is a "fake" level used by Ponder to render our BE.
     */
    public static boolean isPonderLevel(Level level) {
        return ponderLevelClass != null && ponderLevelClass.isInstance(level);
    }

    /**
     * @return True if AE2 is being run within a dev environment.
     */
    public static boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }
}
