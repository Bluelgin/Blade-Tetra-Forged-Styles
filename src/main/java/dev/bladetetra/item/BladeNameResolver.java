package dev.bladetetra.item;

import dev.bladetetra.challenge.BoundaryForging;
import dev.bladetetra.combat.PotatoBladeHandler;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.forging.FoxLegacyParts;
import dev.bladetetra.forging.NamedLegacyParts;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Resolves authored identities before falling back to Tetra's generated name. */
public final class BladeNameResolver {
    public static Component resolve(ModularSlashBladeItem item, ItemStack stack) {
        if (BoundaryForging.isForged(stack)) {
            return Component.translatable(SoulLegacyState.active(stack)
                    == SoulLegacyState.Legacy.AKATSUKI
                    ? "name.blade_tetra.akatsuki_boundary"
                    : "name.blade_tetra.boundary_blade");
        }
        switch (SoulLegacyState.active(stack)) {
            case AKATSUKI -> {
                return Component.translatable("name.blade_tetra.akatsuki");
            }
            case KYOUKA -> {
                return Component.translatable("name.blade_tetra.kyouka");
            }
            case SENBONZAKURA -> {
                return Component.translatable("name.blade_tetra.senbonzakura");
            }
            case RAIKIRI -> {
                return Component.translatable("name.blade_tetra.raikiri");
            }
            default -> {
            }
        }
        FoxLegacyParts.Color foxSet = FoxLegacyParts.fromStack(stack).completeSet();
        if (foxSet == FoxLegacyParts.Color.BLACK) {
            return Component.translatable("name.blade_tetra.legacy.fox_black");
        }
        if (foxSet == FoxLegacyParts.Color.WHITE) {
            return Component.translatable("name.blade_tetra.legacy.fox_white");
        }
        var namedSet = NamedLegacyParts.fromStack(stack).completeSet();
        if (namedSet != null)
            return Component.translatable("name.blade_tetra.legacy.named",
                    Component.translatable(namedSet.translationKey()));
        boolean inheritedLegacy = BladeLegacyEasterEggs.isBanshoUnlocked(stack)
                || BladeLegacyEasterEggs.isBairenUnlocked(stack)
                || BladeLegacyEasterEggs.isShoshinUnlocked(stack);
        if (!inheritedLegacy && PotatoBladeHandler.isPotatoBlade(stack)) {
            return Component.translatable("name.blade_tetra.potato_blade");
        }
        var fishBlade = SaltyFishParts.blade(stack);
        var fishSaya = SaltyFishParts.saya(stack);
        if (fishBlade != null && !inheritedLegacy && !dev.bladetetra.combat.ComponentEffectResolver.hasModule(stack, ModularSlashBladeItem.INSCRIPTION_SLOT,
                ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE)) {
            if (fishSaya != null && !(fishBlade == SaltyFishParts.Length.SHORT && fishSaya == fishBlade)) {
                return Component.translatable(fishBlade == SaltyFishParts.Length.LONG && fishSaya == fishBlade
                        ? "name.blade_tetra.salty_fish.long" : "name.blade_tetra.salty_fish");
            }
            return Component.translatable("name.blade_tetra.fishbone." + fishBlade.id);
        }
        String tetraName = item.getItemName(stack);
        return tetraName.isBlank()
                ? Component.translatable(item.getDescriptionId())
                : Component.literal(tetraName);
    }

    private BladeNameResolver() {
    }
}
