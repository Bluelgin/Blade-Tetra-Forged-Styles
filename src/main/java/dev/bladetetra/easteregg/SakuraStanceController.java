package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import dev.bladetetra.network.ModNetwork;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A post-commit observer, never a combo router or a hit/callback replacement. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SakuraStanceController {
    private record Pending(ItemStack blade, ResourceKey<Level> dimension, int slot, int action, long at) {}
    public static final String FRACTURE="blade_tetra_sakura_fracture";
    private static final String LAST_FRACTURE="blade_tetra_sakura_last_fracture";
    static final int FRACTURE_COOLDOWN=40;
    static final float FRACTURE_CHANCE=.10F;
    private record Reassembly(ItemStack blade,int slot,long at) {}
    private static final Map<UUID,Reassembly> REASSEMBLY=new HashMap<>();
    public static int fracture(ItemStack blade) {
        return blade.hasTag()?Math.max(0,Math.min(4,blade.getTag().getInt(FRACTURE))):0;
    }
    public static float fraction(ItemStack blade) { return dispersed(blade)?0:1-fracture(blade)/4F; }
    public static boolean reassembling(ServerPlayer p) {return REASSEMBLY.containsKey(p.getUUID());}
    static boolean canFracture(ServerPlayer p,ItemStack blade) {
        if(!supported(blade)||dispersed(blade)||reassembling(p)) return false;
        var tag=blade.getTag();
        if(tag==null||!tag.contains(LAST_FRACTURE,net.minecraft.nbt.Tag.TAG_LONG)) return true;
        long elapsed=p.level().getGameTime()-tag.getLong(LAST_FRACTURE);
        // Imported blades from a world with a later clock must not lock forever.
        return elapsed<0||elapsed>=FRACTURE_COOLDOWN;
    }
    public static void fractureHit(ServerPlayer p,ItemStack blade) {
        if(!canFracture(p,blade)) return;
        blade.getOrCreateTag().putLong(LAST_FRACTURE,p.level().getGameTime());
        int stage=Math.min(4,fracture(blade)+1);
        blade.getOrCreateTag().putInt(FRACTURE,stage);
        if(stage==4) {
            blade.getOrCreateTag().putBoolean(SakuraStanceRules.DISPERSED,true);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.blade_tetra.sakura.dispersed"),true);
        }
        p.serverLevel().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS,.35F,1.5F);
        p.containerMenu.broadcastChanges();
    }
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    public static boolean supported(ItemStack blade) {
        return AwakenedBladeCombat.replacesLegacy() && StyleResolver.resolve(blade) == BladeStyle.RENGEKI
                && SoulSkillState.enabled(blade, SoulLegacyState.Legacy.SENBONZAKURA);
    }
    public static boolean dispersed(ItemStack blade) {
        return blade.getTag() != null && blade.getTag().getBoolean(SakuraStanceRules.DISPERSED);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void motion(BladeMotionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        int action = SakuraStanceRules.action(e.getCombo());
        ItemStack blade = player.getMainHandItem();
        if (!supported(blade)) return;
        Pending previous = PENDING.get(player.getUUID());
        if (previous != null && previous.blade == blade && previous.action == SakuraStanceRules.DISPERSE
                && e.getCombo().toString().equals("slashblade:aerial_cleave_loop")
                && player.level().getGameTime() <= previous.at + 1) return;
        // BladeMotionEvent is posted BEFORE updateComboSeq commits and can still
        // be canceled/rewritten by another listener. Verify it at PlayerTick.END.
        PENDING.put(player.getUUID(), new Pending(blade, player.level().dimension(),
                player.getInventory().selected, action, player.level().getGameTime()));
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer player)) return;
        Reassembly rebuilding=REASSEMBLY.get(player.getUUID());
        if(rebuilding!=null) {
            if(!player.isAlive() || player.getMainHandItem()!=rebuilding.blade
                    ||player.getInventory().selected!=rebuilding.slot ||!supported(rebuilding.blade)
                    ||dispersed(rebuilding.blade)) REASSEMBLY.remove(player.getUUID());
            else if(player.level().getGameTime()>=rebuilding.at+14) {
                rebuilding.blade.getOrCreateTag().putInt(FRACTURE,0);
                REASSEMBLY.remove(player.getUUID());player.containerMenu.broadcastChanges();
            }
        }
        Pending p = PENDING.remove(player.getUUID());
        if (p == null || !player.isAlive() || player.getMainHandItem() != p.blade
                || player.getInventory().selected != p.slot || !player.level().dimension().equals(p.dimension)
                || player.level().getGameTime() - p.at > 2 || !supported(p.blade)) return;
        var state = p.blade.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if (state == null) return;
        int action = SakuraStanceRules.action(state.getComboSeq());
        if (action == 0 && p.action == SakuraStanceRules.DISPERSE) action = p.action;
        if (!SakuraStanceRules.changes(action, dispersed(p.blade))
                || !SakuraStanceRules.committed(action, state.getComboSeq(), p.at, state.getLastActionTime())) return;
        p.blade.getOrCreateTag().putBoolean(SakuraStanceRules.DISPERSED, action == SakuraStanceRules.DISPERSE);
        if(action==SakuraStanceRules.CONDENSE)
            REASSEMBLY.put(player.getUUID(),new Reassembly(p.blade,p.slot,player.level().getGameTime()));
        else REASSEMBLY.remove(player.getUUID());
        var tag = p.blade.getTag();
        String id = tag.getString("id");
        if (!id.isBlank()) {
            var at = player.getEyePosition();
            var packet = new AwakenedBladeVfxPacket(3, action + 4, at.x, at.y, at.z, at.x, at.y, at.z, 0,
                    new AwakenedBladeVfxPacket.SakuraCue(player.getUUID(), id, p.slot,
                            p.dimension.location(), action, SakuraCombatVisuals.nextSequence(), p.at, -1));
            if (packet.valid()) ModNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
        }
        // Send visual authorization before equipment/container synchronization;
        // clients can retain the old geometry/pool through the NBT change.
        player.containerMenu.broadcastChanges();
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { PENDING.remove(e.getEntity().getUUID()); REASSEMBLY.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { PENDING.remove(e.getEntity().getUUID()); REASSEMBLY.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { PENDING.remove(e.getEntity().getUUID()); REASSEMBLY.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { PENDING.remove(e.getEntity().getUUID()); REASSEMBLY.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { PENDING.clear(); REASSEMBLY.clear(); }
    private SakuraStanceController() {}
}
