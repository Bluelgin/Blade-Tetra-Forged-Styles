package dev.bladetetra.visual;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Extends the blade-material color used by ordinary slash effects to
 * Resharped's ranged arts. The source material is resolved only while the
 * entity is spawned; no material identifier is persisted on the projectile.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaterialProjectileVisualHandler {
    @SubscribeEvent
    public static void onProjectileJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity instanceof EntityJudgementCut judgementCut) {
            ItemStack blade = getModularBlade(judgementCut.getShooter());
            if (!blade.isEmpty()) {
                int color = MaterialSlashEffectResolver.resolve(blade).color();
                judgementCut.setColor(
                        MaterialSlashEffectResolver.blendTowardWhite(color, 0.14F));
            }
            return;
        }

        if (entity instanceof EntityAbstractSummonedSword summonedSword) {
            ItemStack blade = getModularBlade(summonedSword.getShooter());
            if (blade.isEmpty()) {
                return;
            }

            int color = MaterialSlashEffectResolver.resolve(blade).color();
            if (summonedSword instanceof EntityDrive) {
                color = MaterialSlashEffectResolver.blendTowardWhite(color, 0.08F);
            }
            summonedSword.setColor(color);
        }
    }

    private static ItemStack getModularBlade(Entity shooter) {
        if (!(shooter instanceof LivingEntity living)) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = living.getMainHandItem();
        return stack.getItem() instanceof ModularSlashBladeItem
                ? stack
                : ItemStack.EMPTY;
    }

    private MaterialProjectileVisualHandler() {
    }
}
