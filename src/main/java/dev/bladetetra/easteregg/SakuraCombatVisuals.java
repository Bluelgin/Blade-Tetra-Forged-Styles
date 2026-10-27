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
    public static void motion(mods.flammpfeil.slashblade.event.BladeMotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack blade = player.getMainHandItem();
        int stage = SakuraFlowRules.stage(event.getCombo());
        if (stage == 0 || !SakuraEndlessFlow.eligible(blade)) return;
        var state = blade.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE).orElse(null);
        if (state != null) begin(player, blade, stage, player.level().getGameTime(), state.getTargetEntity(player.level()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void slash(SlashBladeEvent.DoSlashEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player) || !SakuraEndlessFlow.eligible(event.getBlade())) return;
        int stage = SakuraFlowRules.stage(event.getSlashBladeState().getComboSeq());
        if (stage == 0) return;
        Attack attack = begin(player, event.getBlade(), stage, event.getSlashBladeState().getLastActionTime(),
                event.getSlashBladeState().getTargetEntity(player.level()));
        if (attack != null) attack.nativeSlashAt = player.level().getGameTime();
    }

    private static Attack begin(ServerPlayer player, ItemStack blade, int stage, long at, Entity locked) {
        Attack previous = CURRENT.get(player.getUUID());
        if (previous != null && previous.blade == blade && previous.stage == stage && previous.at == at
                && previous.slot == player.getInventory().selected
                && previous.dimension.equals(player.level().dimension()))
            return previous;
        var tag = blade.getTag();
        if (tag == null || tag.getString("id").isBlank()) return null;
        Attack attack = new Attack(player, blade, tag.getString("id"), stage, ++nextSequence, at);
        CURRENT.put(player.getUUID(), attack);
        boolean validTarget = locked instanceof LivingEntity living && living.isAlive()
                && player.distanceToSqr(living) <= 144;
        Vec3 end = validTarget ? locked.getBoundingBox().getCenter()
                : player.getEyePosition().add(player.getLookAngle().scale(3.5));
        // Motion is committed by native updateComboSeq (not virtual queries).
        // B1's first slash is six ticks later, so the rear blades must depart now.
        send(attack, AwakenedBladeVfxPacket.SAKURA_SWING, end, validTarget ? locked.getId() : -1);
        return attack;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void spawned(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntitySlashEffect slash)
                || !(slash.getOwner() instanceof ServerPlayer player)) return;
        Attack attack = CURRENT.get(player.getUUID());
        if (attack != null && attack.blade == player.getMainHandItem()
                && player.level().dimension().equals(attack.dimension)
                && attack.nativeSlashAt == player.level().getGameTime()
                && player.level().getGameTime() - attack.at <= 14
                && player.getMainHandItem().getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                    .map(state -> state.getLastActionTime() == attack.at
                        && SakuraFlowRules.stage(state.getComboSeq()) == attack.stage).orElse(false)) {
            if (SLASHES.size() >= 4096) SLASHES.clear();
            SLASHES.put(slash, attack);
        }
    }

    public static void confirm(ServerPlayer player, ItemStack blade, Entity direct, LivingEntity target) {
        if (!SakuraEndlessFlow.eligible(blade)) return;
        Attack attack;
        if (direct instanceof EntitySlashEffect slash) attack = SLASHES.get(slash);
        else if (direct == player) {
            attack = CURRENT.get(player.getUUID());
            // Vanilla direct melee can kill a target before B1's delayed slash.
            // Accept it only for the currently committed B action, never an old
            // flurry or an unrelated projectile/secondary skill.
            Attack current = attack;
            if (current == null || !blade.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                    .map(state -> state.getLastActionTime() == current.at
                        && SakuraFlowRules.stage(state.getComboSeq()) == current.stage).orElse(false)) return;
        } else return;
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
        SLASHES.values().removeIf(attack -> attack.player.getUUID().equals(e.getEntity().getUUID()));
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
        long nativeSlashAt = Long.MIN_VALUE;
        Attack(ServerPlayer player, ItemStack blade, String id, int stage, long sequence, long at) {
            this.player = player; this.blade = blade; this.bladeId = id;
            this.dimension = player.level().dimension(); this.slot = player.getInventory().selected;
            this.stage = stage; this.sequence = sequence; this.at = at;
        }
    }
    private SakuraCombatVisuals() {}
}
