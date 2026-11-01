package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.kyouka.KyoukaMirrorSessions;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.network.SoulActivityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static dev.bladetetra.easteregg.SoulLegacyState.Legacy;

/** Coalesces accepted skill cues per held weapon; sends no idle heartbeat or world VFX broadcast. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SoulActivityService {
    private static final Map<UUID, Binding> BINDINGS = new HashMap<>();
    private static final class Binding {
        final ItemStack blade;
        final int slot;
        final net.minecraft.resources.ResourceLocation dimension;
        int triggered, sustained;
        Binding(ServerPlayer p) { blade = p.getMainHandItem(); slot = p.getInventory().selected; dimension = p.level().dimension().location(); }
        boolean matches(ServerPlayer p) {
            return p.getMainHandItem() == blade && p.getInventory().selected == slot && p.level().dimension().location().equals(dimension);
        }
    }
    public static int bit(Legacy soul) { return soul == Legacy.NONE ? 0 : 1 << (soul.ordinal() - 1); }
    /** Called only after the owning controller accepts a skill, not for an attempted native action. */
    public static void triggered(ServerPlayer p, ItemStack blade, Legacy soul) {
        if (!AwakenedBladeCombat.replacesLegacy() || !p.isAlive() || p.isSpectator()
                || p.getMainHandItem() != blade || !SoulSkillState.available(blade, soul)) return;
        Binding b = binding(p);
        b.triggered |= bit(soul);
    }
    private static Binding binding(ServerPlayer p) {
        Binding b = BINDINGS.get(p.getUUID());
        if (b == null || !b.matches(p)) { b = new Binding(p); BINDINGS.put(p.getUUID(), b); }
        return b;
    }
    private static int sustained(ServerPlayer p) {
        ItemStack blade = p.getMainHandItem();
        int mask = 0;
        if (SoulSkillState.available(blade, Legacy.KYOUKA) && (KyoukaMirrorSessions.armored(p) || KyoukaMirrorSessions.hasPendingAttack(p))) mask |= bit(Legacy.KYOUKA);
        if (SoulSkillState.available(blade, Legacy.SENBONZAKURA) && (SakuraInfiniteDanceSA.active(p, blade)
                || SakuraStanceController.reassembling(p) || SakuraShardAttackService.hasPendingAttack(p))) mask |= bit(Legacy.SENBONZAKURA);
        if (SoulSkillState.available(blade, Legacy.RAIKIRI) && RaikiriActionObserver.thunderArmored(p)) mask |= bit(Legacy.RAIKIRI);
        if (SoulSkillState.available(blade, Legacy.AKATSUKI) && (AkatsukiBloodTendrils.active(p) || AkatsukiExecution.executing(p) || AkatsukiBloodBlade.presenting(p))) mask |= bit(Legacy.AKATSUKI);
        return mask;
    }
    /** Separate collection from delivery so the real controller transitions can be smoke-tested. */
    static SoulActivityPacket collect(ServerPlayer p) {
        if (!AwakenedBladeCombat.replacesLegacy() || !p.isAlive() || p.isSpectator()
                || SoulLegacyState.active(p.getMainHandItem()) == Legacy.NONE) {
            BINDINGS.remove(p.getUUID()); return null;
        }
        Binding previous = BINDINGS.get(p.getUUID());
        if (previous != null && !previous.matches(p)) { BINDINGS.remove(p.getUUID()); previous = null; }
        int active = sustained(p);
        if (previous == null && active == 0) return null;
        Binding b = binding(p);
        if (b.triggered == 0 && b.sustained == active) return null;
        var tag = b.blade.getOrCreateTag();
        if (!tag.hasUUID(SoulSkillState.BLADE_ID)) tag.putUUID(SoulSkillState.BLADE_ID, UUID.randomUUID());
        // Equipment must be queued first: the client validates the same server-assigned identity.
        p.containerMenu.broadcastChanges();
        var packet = new SoulActivityPacket(b.slot, tag.getUUID(SoulSkillState.BLADE_ID), b.dimension, b.triggered, active);
        b.triggered = 0; b.sustained = active;
        return packet;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        var packet = collect(p);
        if (packet != null) ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { BINDINGS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { BINDINGS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { BINDINGS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { BINDINGS.clear(); }
    private SoulActivityService() {}
}
