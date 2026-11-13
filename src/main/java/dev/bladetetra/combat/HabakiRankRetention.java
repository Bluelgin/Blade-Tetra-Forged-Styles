package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class HabakiRankRetention {
    public interface DecayClock {
        void bladeTetra$tickRankDecay(long now, boolean holdingHabaki);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var player = event.player;
        var blade = player.getMainHandItem();
        boolean holdingHabaki = player.isAlive() && !player.isSpectator()
                && blade.getItem() instanceof ModularSlashBladeItem
                && ComponentEffectResolver.hasModule(blade, ModularSlashBladeItem.HABAKI_SLOT,
                        ModularSlashBladeItem.PRECISION_HABAKI_MODULE);
        player.getCapability(CapabilityConcentrationRank.RANK_POINT).ifPresent(rank -> {
            if (rank instanceof DecayClock clock) clock.bladeTetra$tickRankDecay(player.level().getGameTime(), holdingHabaki);
        });
    }

    private HabakiRankRetention() {}
}
