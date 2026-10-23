package dev.bladetetra.aura;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.CombatBalanceRuntime;
import dev.bladetetra.combat.CombatBalanceRules;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRankCapabilityProvider;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/** Hooks actual server-side slash creation, not clicks, hits or animation guesses. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SwordAuraTrigger {
    private static final Map<ServerPlayer, Long> LAST_BEAT = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSlashCreated(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof EntitySlashEffect slash)
                || !(slash.getShooter() instanceof ServerPlayer player)) return;
        if (!(player.getMainHandItem().getItem() instanceof ModularSlashBladeItem)
                || !SwordAuraMastery.knows(player)) return;
        // Reuse the server's actual native/forged SA context. An SA can emit a
        // normal-looking ComboState or slash, so a motion ID alone is not proof.
        if (!Double.isFinite(slash.getDamage()) || slash.getDamage() <= 0
                || CombatBalanceRuntime.kind(player, slash) != CombatBalanceRules.AttackKind.ORDINARY) return;
        ResourceLocation combo = player.getMainHandItem()
                .getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getComboSeq()).orElse(null);
        BladeStyle style = StyleResolver.resolve(player.getMainHandItem());
        if (!SwordAuraMotionFilter.permits(combo, style)) return;
        boolean sss = player.getCapability(ConcentrationRankCapabilityProvider.RANK_POINT)
                .map(rank -> rank.getRank(player.level().getGameTime())
                        == IConcentrationRank.ConcentrationRanks.SSS)
                .orElse(false);
        if (!sss) return;

        // Native B1 can spawn two real slashes during the same beat. Never turn
        // that paired visual into double projectile damage or a feedback loop.
        long tick = player.level().getGameTime();
        if (LAST_BEAT.getOrDefault(player, Long.MIN_VALUE) == tick) return;
        LAST_BEAT.put(player, tick);
        SwordAuraSpawner.emit(player, slash, style);
    }

    private SwordAuraTrigger() {}
}
