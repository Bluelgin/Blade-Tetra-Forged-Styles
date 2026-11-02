package dev.bladetetra.combat;

import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.world.item.ItemStack;

/** A confirmation belongs to an ordered source pair, not to every future fitting. */
public final class ProgrammaticFusionAttunement {
    private static final String PAIR = "blade_tetra_fused_pair";
    private static final String VERSION = "blade_tetra_fusion_confirmation_version";

    public static boolean matches(ItemStack stack, ProgrammaticFusionPlan plan) {
        return stack != null && plan != null && stack.hasTag()
                && plan.key().equals(stack.getTag().getString(PAIR));
    }

    public static boolean confirm(ItemStack stack) {
        ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.candidate(stack);
        if (plan == null) return false;
        stack.getOrCreateTag().putString(PAIR, plan.key());
        stack.getOrCreateTag().putInt(VERSION, 1);
        return true;
    }

    /** Only an old, actually installed structural SA is grandfathered in. */
    static void reconcile(ItemStack stack, ISlashBladeState state) {
        var tag = stack.getOrCreateTag();
        ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.candidate(stack);
        if (plan == null && !tag.contains(PAIR)
                && !tag.getString("blade_tetra_named_ability_owner").startsWith("programmatic:")) return;
        if (!tag.contains(VERSION)) {
            if (plan != null
                    && ("programmatic:" + plan.key()).equals(tag.getString("blade_tetra_named_ability_owner"))
                    && ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId().equals(state.getSlashArtsKey())) {
                tag.putString(PAIR, plan.key());
            }
            tag.putInt(VERSION, 1);
        }
        invalidateChangedPair(stack);
    }

    public static void invalidateChangedPair(ItemStack stack) {
        if (stack == null || !stack.hasTag() || !stack.getTag().contains(PAIR)) return;
        if (!matches(stack, ProgrammaticFusionPlan.candidate(stack))) stack.getTag().remove(PAIR);
    }

    /** ConfigSchematic temporarily clears the source while rebuilding a module. */
    public static void preserveUnchangedPair(ItemStack original, ItemStack result) {
        var before = ProgrammaticFusionPlan.from(original);
        var after = ProgrammaticFusionPlan.candidate(result);
        if (before != null && after != null && before.key().equals(after.key())) confirm(result);
        else invalidateChangedPair(result);
    }

    private ProgrammaticFusionAttunement() {}
}
