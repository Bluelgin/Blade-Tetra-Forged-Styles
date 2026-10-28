package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Passive observation only. Never routes, writes or executes a native action. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class RaikiriActionObserver {
    static final String BLADE_ID="blade_tetra_awakened_v2_id";
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    static final class Session {
        final UUID owner,blade,nonce=UUID.randomUUID();
        final ResourceKey<Level> dimension;
        final int slot;
        final ItemStack stack;
        long cleaveStarted;
        boolean cleaving,observedAir;
        Pending pending;
        Run rapid;
        Vec3 observedPosition;
        long observedTick;
        Session(ServerPlayer p,ItemStack stack) {
            owner=p.getUUID();blade=id(stack);dimension=p.level().dimension();
            slot=p.getInventory().selected;this.stack=stack;
            observedPosition=p.position();observedTick=p.level().getGameTime();
        }
        boolean valid(ServerPlayer p) {
            return p!=null && p.isAlive() && !p.isSpectator() && eligible(stack)
                    && p.level().dimension().equals(dimension) && p.getInventory().selected==slot
                    && p.getMainHandItem()==stack && blade.equals(id(stack));
        }
    }
    private record Pending(BladeMotionEvent event,long tick,Vec3 position) {}
    private static final class Run {
        final UUID id=UUID.randomUUID();
        final long stamp;
        Vec3 last;
        long lastTick;
        double length;
        int positions=1;
        Run(Pending p) { stamp=p.tick;last=p.position;lastTick=p.tick; }
    }
    static UUID id(ItemStack stack) {
        var tag=stack.getOrCreateTag();
        if (!tag.hasUUID(BLADE_ID)) tag.putUUID(BLADE_ID,UUID.randomUUID());
        return tag.getUUID(BLADE_ID);
    }
    static boolean eligible(ItemStack stack) {
        // active() preserves the baseline's primary-soul precedence. A Raikiri
        // secondary inscription alone never grants this controller.
        return AwakenedBladeCombat.replacesLegacy()
                && net.minecraftforge.fml.ModList.get().getModContainerById("slashblade")
                        .map(mod->"1.9.63".equals(mod.getModInfo().getVersion().toString())).orElse(false)
                && SoulLegacyState.inscribed(stack).stream().findFirst().orElse(SoulLegacyState.Legacy.NONE)
                        ==SoulLegacyState.Legacy.RAIKIRI
                && (SoulLegacyState.inscribed(stack).size()==1
                        || SoulResonance.isFormed(stack,SoulResonance.resolve(stack)))
                && SoulLegacyState.active(stack)==SoulLegacyState.Legacy.RAIKIRI
                && SoulLegacyState.isActive(stack,SoulLegacyState.Legacy.RAIKIRI);
    }
    static Session session(ServerPlayer p) {
        Session s=SESSIONS.get(p.getUUID());
        if (s!=null && !s.valid(p)) { clear(p.getUUID(),p.getServer()); s=null; }
        if (s==null && eligible(p.getMainHandItem()) && p.isAlive() && !p.isSpectator()
                && SESSIONS.size()<512) {
            s=new Session(p,p.getMainHandItem()); SESSIONS.put(p.getUUID(),s);
        }
        return s;
    }
    static boolean finite(Vec3 p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    static boolean withinBorder(ServerLevel level,AABB box) {
        var border=level.getWorldBorder();
        return box.minX>=border.getMinX() && box.maxX<=border.getMaxX()
                && box.minZ>=border.getMinZ() && box.maxZ<=border.getMaxZ();
    }
    static boolean loaded(ServerLevel level,AABB box) {
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.maxX)
                || !Double.isFinite(box.minY) || !Double.isFinite(box.maxY)
                || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ)) return false;
        if (box.minY<level.getMinBuildHeight() || box.maxY>=level.getMaxBuildHeight()) return false;
        for (int x=BlockPos.containing(box.minX,0,0).getX()>>4;x<=BlockPos.containing(box.maxX,0,0).getX()>>4;x++)
            for (int z=BlockPos.containing(0,0,box.minZ).getZ()>>4;z<=BlockPos.containing(0,0,box.maxZ).getZ()>>4;z++)
                if (!level.hasChunk(x,z)) return false;
        return true;
    }
    /** BladeMotionEvent is before the native state write. This is only a candidate. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void motion(BladeMotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)
                || !ComboStateRegistry.RAPID_SLASH.getId().equals(event.getCombo())
                || !eligible(p.getMainHandItem())) return;
        Session s=session(p);
        if(s!=null) s.pending=new Pending(event,p.level().getGameTime(),p.position());
    }
    private static void committed(ServerPlayer p,Session s) {
        Pending pending=s.pending;if(pending==null) return;
        long now=p.level().getGameTime();
        var state=p.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE).orElse(null);
        if(pending.event.isCanceled() || !ComboStateRegistry.RAPID_SLASH.getId().equals(pending.event.getCombo())
                || now-pending.tick>1 || now<pending.tick) { s.pending=null;return; }
        if(state==null || !ComboStateRegistry.RAPID_SLASH.getId().equals(activeCombo(p,state))
                || state.getLastActionTime()!=pending.tick) return;
        if(s.rapid==null || s.rapid.stamp!=pending.tick) {
            finish(s);s.rapid=new Run(pending);
        }
        s.pending=null;
    }
    /** Read raw state and public timing only. resolvCurrentComboState can WRITE native state. */
    static ResourceLocation activeCombo(ServerPlayer p,ISlashBladeState state) {
        ResourceLocation raw=state.getComboSeq();
        var node=ComboStateRegistry.REGISTRY.get().getValue(raw);
        long age=p.level().getGameTime()-state.getLastActionTime();
        return node!=null && age>=0 && age<=40 && age*50L<=node.getTimeoutMS()
                ? raw : ComboStateRegistry.NONE.getId();
    }
    static void observe(ServerPlayer p,Session s) {
        long now=p.level().getGameTime();
        double maximum=RaikiriConfig.MAX_SEGMENT.get();
        boolean continuous=now==s.observedTick
                ? finite(p.position()) && p.position().distanceToSqr(s.observedPosition)<=maximum*maximum
                : RaikiriRules.continuous(point(s.observedPosition),point(p.position()),s.observedTick,now,maximum);
        if(!continuous) {
            clear(p.getUUID(),p.getServer());return;
        }
        s.observedPosition=p.position();s.observedTick=now;
        committed(p,s);Run r=s.rapid;if(r==null) return;
        var state=p.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE).orElse(null);
        if(state==null || state.getLastActionTime()!=r.stamp
                || !ComboStateRegistry.RAPID_SLASH.getId().equals(activeCombo(p,state))) { finish(s);return; }
        Vec3 at=p.position();
        if(now==r.lastTick && (r.positions>1 || at.distanceToSqr(r.last)<0.000001D)) return;
        double distance=at.distanceTo(r.last);
        if(!RaikiriRules.continuous(point(r.last),point(at),now==r.lastTick?now-1:r.lastTick,now,
                RaikiriConfig.MAX_SEGMENT.get()) || !legalSegment(p,r.last,at)) {
            clear(p.getUUID(),p.getServer());return;
        }
        if(now-r.stamp>RaikiriConfig.MAX_OBSERVE_TICKS.get()
                || r.positions>=32 || r.length+distance>RaikiriConfig.MAX_PATH.get()) { finish(s);return; }
        r.length+=distance;
        if(distance>0.001D) {
            r.positions++;
            RaikiriFieldService.segment(p,s,r.id,r.last,at,r.lastTick,now);
        }
        r.last=at;r.lastTick=now;
    }
    private static RaikiriRules.Point point(Vec3 p) { return new RaikiriRules.Point(p.x,p.y,p.z); }
    private static boolean legalSegment(ServerPlayer p,Vec3 from,Vec3 to) {
        AABB end=p.getBoundingBox(),start=end.move(from.subtract(to));
        return loaded(p.serverLevel(),start.minmax(end)) && withinBorder(p.serverLevel(),start.minmax(end))
                && p.serverLevel().noCollision(p,start.minmax(end));
    }
    private static void finish(Session s) {
        if(s.rapid!=null) RaikiriFieldService.finish(s.rapid.id);
        s.rapid=null;
    }
    @SubscribeEvent public static void teleport(EntityTeleportEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) clear(p.getUUID(),p.getServer());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void interrupted(LivingHurtEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) {
            Session s=SESSIONS.get(p.getUUID());if(s!=null) { finish(s);s.pending=null; }
        }
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Session s=session(p);if(s==null) return;
        ResourceLocation combo=p.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(b->activeCombo(p,b)).orElse(null);
        boolean air=ComboStateRegistry.AERIAL_CLEAVE.getId().equals(combo)
                || ComboStateRegistry.AERIAL_CLEAVE_LOOP.getId().equals(combo);
        long now=p.level().getGameTime();
        if (air && !p.onGround() && !s.observedAir) { s.cleaving=true; s.cleaveStarted=now; }
        s.observedAir=air;
        if (s.cleaving && p.onGround()) {
            if ((air || ComboStateRegistry.AERIAL_CLEAVE_LANDING.getId().equals(combo))
                    && RaikiriRules.landing(true,true,RaikiriFieldService.support(p)!=null,now-s.cleaveStarted))
                RaikiriFieldService.plant(p,s);
            s.cleaving=false;
        } else if (!air && !ComboStateRegistry.AERIAL_CLEAVE_LANDING.getId().equals(combo)
                || now-s.cleaveStarted>100) s.cleaving=false;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        MinecraftServer server=e.getServer();
        for(UUID owner:List.copyOf(SESSIONS.keySet())) {
            Session s=SESSIONS.get(owner);ServerPlayer p=owner(server,s);
            if(!s.valid(p)) clear(owner,server);else observe(p,s);
        }
        RaikiriFieldService.tick(server);
    }
    static ServerPlayer owner(MinecraftServer server,Session s) {
        ServerLevel level=server.getLevel(s.dimension);
        return level!=null && level.getPlayerByUUID(s.owner) instanceof ServerPlayer p ? p : null;
    }
    static void clear(UUID owner,MinecraftServer server) {
        SESSIONS.remove(owner); RaikiriFieldService.clear(owner,server);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { clear(e.getEntity().getUUID(),e.getEntity().getServer()); RaikiriFieldService.forgetViewer(e.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { clear(e.getEntity().getUUID(),e.getEntity().getServer()); RaikiriFieldService.forgetViewer(e.getEntity().getUUID()); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if(e.getEntity() instanceof ServerPlayer p) clear(p.getUUID(),p.getServer()); }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) {
        if(e.getLevel() instanceof ServerLevel l)
            for(UUID owner:java.util.List.copyOf(SESSIONS.keySet()))
                if(SESSIONS.get(owner).dimension.equals(l.dimension())) clear(owner,l.getServer());
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { SESSIONS.clear(); RaikiriFieldService.reset(); }
    private RaikiriActionObserver() {}
}
