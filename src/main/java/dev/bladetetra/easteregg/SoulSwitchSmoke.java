package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.kyouka.KyoukaMirrorSessions;
import dev.bladetetra.network.SoulSwitchPacket;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import java.util.UUID;

/** Extends the existing opt-in Forge smoke; never runs in ordinary worlds. */
final class SoulSwitchSmoke {
    static void verify(ServerLevel level, ServerPlayer actor) {
        ItemStack previous = actor.getMainHandItem();
        int checks = 0;
        try {
            for (String pair : new String[] {"akatsuki_kyouka", "akatsuki_senbonzakura", "akatsuki_raikiri",
                    "kyouka_senbonzakura", "kyouka_raikiri", "senbonzakura_raikiri"}) {
                advance(level, 40);
                var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
                var tag = blade.getOrCreateTag();
                tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT, ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
                tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE + "_material", "awakened_soul_inscription/" + pair);
                for (String key : new String[] {AkatsukiAwakening.TAG_UNLOCKED, KyoukaAwakening.TAG_UNLOCKED,
                        SenbonzakuraAwakening.TAG_UNLOCKED, BladeLegacyEasterEggs.TAG_RAIKIRI_UNLOCKED}) tag.putBoolean(key, true);
                blade.getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(s -> { s.setBroken(false); s.setSealed(false); });
                actor.setItemSlot(EquipmentSlot.MAINHAND, blade);
                SoulSwitchService.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, actor));
                check(tag.hasUUID(SoulSkillState.BLADE_ID), "server assigns held fusion identity"); checks++;
                var souls = SoulLegacyState.inscribed(blade).stream().toList();
                var first = souls.get(0); var second = souls.get(1);
                UUID id = tag.getUUID(SoulSkillState.BLADE_ID);
                int slot = actor.getInventory().selected;
                var nativeState = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
                var combo = nativeState.getComboSeq(); long action = nativeState.getLastActionTime();
                long rank = actor.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new).getRawRankPoint();
                SoulSwitchService.request(actor, new SoulSwitchPacket(slot, UUID.randomUUID(), first.ordinal()));
                check(SoulSkillState.selected(blade) == first, "wrong blade rejected"); checks++;
                SoulSwitchService.request(actor, new SoulSwitchPacket((slot + 1) % 9, id, first.ordinal()));
                check(SoulSkillState.selected(blade) == first, "wrong slot rejected"); checks++;
                SoulSwitchService.request(actor, new SoulSwitchPacket(slot, id, first.ordinal()));
                check(SoulSkillState.selected(blade) == second, "server switches every pair"); checks++;
                check(SoulLegacyState.active(blade) == first, "name/art primary retained"); checks++;
                check(combo.equals(nativeState.getComboSeq()) && action == nativeState.getLastActionTime(), "native combo unchanged"); checks++;
                check(rank == actor.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new).getRawRankPoint(), "switch never refills or charges Rank"); checks++;
                check(SoulSkillState.selected(ItemStack.of(blade.save(new CompoundTag()))) == second, "selection survives save round trip"); checks++;
                advance(level, 4);
                SoulSwitchService.request(actor, new SoulSwitchPacket(slot, id, second.ordinal()));
                check(SoulSkillState.selected(blade) == second, "cooldown cannot be bypassed"); checks++;
                advance(level, 20);
                SoulSwitchService.request(actor, new SoulSwitchPacket(slot, id, first.ordinal()));
                check(SoulSkillState.selected(blade) == second, "stale request cannot toggle a new selection"); checks++;
                SoulSwitchService.request(actor, new SoulSwitchPacket(slot, id, second.ordinal()));
                check(SoulSkillState.selected(blade) == first, "switches back after cooldown"); checks++;
                for (var selected : souls) for (String module : new String[] {ModularSlashBladeItem.BLADE_MODULE,
                        ModularSlashBladeItem.WAKIZASHI_MODULE, ModularSlashBladeItem.NODACHI_MODULE}) {
                    tag.putString(ModularSlashBladeItem.BLADE_SLOT, module);
                    tag.putInt(SoulSkillState.SELECTED, selected.ordinal());
                    var style = dev.bladetetra.combat.StyleResolver.resolve(blade);
                    for (var candidate : souls) {
                        check(SoulSkillState.enabled(blade, candidate)
                                == (candidate == selected && SoulSkillState.allowsStyle(candidate, style)), "only selected, compatible soul enabled"); checks++;
                    }
                    check(SakuraStanceController.supported(blade) == (selected == SoulLegacyState.Legacy.SENBONZAKURA && style == BladeStyle.RENGEKI), "Sakura server/client admission"); checks++;
                    check(KyoukaMirrorSessions.eligible(blade) == (selected == SoulLegacyState.Legacy.KYOUKA && style == BladeStyle.IAIDO), "Kyouka controller admission"); checks++;
                    check(RaikiriActionObserver.eligible(blade) == (selected == SoulLegacyState.Legacy.RAIKIRI), "Raikiri can be selected as secondary soul"); checks++;
                    check(!SoulResonance.isFormed(blade, SoulResonance.resolve(blade)), "retired automatic pair proc stays off"); checks++;
                }
                tag.putInt(SoulSkillState.SELECTED, second.ordinal());
                blade.getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(s -> s.setBroken(true));
                check(!SoulSkillState.enabled(blade, second), "broken blade cannot use selected skill"); checks++;
            }
            LogUtils.getLogger().info("SOUL_SWITCH_SMOKE_PASS: {} checks; six pairs, authoritative requests, cooldown, saved selection, style/controller admission, stable art and native Rank/combo", checks);
        } finally { actor.setItemSlot(EquipmentSlot.MAINHAND, previous); }
    }
    private static void advance(ServerLevel level, int ticks) {
        level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + ticks);
    }
    private static void check(boolean okay, String message) { if (!okay) throw new IllegalStateException(message); }
    private SoulSwitchSmoke() {}
}
