package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.item.SaltyFishParts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SaltyFishFeedback {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        var target = event.getEntity();
        if (event.getAmount() <= 0 || !(target.level() instanceof ServerLevel level)
                || !(event.getSource().getDirectEntity() instanceof ServerPlayer player)) return;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ModularSlashBladeItem)
                || SaltyFishParts.blade(stack) == null) return;
        SaltyFishHitFeedback.play(level, player, target);
    }

    private SaltyFishFeedback() {}
}
