package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
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
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Native action transactions and one bounded server session per active owner. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class RaikiriStepController {
    static final String BLADE_ID="blade_tetra_awakened_v2_id";
    static final String SEQUENCE="blade_tetra_raikiri_step_sequence";
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    static final class Session {
        final UUID owner, blade, nonce=UUID.randomUUID();
        final ResourceKey<Level> dimension;
        final int slot;
        final ItemStack stack;
        int chain;
        long started, nextStep, cleaveStarted;
        boolean confirmed, cleaving, observedAir;
        UUID sequence;
        final java.util.List<HitProof> proofs=new java.util.ArrayList<>();
        Session(ServerPlayer p, ItemStack stack) {
            owner=p.getUUID(); blade=id(stack); dimension=p.level().dimension();
            slot=p.getInventory().selected; this.stack=stack;
        }
        boolean valid(ServerPlayer p) {
            return p != null && p.isAlive() && !p.isSpectator()
                    && AwakenedBladeCombat.replacesLegacy() && p.level().dimension().equals(dimension)
                    && p.getInventory().selected==slot && p.getMainHandItem()==stack
                    && blade.equals(id(stack)) && eligible(stack);
        }
    }
    private record HitProof(LivingDamageEvent event, float before, UUID sequence) {}
    private static void settle(Session s) {
        for (HitProof proof:s.proofs) {
            LivingDamageEvent e=proof.event;
            if (!e.isCanceled() && Float.isFinite(e.getAmount()) && e.getAmount()>0
                    && e.getEntity().getHealth()<proof.before
                    && RaikiriRules.confirmed(e.getEntity().level().getGameTime(),s.started,s.sequence,proof.sequence))
                s.confirmed=true;
        }
        s.proofs.clear();
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
    /** No movement, payment, sequence or queued damage during NextComboEvent. */
    public static boolean canRoute(ServerPlayer p, ItemStack blade) {
        if (blade!=p.getMainHandItem() || !eligible(blade) || !p.onGround()) return false;
        Session s=SESSIONS.get(p.getUUID());
        if (s!=null && !s.valid(p)) s=null;
        if(s!=null) settle(s);
        long now=p.level().getGameTime();
        if (s!=null && now<s.nextStep || !RaikiriFieldService.hasTrailCapacity(p.getUUID())) return false;
        if (safeEnd(p).distanceToSqr(p.position())<0.24D) return false;
        if (s!=null && RaikiriRules.followup(s.chain,s.confirmed,s.started,now)) return true;
        if (!dev.bladetetra.config.GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()) return true;
        return p.getCapability(CapabilityConcentrationRank.RANK_POINT).map(rank ->
                AwakenedRankCost.canPay(rank.getRankPoint(now),rank.getUnitCapacity(),rank.getRank(now).level,
                        AwakenedRankCost.requiredLevel(blade,AwakenedRankCost.Cast.RAIKIRI_STEP),
                        AwakenedRankCost.cost(blade,AwakenedRankCost.Cast.RAIKIRI_STEP))).orElse(false);
    }
    /** Called by the registered node's clickAction AFTER updateComboSeq commits. */
    static void committed(ServerPlayer p) {
        ItemStack blade=p.getMainHandItem();
        ResourceLocation seq=blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(b->b.getComboSeq()).orElse(null);
        if (!RaikiriRapidCombo.isThunderCombo(seq) || !RaikiriRapidCombo.directionalIntent(p)
                || !canRoute(p,blade)) return;
        Session s=session(p);
        if (s==null) return;
        long now=p.level().getGameTime();
        Vec3 from=p.position(), end=safeEnd(p);
        boolean free=RaikiriRules.followup(s.chain,s.confirmed,s.started,now);
        if (!free && !AwakenedRankCost.trySpend(p,blade,AwakenedRankCost.Cast.RAIKIRI_STEP)) return;
        s.chain=free?s.chain+1:1; s.confirmed=false; s.started=now;
        s.nextStep=now+(s.chain>1?6:10); s.sequence=UUID.randomUUID(); s.proofs.clear();
        p.connection.teleport(end.x,end.y,end.z,p.getYRot(),p.getXRot());
        // Capture the actual server endpoint after teleport, never a predicted target position.
        RaikiriFieldService.step(p,s,from,p.position());
    }
    static Vec3 safeEnd(ServerPlayer p) {
        Vec3 look=p.getLookAngle().multiply(1,0,1);
        Vec3 from=p.position(), moved=from;
        if (look.lengthSqr()<0.0001D) return from;
        Vec3 direction=look.normalize();
        // 0.125 spacing plus swept AABB prevents thin shapes between samples.
        for (int i=1;i<=28;i++) {
            Vec3 next=from.add(direction.scale(i*0.125D));
            AABB box=p.getBoundingBox().move(next.subtract(from));
            AABB sweep=p.getBoundingBox().move(moved.subtract(from)).minmax(box);
            ServerLevel level=p.serverLevel();
            if (!loaded(level,sweep) || !level.getWorldBorder().isWithinBounds(box)
                    || !level.noCollision(p,sweep)) break;
            moved=next;
        }
        return moved;
    }
    static boolean loaded(ServerLevel level,AABB box) {
        if (box.minY<level.getMinBuildHeight() || box.maxY>=level.getMaxBuildHeight()) return false;
        for (int x=BlockPos.containing(box.minX,0,0).getX()>>4;x<=BlockPos.containing(box.maxX,0,0).getX()>>4;x++)
            for (int z=BlockPos.containing(0,0,box.minZ).getZ()>>4;z<=BlockPos.containing(0,0,box.maxZ).getZ()>>4;z++)
                if (!level.hasChunk(x,z)) return false;
        return true;
    }
    static void tag(EntitySlashEffect slash,ServerPlayer p) {
        Session s=SESSIONS.get(p.getUUID());
        if (slash!=null && s!=null && s.valid(p) && s.sequence!=null
                && p.level().getGameTime()-s.started<=7) {
            slash.getPersistentData().putUUID(SEQUENCE,s.sequence);
            slash.getPersistentData().putUUID(BLADE_ID,s.blade);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void hit(LivingDamageEvent e) {
        if (!Float.isFinite(e.getAmount()) || e.getAmount()<=0
                || SoulLegacyDamageGuard.isSecondary(e.getSource())
                || !(e.getSource().getEntity() instanceof ServerPlayer p)
                || !(e.getSource().getDirectEntity() instanceof EntitySlashEffect slash)
                || slash.getShooter()!=p || !AwakenedBladeCombat.canAffect(p,e.getEntity())) return;
        Session s=SESSIONS.get(p.getUUID()); var tag=slash.getPersistentData();
        if (s!=null && s.valid(p) && tag.hasUUID(SEQUENCE) && tag.hasUUID(BLADE_ID)
                && s.proofs.size()<16 && s.blade.equals(tag.getUUID(BLADE_ID))
                && RaikiriRules.confirmed(p.level().getGameTime(),s.started,s.sequence,tag.getUUID(SEQUENCE)))
            s.proofs.add(new HitProof(e,e.getEntity().getHealth(),tag.getUUID(SEQUENCE)));
    }
    public static boolean free(ServerPlayer p) {
        Session s=SESSIONS.get(p.getUUID());
        if(s!=null) settle(s);
        return s!=null && s.valid(p) && RaikiriRules.followup(s.chain,s.confirmed,s.started,p.level().getGameTime());
    }
    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (!(e.player instanceof ServerPlayer p)) return;
        Session s=session(p); if (s==null) return;
        settle(s);
        ResourceLocation combo=p.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(b->b.resolvCurrentComboState(p)).orElse(null);
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
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase!=TickEvent.Phase.END) return;
        MinecraftServer server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server==null) return;
        for (UUID owner:java.util.List.copyOf(SESSIONS.keySet()))
            if (!SESSIONS.get(owner).valid(owner(server,SESSIONS.get(owner)))) clear(owner,server);
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
    private RaikiriStepController() {}
}
