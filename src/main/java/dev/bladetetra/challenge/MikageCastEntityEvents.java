package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers authored native visuals/projectiles with their current cast, without realm scans. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageCastEntityEvents {
    @SubscribeEvent
    public static void onSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        Entity owner = entity instanceof EntityAbstractSummonedSword sword ? sword.getShooter()
                : entity instanceof EntityJudgementCut cut ? cut.getOwner()
                : entity instanceof EntitySlashEffect slash ? slash.getShooter() : null;
        if (owner instanceof MikageEntity mikage) {
            var corridor = mikage.encounter().corridor();
            if (corridor == null || !corridor.combo.capture(entity)) mikage.attackTimeline().ownSpawn(entity);
        }
    }

    private MikageCastEntityEvents() {}
}
