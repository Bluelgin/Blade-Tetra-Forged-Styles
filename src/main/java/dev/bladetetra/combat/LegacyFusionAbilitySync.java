package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reconciles structural Slash Arts and Special Effects with assembled blade modules. */
final class LegacyFusionAbilitySync {
    private static final String LAST_ACTIVE = "blade_tetra_legacy_fusion_active";
    private static final String PREVIOUS_SLASH_ART =
            "blade_tetra_legacy_fusion_previous_slash_art";
    private static final String ABILITY_OWNER = "blade_tetra_named_ability_owner";
    private static final String APPLIED_SLASH_ART = "blade_tetra_named_applied_slash_art";
    private static final String OWNED_SPECIAL_EFFECTS = "blade_tetra_named_owned_effects";

    static void sync(ItemStack blade, ISlashBladeState state) {
        LegacyFusion active = LegacyFusion.active(blade);
        ForgedSlashArtPlan forged = ForgedSlashArtPlan.from(blade);
        CompoundTag tag = blade.getOrCreateTag();
        String previous = tag.getString(LAST_ACTIVE);
        String current = active == null ? "" : active.id();

        if (active != LegacyFusion.WHITE_SAYA_BLACK_HILT) {
            TwinFoxFusionHandler.clearStoredPursuit(tag);
        }

        ResourceLocation piercing = ModSlashBladeAbilities.TWIN_FOX_PIERCING.getId();
        NamedLegacyParts parts = NamedLegacyParts.fromStack(blade);
        ProgrammaticFusionPlan programmatic = active == null
                ? ProgrammaticFusionPlan.from(parts) : null;
        LegacyImprintKind orthodox = active == null && programmatic == null
                ? parts.completeSet() : null;
        if (orthodox != null && !orthodox.supportsOrthodoxInheritance()) {
            orthodox = null;
        }

        // Structural named-blade identity wins over a forged inscription.
        // A forged spec may stay on the item as a dormant player-authored preset,
        // but it must never replace an authored/programmatic/inherited SA while
        // leaving that fitting's coupled SE behind (for example Dead Thought).
        //
        // Priority:
        // authored fusion > programmatic mixed fusion > orthodox inheritance
        // > forged custom SA > previous/external SA.
        String owner = active != null
                ? "fusion:" + active.id()
                : programmatic != null
                        ? "programmatic:" + programmatic.key()
                        : orthodox != null
                                ? "orthodox:" + orthodox.id()
                                : forged == null ? "" : "forged:" + forged.key();
        ResourceLocation desiredSlashArt = active != null
                ? active.slashArt()
                : programmatic != null
                        ? ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId()
                        : orthodox != null
                                ? LegacyAbilityResolver.registeredSlashArt(
                                        orthodox.slashArt())
                                : forged == null ? null
                                        : ModSlashBladeAbilities.FORGED_SLASH_ART.getId();
        List<ResourceLocation> desiredEffects = active != null
                ? active.specialEffects()
                : programmatic != null
                        ? List.of()
                        : orthodox == null ? List.of()
                                : LegacyAbilityResolver.registeredSpecialEffects(
                                        orthodox.specialEffects());

        String previousOwner = tag.getString(ABILITY_OWNER);
        // Migrate the first fusion build without losing the SA it displaced.
        if (previousOwner.isEmpty()
                && LegacyFusion.BLACK_SAYA_WHITE_HILT.id().equals(previous)
                && piercing.equals(state.getSlashArtsKey())) {
            previousOwner = "fusion:" + LegacyFusion.BLACK_SAYA_WHITE_HILT.id();
            tag.putString(ABILITY_OWNER, previousOwner);
            tag.putString(APPLIED_SLASH_ART, piercing.toString());
        }
        migrateLegacyFusionEffects(tag, state, previousOwner);

        if (!owner.equals(previousOwner)) {
            restoreStructuralSlashArt(tag, state);
            removeOwnedSpecialEffects(tag, state);
            reconcileStructuralSlashArt(tag, state, desiredSlashArt);
            reconcileOwnedSpecialEffects(tag, state, desiredEffects);
            if (owner.isEmpty()) {
                tag.remove(ABILITY_OWNER);
            } else {
                tag.putString(ABILITY_OWNER, owner);
            }
        } else if (!owner.isEmpty()) {
            // Structural abilities remain authoritative while their owner is
            // assembled, but registry/data changes must also be able to withdraw an
            // ability without requiring the player to disassemble the weapon first.
            reconcileStructuralSlashArt(tag, state, desiredSlashArt);
            reconcileOwnedSpecialEffects(tag, state, desiredEffects);
        }

        if (current.isEmpty()) {
            tag.remove(LAST_ACTIVE);
        } else if (!current.equals(previous)) {
            tag.putString(LAST_ACTIVE, current);
        }
    }

