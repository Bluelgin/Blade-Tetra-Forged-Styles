package dev.bladetetra.challenge;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ritual roles are attached only to session-owned enemies. */
public enum DivineDomainEnemyRole {
    ORDINARY("entity.blade_tetra.divine.ordinary"), TAINTED("entity.blade_tetra.divine.tainted"), INVADER("entity.blade_tetra.divine.invader"),
    GUARDIAN("entity.blade_tetra.divine.guardian"), PURSUER("entity.blade_tetra.divine.pursuer"), FINAL("entity.blade_tetra.divine.final");

    private static final String TAG = "blade_tetra_divine_role";
    private final String nameKey;
    DivineDomainEnemyRole(String nameKey) { this.nameKey = nameKey; }

    public static DivineDomainEnemyRole of(net.minecraft.world.entity.Entity entity) {
        int index = entity.getPersistentData().getInt(TAG);
        return index >= 0 && index < values().length ? values()[index] : ORDINARY;
    }

    static void assign(Mob mob, DivineDomainTier tier, int index, boolean elite, boolean finalBoss) {
        DivineDomainEnemyRole role = finalBoss ? FINAL : elite ? TAINTED : ORDINARY;
        if (!finalBoss && tier.hasAdvancedRoles()) {
            role = switch (Math.floorMod(index, 7)) {
                case 0 -> INVADER;
                case 1 -> GUARDIAN;
                case 2 -> PURSUER;
                default -> role;
            };
        }
        mob.getPersistentData().putInt(TAG, role.ordinal());
        mob.setCustomName(Component.translatable(role.nameKey));
        if (role == PURSUER && mob.getAttribute(Attributes.MOVEMENT_SPEED) != null)
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.42);
        if (role == FINAL) {
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(tier == DivineDomainTier.AVICI ? 400 : 240);
            mob.setHealth(mob.getMaxHealth());
            mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(tier == DivineDomainTier.AVICI ? 14 : 10);
            mob.getAttribute(Attributes.ARMOR).setBaseValue(10);
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            mob.setDropChance(EquipmentSlot.MAINHAND, 0);
        }
    }
}
