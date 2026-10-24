package dev.bladetetra.aura;

import mods.flammpfeil.slashblade.util.AttackManager;
import dev.bladetetra.config.CombatBalanceConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.server.level.ServerPlayer;

import static mods.flammpfeil.slashblade.SlashBladeConfig.SLASHBLADE_DAMAGE_MULTIPLIER;

/** Snapshot a positive launch damage, leaving downstream damage events untouched. */
public final class SwordAuraDamage {
    // Approximate the native summoned sword's 95% single-hit baseline, while
    // allowing weapon attack attributes to grow in high-stat modpacks.
    static final double SUMMONED_SWORD_RATIO = 0.95D;
    static final double PANEL_CONTRIBUTION = 0.10D;

    public static float snapshot(ServerPlayer player) {
        double attack = Math.max(0.0D, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        int power = player.getMainHandItem().getEnchantmentLevel(Enchantments.POWER_ARROWS);
        double reference = Math.max(Mth.ceil((double) power), attack * PANEL_CONTRIBUTION);
        double amount = reference * SUMMONED_SWORD_RATIO
                * AttackManager.getSlashBladeDamageScale(player)
                * SLASHBLADE_DAMAGE_MULTIPLIER.get();
        return scale(amount, CombatBalanceConfig.SWORD_AURA.get());
    }

    static float scale(double amount, double multiplier) {
        double scaled = amount * multiplier;
        if (!Double.isFinite(scaled)) return 0.0F;
        // Float representation is a limitation of Minecraft's damage API, not
        // a gameplay soft cap. Do not clamp to arbitrary pack-size limits.
        return (float) Math.min(Float.MAX_VALUE, Math.max(0.0D, scaled));
    }

    private SwordAuraDamage() {}
}
