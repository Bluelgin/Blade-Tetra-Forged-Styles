package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import dev.bladetetra.network.AwakenedBladeVfxPacket.SakuraCue;
import dev.bladetetra.network.ModNetwork;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** One swing cue per committed native action, one confirmation per attack.
 * No virtual combo query, shard position heartbeat, damage or item writes.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SakuraCombatVisuals {
    private static final Map<UUID, Attack> CURRENT = new HashMap<>();
    private static final Map<EntitySlashEffect, Attack> SLASHES = new WeakHashMap<>();
    private static long nextSequence;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void slash(SlashBladeEvent.DoSlashEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player) || !SakuraEndlessFlow.eligible(event.getBlade())) return;
        int stage = SakuraFlowRules.stage(event.getSlashBladeState().getComboSeq());
        if (stage == 0) return;
        long at = event.getSlashBladeState().getLastActionTime();
        Attack previous = CURRENT.get(player.getUUID());
        if (previous != null && previous.blade == event.getBlade()
                && previous.stage == stage && previous.at == at) return;
        var tag = event.getBlade().getTag();
        if (tag == null || tag.getString("id").isBlank()) return;
        Attack attack = new Attack(player, event.getBlade(), tag.getString("id"), stage, ++nextSequence, at);
        CURRENT.put(player.getUUID(), attack);
        Entity locked = event.getSlashBladeState().getTargetEntity(player.level());
        Vec3 end = locked instanceof LivingEntity living && living.isAlive()
                && player.distanceToSqr(living) <= 144 ? living.getBoundingBox().getCenter()
                : player.getEyePosition().add(player.getLookAngle().scale(3.5));
        // Lock-on is a trajectory hint, never a confirmed hit flash.
        send(attack, AwakenedBladeVfxPacket.SAKURA_SWING, end,
                locked instanceof LivingEntity && player.distanceToSqr(locked) <= 144 ? locked.getId() : -1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void spawned(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntitySlashEffect slash)
                || !(slash.getOwner() instanceof ServerPlayer player)) return;
        Attack attack = CURRENT.get(player.getUUID());
        if (attack != null && attack.blade == player.getMainHandItem()
                && player.level().dimension().equals(attack.dimension)
                && player.level().getGameTime() - attack.at <= 7
                && player.getMainHandItem().getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                    .map(state -> state.getLastActionTime() == attack.at
                        && SakuraFlowRules.stage(state.getComboSeq()) == attack.stage).orElse(false)) {
            if (SLASHES.size() >= 4096) SLASHES.clear();
            SLASHES.put(slash, attack);
        }
    }

    public static void confirm(ServerPlayer player, ItemStack blade, Entity direct, LivingEntity target) {
        if (!SakuraEndlessFlow.eligible(blade) || !(direct instanceof EntitySlashEffect slash)) return;
        Attack attack = SLASHES.get(slash);
        if (attack == null || attack.player != player || attack.blade != blade || attack.confirmed
                || attack.slot != player.getInventory().selected
                || !player.level().dimension().equals(attack.dimension)
                || player.level().getGameTime() - attack.at > 20) return;
        attack.confirmed = true;
        send(attack, AwakenedBladeVfxPacket.SAKURA_HIT, target.getBoundingBox().getCenter(), target.getId());
    }

    private static void send(Attack attack, int type, Vec3 end, int target) {
        ServerPlayer player = attack.player;
        Vec3 origin = player.getEyePosition();
        var packet = new AwakenedBladeVfxPacket(3, type, origin.x, origin.y, origin.z,
                end.x, end.y, end.z, 0, new SakuraCue(player.getUUID(), attack.bladeId, attack.slot,
                player.level().dimension().location(), attack.stage, attack.sequence, attack.at, target));
        if (packet.valid()) ModNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer player)) return;
        Attack attack = CURRENT.get(player.getUUID());
        if (attack != null && (attack.blade != player.getMainHandItem() || attack.slot != player.getInventory().selected
                || !SakuraEndlessFlow.eligible(attack.blade) || !player.isAlive()
                || !player.level().dimension().equals(attack.dimension)
                || player.level().getGameTime() - attack.at > 40)) CURRENT.remove(player.getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        CURRENT.remove(e.getEntity().getUUID());
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {
        CURRENT.clear(); SLASHES.clear(); nextSequence = 0;
    }
    private static final class Attack {
        final ServerPlayer player;
        final ItemStack blade;
        final String bladeId;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final int stage, slot;
        final long sequence, at;
        boolean confirmed;
        Attack(ServerPlayer player, ItemStack blade, String id, int stage, long sequence, long at) {
            this.player = player; this.blade = blade; this.bladeId = id;
            this.dimension = player.level().dimension(); this.slot = player.getInventory().selected;
            this.stage = stage; this.sequence = sequence; this.at = at;
        }
    }
    private SakuraCombatVisuals() {}
}
