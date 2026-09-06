package dev.bladetetra.forging;

import dev.bladetetra.BladeTetra;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Persists historical player calibration while migrating obsolete per-blade copies. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyCalibration {
    private static final String PLAYER_ROOT = "blade_tetra_legacy_calibrations";
    public static final String STACK_ROOT = "blade_tetra_legacy_calibrations";

    public static LegacyCalibrationProfile learn(Player player, LegacyImprintKind kind,
            LegacyCalibrationProfile profile) {
        CompoundTag root = player.getPersistentData().getCompound(PLAYER_ROOT);
        long nextRevision = Math.max(player.level().getGameTime(),
                known(player, kind).revision() + 1L);
        LegacyCalibrationProfile stored = profile.withRevision(nextRevision);
        root.put(kind.id(), stored.write());
        player.getPersistentData().put(PLAYER_ROOT, root);
        return stored;
    }

    public static LegacyCalibrationProfile known(Player player, LegacyImprintKind kind) {
        CompoundTag root = player.getPersistentData().getCompound(PLAYER_ROOT);
        return root.contains(kind.id(), Tag.TAG_COMPOUND)
                ? LegacyCalibrationProfile.read(root.getCompound(kind.id()))
                : kind.defaultProfile();
    }

    public static LegacyCalibrationProfile fromStack(ItemStack stack,
            LegacyImprintKind kind) {
        if (kind == null) {
            return LegacyCalibrationProfile.DEFAULT;
        }
        CompoundTag root = stack.getOrCreateTag().getCompound(STACK_ROOT);
        String key = kind.id();
        return root.contains(key, Tag.TAG_COMPOUND)
                ? LegacyCalibrationProfile.read(root.getCompound(key))
                : kind.defaultProfile();
    }

    /**
     * Version 1.5 renders named fittings from the catalog profile and no longer
     * consumes a complete calibration snapshot from every blade. Old snapshots
     * could retain every fitting ever installed and grow once addons supplied
     * more named blades, so remove that redundant copy as soon as the item is
     * loaded in a player inventory. The player's learned records, Tetra module
     * variants and legacy identifiers remain untouched.
     */
    public static boolean migrateStack(ItemStack stack) {
        return stack != null && migrateStackTag(stack.getTag());
    }

    public static boolean migrateStackTag(CompoundTag stackTag) {
        if (stackTag == null || !stackTag.contains(STACK_ROOT)) {
            return false;
        }
        stackTag.remove(STACK_ROOT);
        return true;
    }

    /** Kept as a binary-safe bridge for development integrations from older builds. */
    @Deprecated
    public static void attachKnownProfiles(ItemStack stack, Player player) {
        migrateStack(stack);
    }



    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag learned = event.getOriginal().getPersistentData()
                .getCompound(PLAYER_ROOT);
        if (!learned.isEmpty()) {
            event.getEntity().getPersistentData().put(PLAYER_ROOT, learned.copy());
        }
    }

    private LegacyCalibration() {
    }
}
