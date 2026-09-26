package dev.bladetetra.combat;

import dev.bladetetra.item.ForgedSlashArtOrbItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Stable, versioned snapshot of a player-authored Slash Art.
 *
 * <p>The Tetra orb owns editable module state. Blades only receive this compact
 * snapshot, so editing the orb later never mutates an already-inscribed weapon.</p>
 */
public record ForgedSlashArtSpec(
        String coreVariant,
        ForgedSlashArtPlan.Technique primary,
        ForgedSlashArtPlan.Technique secondary,
        ForgedSlashArtPlan.Modifier modifier) {
    private static final int VERSION = 1;
    private static final String INSCRIPTION_KEY = "blade_tetra_forged_slash_art";
    private static final String PREVIOUS_SLASH_ART_KEY =
            "blade_tetra_forged_previous_slash_art";
    private static final String VERSION_KEY = "version";
    private static final String CORE_KEY = "core";
    private static final String PRIMARY_KEY = "primary";
    private static final String SECONDARY_KEY = "secondary";
    private static final String MODIFIER_KEY = "modifier";

    public static ForgedSlashArtSpec fromOrb(ItemStack orb) {
        if (orb == null || !(orb.getItem() instanceof ForgedSlashArtOrbItem)
                || !ComponentEffectResolver.hasModule(
                        orb, ForgedSlashArtOrbItem.SA_CORE_SLOT,
                        ForgedSlashArtOrbItem.SA_CORE_MODULE)
                || !ComponentEffectResolver.hasModule(
                        orb, ForgedSlashArtOrbItem.SA_PRIMARY_SLOT,
                        ForgedSlashArtOrbItem.SA_PRIMARY_MODULE)
                || !ComponentEffectResolver.hasModule(
                        orb, ForgedSlashArtOrbItem.SA_SECONDARY_SLOT,
                        ForgedSlashArtOrbItem.SA_SECONDARY_MODULE)
                || !ComponentEffectResolver.hasModule(
                        orb, ForgedSlashArtOrbItem.SA_MODIFIER_SLOT,
                        ForgedSlashArtOrbItem.SA_MODIFIER_MODULE)) {
            return null;
        }

        String core = ComponentEffectResolver.moduleVariant(
                orb, ForgedSlashArtOrbItem.SA_CORE_SLOT);
        ForgedSlashArtPlan.Technique primary =
                ForgedSlashArtPlan.Technique.fromVariant(
                        ComponentEffectResolver.moduleVariant(
                                orb, ForgedSlashArtOrbItem.SA_PRIMARY_SLOT));
        ForgedSlashArtPlan.Technique secondary =
                ForgedSlashArtPlan.Technique.fromVariant(
                        ComponentEffectResolver.moduleVariant(
                                orb, ForgedSlashArtOrbItem.SA_SECONDARY_SLOT));
        ForgedSlashArtPlan.Modifier modifier =
                ForgedSlashArtPlan.Modifier.fromVariant(
                        ComponentEffectResolver.moduleVariant(
                                orb, ForgedSlashArtOrbItem.SA_MODIFIER_SLOT));
        if (core.isBlank() || primary == null || secondary == null || modifier == null) {
            return null;
        }
        return new ForgedSlashArtSpec(core, primary, secondary, modifier);
    }

    public static ForgedSlashArtSpec fromBlade(ItemStack blade) {
        if (blade == null || blade.isEmpty()) {
            return null;
        }
        CompoundTag root = blade.getTag();
        if (root == null || !root.contains(INSCRIPTION_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        return fromTag(root.getCompound(INSCRIPTION_KEY));
    }

    static ForgedSlashArtSpec fromTag(CompoundTag tag) {
        if (tag == null || tag.getInt(VERSION_KEY) != VERSION) {
            return null;
        }
        String core = tag.getString(CORE_KEY);
        ForgedSlashArtPlan.Technique primary =
                ForgedSlashArtPlan.Technique.fromVariant(tag.getString(PRIMARY_KEY));
        ForgedSlashArtPlan.Technique secondary =
                ForgedSlashArtPlan.Technique.fromVariant(tag.getString(SECONDARY_KEY));
        ForgedSlashArtPlan.Modifier modifier =
                ForgedSlashArtPlan.Modifier.fromVariant(tag.getString(MODIFIER_KEY));
        if (core.isBlank() || primary == null || secondary == null || modifier == null) {
            return null;
        }
        return new ForgedSlashArtSpec(core, primary, secondary, modifier);
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_KEY, VERSION);
        tag.putString(CORE_KEY, coreVariant);
        tag.putString(PRIMARY_KEY, primary.id());
        tag.putString(SECONDARY_KEY, secondary.id());
        tag.putString(MODIFIER_KEY, modifier.id());
        return tag;
    }

    public void writeToBlade(ItemStack blade) {
        blade.getOrCreateTag().put(INSCRIPTION_KEY, toTag());
    }

    public static void clearFromBlade(ItemStack blade) {
        CompoundTag tag = blade.getTag();
        if (tag != null) {
            tag.remove(INSCRIPTION_KEY);
        }
    }

    public static boolean hasInscription(ItemStack blade) {
        return fromBlade(blade) != null;
    }

    public static int installedOrbComponentCount(ItemStack orb) {
        if (orb == null || !(orb.getItem() instanceof ForgedSlashArtOrbItem)) {
            return 0;
        }
        int count = 0;
        if (ComponentEffectResolver.hasModule(
                orb, ForgedSlashArtOrbItem.SA_CORE_SLOT,
                ForgedSlashArtOrbItem.SA_CORE_MODULE)) {
            count++;
        }
        if (ComponentEffectResolver.hasModule(
                orb, ForgedSlashArtOrbItem.SA_PRIMARY_SLOT,
                ForgedSlashArtOrbItem.SA_PRIMARY_MODULE)) {
            count++;
        }
        if (ComponentEffectResolver.hasModule(
                orb, ForgedSlashArtOrbItem.SA_SECONDARY_SLOT,
                ForgedSlashArtOrbItem.SA_SECONDARY_MODULE)) {
            count++;
        }
        if (ComponentEffectResolver.hasModule(
                orb, ForgedSlashArtOrbItem.SA_MODIFIER_SLOT,
                ForgedSlashArtOrbItem.SA_MODIFIER_MODULE)) {
            count++;
        }
        return count;
    }

    public static void rememberPreviousSlashArt(
            ItemStack blade, ResourceLocation slashArt) {
        CompoundTag tag = blade.getOrCreateTag();
        if (!tag.contains(PREVIOUS_SLASH_ART_KEY, Tag.TAG_STRING)
                && slashArt != null) {
            tag.putString(PREVIOUS_SLASH_ART_KEY, slashArt.toString());
        }
    }

    public static ResourceLocation previousSlashArt(ItemStack blade) {
        CompoundTag tag = blade.getTag();
        if (tag == null || !tag.contains(PREVIOUS_SLASH_ART_KEY, Tag.TAG_STRING)) {
            return null;
        }
        return ResourceLocation.tryParse(tag.getString(PREVIOUS_SLASH_ART_KEY));
    }

    public static void clearPreviousSlashArt(ItemStack blade) {
        CompoundTag tag = blade.getTag();
        if (tag != null) {
            tag.remove(PREVIOUS_SLASH_ART_KEY);
        }
    }

    public String key() {
        return coreVariant + "|" + primary.id() + "|" + secondary.id()
                + "|" + modifier.id();
    }
}
