package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.common.Mod;

/** Marks successful locked Slash Arts so Mikage can distinguish them from ordinary hits. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageCounterEvents {
    static final String SA_UNTIL = "blade_tetra_mikage_sa_until";
    static final String SA_KIND = "blade_tetra_mikage_sa_kind";
    static final String SA_SERIAL = "blade_tetra_mikage_sa_serial";

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        LivingEntity user = event.getEntityLiving();
        if (user.level().isClientSide() || event.getType() != SlashArts.ArtsType.Success) {
            return;
        }
        Entity target = event.getSlashBladeState().getTargetEntity(user.level());
        if (!(target instanceof MikageEntity)) {
            return;
        }
        String slashArtKey = event.getSlashBladeState().getSlashArtsKey().toString();
        String comboKey = event.getComboState().toString();
        if (isJudgementCut(slashArtKey) || isJudgementCut(comboKey)) {
            ((MikageEntity) target).registerJudgementCutCast(user);
            return;
        }
        user.getPersistentData().putLong(SA_UNTIL, user.level().getGameTime() + 30L);
        user.getPersistentData().putString(SA_KIND, comboKey);
        user.getPersistentData().putInt(SA_SERIAL,
                user.getPersistentData().getInt(SA_SERIAL) + 1);
    }

    @SubscribeEvent
    public static void onJudgementCutSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof mods.flammpfeil.slashblade.entity.EntityJudgementCut cut)
                || !(cut.getOwner() instanceof LivingEntity owner)) {
            return;
        }
        final Entity[] target = {null};
        owner.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .ifPresent(state -> target[0] = state.getTargetEntity(owner.level()));
        if (target[0] instanceof MikageEntity mikage
                && mikage.isJudgementCutBlocked(owner)) {
            event.setCanceled(true);
        }
    }

    private static boolean isJudgementCut(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("judgement") || normalized.contains("judgment")
                || normalized.contains("dimension_slash");
    }

    private MikageCounterEvents() {
    }
}