    private static void migrateLegacyFusionEffects(CompoundTag tag,
            ISlashBladeState state, String previousOwner) {
        if (!tag.getString(OWNED_SPECIAL_EFFECTS).isEmpty()
                || !previousOwner.startsWith("fusion:")) {
            return;
        }
        LegacyFusion fusion = LegacyFusion.byId(previousOwner.substring("fusion:".length()));
        if (fusion == null || fusion.specialEffects().isEmpty()) {
            return;
        }
        List<String> owned = new ArrayList<>();
        for (ResourceLocation effect : fusion.specialEffects()) {
            if (state.getSpecialEffects().contains(effect)) {
                owned.add(effect.toString());
            }
        }
        writeOwnedSpecialEffects(tag, owned);
    }

    private static void applyStructuralSlashArt(CompoundTag tag,
            ISlashBladeState state, ResourceLocation slashArt) {
        if (slashArt == null || slashArt.equals(state.getSlashArtsKey())) {
            return;
        }
        tag.putString(PREVIOUS_SLASH_ART, state.getSlashArtsKey().toString());
        tag.putString(APPLIED_SLASH_ART, slashArt.toString());
        state.setSlashArtsKey(slashArt);
    }

    private static void reconcileStructuralSlashArt(CompoundTag tag,
            ISlashBladeState state, ResourceLocation slashArt) {
        ResourceLocation applied = ResourceLocation.tryParse(tag.getString(APPLIED_SLASH_ART));
        if (slashArt == null) {
            if (applied != null) {
                restoreStructuralSlashArt(tag, state);
            }
            return;
        }
        if (slashArt.equals(state.getSlashArtsKey())) {
            return;
        }
        if (applied == null) {
            applyStructuralSlashArt(tag, state, slashArt);
            return;
        }
        tag.putString(APPLIED_SLASH_ART, slashArt.toString());
        state.setSlashArtsKey(slashArt);
    }

    private static void restoreStructuralSlashArt(CompoundTag tag,
            ISlashBladeState state) {
        ResourceLocation applied = ResourceLocation.tryParse(tag.getString(APPLIED_SLASH_ART));
        if (applied != null && applied.equals(state.getSlashArtsKey())) {
            ResourceLocation restored = ResourceLocation.tryParse(
                    tag.getString(PREVIOUS_SLASH_ART));
            state.setSlashArtsKey(restored == null
                    ? mods.flammpfeil.slashblade.registry.SlashArtsRegistry.NONE.getId()
                    : restored);
        }
        tag.remove(PREVIOUS_SLASH_ART);
        tag.remove(APPLIED_SLASH_ART);
    }

    private static void reconcileOwnedSpecialEffects(CompoundTag tag,
            ISlashBladeState state, List<ResourceLocation> desired) {
        Set<ResourceLocation> desiredSet = new LinkedHashSet<>(desired);
        List<String> owned = new ArrayList<>();
        String stored = tag.getString(OWNED_SPECIAL_EFFECTS);
        if (!stored.isEmpty()) {
            owned.addAll(List.of(stored.split(",")));
        }

        var iterator = owned.iterator();
        while (iterator.hasNext()) {
            String value = iterator.next();
            ResourceLocation effect = ResourceLocation.tryParse(value);
            if (effect == null || !desiredSet.contains(effect)) {
                if (effect != null) {
                    state.getSpecialEffects().removeIf(effect::equals);
                }
                iterator.remove();
            }
        }

        for (ResourceLocation effect : desiredSet) {
            if (!state.getSpecialEffects().contains(effect)) {
                state.addSpecialEffect(effect);
                if (!owned.contains(effect.toString())) {
                    owned.add(effect.toString());
                }
            }
        }
        writeOwnedSpecialEffects(tag, owned);
    }

    private static void removeOwnedSpecialEffects(CompoundTag tag,
            ISlashBladeState state) {
        String stored = tag.getString(OWNED_SPECIAL_EFFECTS);
        if (!stored.isEmpty()) {
            for (String value : stored.split(",")) {
                ResourceLocation effect = ResourceLocation.tryParse(value);
                if (effect != null) {
                    state.getSpecialEffects().removeIf(effect::equals);
                }
            }
        }
        tag.remove(OWNED_SPECIAL_EFFECTS);
    }

    private static void writeOwnedSpecialEffects(CompoundTag tag, List<String> owned) {
        if (owned.isEmpty()) {
            tag.remove(OWNED_SPECIAL_EFFECTS);
        } else {
            tag.putString(OWNED_SPECIAL_EFFECTS, String.join(",", owned));
        }
    }

    private LegacyFusionAbilitySync() {
    }
}
