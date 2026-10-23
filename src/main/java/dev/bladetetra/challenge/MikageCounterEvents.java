package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Observe successful player arts for selection. Never delete, weaken or unlock their attacks. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageCounterEvents {
    @SubscribeEvent public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (event.getType() != SlashArts.ArtsType.Success || !(event.getEntityLiving() instanceof ServerPlayer player)) return;
        if (event.getSlashBladeState().getTargetEntity(player.level()) instanceof MikageEntity boss)
            boss.encounter().observeSlashArt(player, event.getSlashBladeState().getSlashArtsKey().toString());
    }
    private MikageCounterEvents() { }
}
