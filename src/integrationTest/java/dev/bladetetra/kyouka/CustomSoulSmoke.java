package dev.bladetetra.kyouka;

import com.mojang.logging.LogUtils;
import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.easteregg.*;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.SoulSwitchPacket;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
import static dev.bladetetra.easteregg.SoulLegacyState.Legacy.*;

/** Uses the existing isolated combat smoke: real native commits, damage and delayed effects. */
public final class CustomSoulSmoke {
    private final ServerPlayer actor;
    private final List<EntitySlashEffect> slashes = new ArrayList<>();
    private int checks;
    private CustomSoulSmoke(ServerPlayer actor) { this.actor = actor; }
    @SubscribeEvent public void join(EntityJoinLevelEvent e) {
        if (e.getEntity() instanceof EntitySlashEffect slash && slash.getOwner() == actor) slashes.add(slash);
    }
    public static void verify(ServerLevel level, ServerPlayer actor) {
        var probe = new CustomSoulSmoke(actor);
        ItemStack previous = actor.getMainHandItem();
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        var tag = blade.getOrCreateTag();
        tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT, ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
        tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE + "_material", "awakened_soul_inscription/kyouka_senbonzakura");
        tag.putString(ModularSlashBladeItem.BLADE_SLOT, ModularSlashBladeItem.CUSTOM_STANDARD_BLADE_MODULE);
        tag.putBoolean(KyoukaAwakening.TAG_UNLOCKED, true);
        tag.putBoolean(SenbonzakuraAwakening.TAG_UNLOCKED, true);
        var mixed = new CustomStyleSpec("Mixed soul smoke", List.of(ModComboStates.IAIDO_FOLLOW.getId(), ComboStateRegistry.COMBO_B1.getId()));
        tag.put(CustomStyleSpec.STACK_KEY, mixed.encode());
        var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setBroken(false); state.setSealed(false);
        actor.setItemSlot(EquipmentSlot.MAINHAND, blade);
        // Native mirror validation requires a tracked server entity, so use the loaded spawn region.
        var spawn = level.getSharedSpawnPos();
        double x = spawn.getX() + .5, y = level.getMaxBuildHeight() - 40, z = spawn.getZ() + .5;
        level.getChunk(net.minecraft.core.BlockPos.containing(x, y, z));
        Zombie target = new Zombie(level);
        actor.moveTo(x, y, z, 0, 0); actor.setOnGround(true);
        target.moveTo(x, y, z + 2, 0, 0); target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0); target.setHealth(1000); level.addFreshEntity(target);
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            for (var selected : List.of(KYOUKA, SENBONZAKURA)) {
                tag.putInt(SoulSkillState.SELECTED, selected.ordinal());
                probe.check(KyoukaMirrorSessions.eligible(blade) && SakuraStanceController.supported(blade), "both controllers survive either active selection");
                probe.check(SoulSkillState.enabled(blade, selected) && !SoulSkillState.enabled(blade, selected == KYOUKA ? SENBONZAKURA : KYOUKA), "only one active art selected");
            }
            tag.put(CustomStyleSpec.STACK_KEY, new CustomStyleSpec("Shared", List.of(ComboStateRegistry.RAPID_SLASH.getId())).encode());
            probe.check(!KyoukaMirrorSessions.eligible(blade) && !SakuraStanceController.supported(blade), "shared rush does not grant either family");
            tag.put(CustomStyleSpec.STACK_KEY, mixed.encode());
            tag.putInt(SoulSkillState.SELECTED, KYOUKA.ordinal());
            tag.putInt(SakuraStanceController.FRACTURE, 2);
            advance(level, 40);
            state.updateComboSeq(actor, ModComboStates.IAIDO_FOLLOW.getId());
            var session = KyoukaMirrorSessions.STATES.get(actor.getUUID());
            probe.check(session != null && session.action != null, "installed Iaido action admitted");
            AttackManager.doMeleeAttack(actor, target, true, false, 1F);
            probe.check(session.last != null, "native Iaido hit records mirror snapshot");
            probe.check(!SakuraShardAttackService.switchBlocked(actor), "Iaido hit does not release Sakura shards");
            probe.check(SakuraStanceController.fracture(blade) == 2, "Iaido hit preserves fracture");
            // Armor impact must remain bound to its source while the next B action is committed.
            KyoukaMirrorSessions.activate(actor, Set.of());
            advance(level, 3); target.invulnerableTime = 0;
            state.updateComboSeq(actor, ModComboStates.IAIDO_FOLLOW.getId());
            AttackManager.doMeleeAttack(actor, target, true, false, 1F);
            probe.check(session.pending.size() == 1, "native water hit schedules impact");
            var pending = session.pending.get(0);
            tag.putBoolean(SakuraStanceRules.DISPERSED, true);
            advance(level, 1); target.invulnerableTime = 0;
            state.updateComboSeq(actor, ComboStateRegistry.COMBO_B1.getId());
            AttackManager.doMeleeAttack(actor, target, true, false, 1F);
            probe.check(session.action == null && session.pending.size() == 1, "B hit cannot double-proc mirror or cancel accepted impact");
            probe.check(SakuraShardAttackService.switchBlocked(actor), "B hit releases shards while Kyouka is selected");
            // Capture a B slash, then change to Iaido before that slash hits.
            AttackManager.doSlash(actor, 25, false, false, .5);
            var slash = probe.slashes.get(probe.slashes.size() - 1);
            var source = level.damageSources().indirectMagic(slash, actor);
            advance(level, 1);
            state.updateComboSeq(actor, ModComboStates.IAIDO_FOLLOW.getId());
            var rank = actor.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
            rank.setRawRankPoint(1000); rank.setLastUpdte(level.getGameTime());
            rank.addRankPoint(source);
            probe.check(rank.getRawRankPoint() == 1000, "late B slash cannot credit native Rank during Iaido");
            rank.addRankPoint(actor, 100);
            SakuraEndlessFlow.reconcile(actor);
            probe.check(rank.getRawRankPoint() == 1100, "dispersed shards do not erase other-family Rank gains");
            var hurt = new net.minecraftforge.event.entity.living.LivingHurtEvent(target, source, 10);
            SakuraCombatVisuals.scaleOrdinary(hurt);
            probe.check(Math.abs(hurt.getAmount() - 7) < .001, "late B slash retains dispersed reduction");
            AttackManager.doSlash(actor, 25, false, false, .5);
            var kyoukaSlash = probe.slashes.get(probe.slashes.size() - 1);
            var kyoukaSource = level.damageSources().indirectMagic(kyoukaSlash, actor);
            var kyoukaHurt = new net.minecraftforge.event.entity.living.LivingHurtEvent(target, kyoukaSource, 10);
            SakuraCombatVisuals.scaleOrdinary(kyoukaHurt);
            probe.check(kyoukaHurt.getAmount() == 10, "dispersed Sakura never scales an Iaido slash");
            long earnedBefore = rank.getRawRankPoint();
            rank.addRankPoint(kyoukaSource);
            SakuraEndlessFlow.reconcile(actor);
            probe.check(rank.getRawRankPoint() > earnedBefore, "Iaido slash keeps actual native Rank credit with shards dispersed");
            while (level.getGameTime() < pending.due()) {
                advance(level, 1);
                SakuraShardAttackService.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, actor));
            }
            float before = target.getHealth(); target.invulnerableTime = 0;
            KyoukaMirrorSessions.step();
            probe.check(target.getHealth() < before && session.pending.isEmpty(), "accepted water impact finishes after crossing families");
            SakuraShardAttackService.cancel(actor);
            KyoukaMirrorSessions.endArmor(session);
            state.updateComboSeq(actor, ComboStateRegistry.COMBO_B2.getId());
            KyoukaMirrorSessions.rankChanged(actor, 6, 7, kyoukaSource);
            probe.check(session.promotion == level.getGameTime(), "late Iaido promotion belongs to captured Kyouka slash during B2");
            session.promotion = -1;
            KyoukaMirrorSessions.rankChanged(actor, 6, 7, source);
            probe.check(session.promotion == -1, "Sakura source cannot promote a stale Kyouka snapshot");
            KyoukaMirrorSessions.activate(actor, Set.of());
            advance(level, 40);
            SoulSwitchService.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, actor));
            SoulSwitchService.request(actor, new SoulSwitchPacket(actor.getInventory().selected, tag.getUUID(SoulSkillState.BLADE_ID), KYOUKA.ordinal()));
            probe.check(SoulSkillState.selected(blade) == SENBONZAKURA && KyoukaMirrorSessions.armored(actor), "active selection preserves linked armor");
            var art = new SlashBladeEvent.PerformSlashArtEvent(actor, 40, state, ComboStateRegistry.SAKURA_END_LEFT.getId(), SlashArts.ArtsType.Success);
            tag.putInt(SoulSkillState.SELECTED, KYOUKA.ordinal());
            SakuraInfiniteDanceSA.art(art);
            probe.check(!SakuraDanceCombos.owns(art.getComboState()), "ordinary linkage cannot admit unselected Sakura art");
            blade = ItemStack.of(blade.save(new net.minecraft.nbt.CompoundTag()));
            probe.check(SoulSkillState.allowsStyle(blade, KYOUKA) && SoulSkillState.allowsStyle(blade, SENBONZAKURA), "existing custom spec survives saved blade round trip");
            LogUtils.getLogger().info("CUSTOM_SOUL_SMOKE_PASS: {} checks; actual native mixed actions, no double proc, persistent water impact, source-bound late slash damage/Rank, active art selection and saved custom spec", probe.checks);
        } finally {
            SakuraInfiniteDanceSA.end(actor); SakuraShardAttackService.cancel(actor);
            KyoukaMirrorSessions.releaseForSwitch(actor);
            probe.slashes.forEach(EntitySlashEffect::discard);
            MinecraftForge.EVENT_BUS.unregister(probe); target.discard(); actor.setItemSlot(EquipmentSlot.MAINHAND, previous);
        }
    }
    private static void advance(ServerLevel level, int ticks) {
        level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + ticks);
    }
    private void check(boolean okay, String message) {
        if (!okay) throw new IllegalStateException(message);
        checks++;
    }
}
