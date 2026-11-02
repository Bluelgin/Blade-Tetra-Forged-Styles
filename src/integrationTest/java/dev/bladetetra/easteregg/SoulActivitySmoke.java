package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import static dev.bladetetra.easteregg.SoulLegacyState.Legacy.SENBONZAKURA;

/** Extends the existing isolated combat smoke with authoritative HUD cue admission. */
final class SoulActivitySmoke {
    static void verify(ServerLevel level, ServerPlayer actor) {
        ItemStack previous = actor.getMainHandItem();
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        var tag = blade.getOrCreateTag();
        tag.putString(ModularSlashBladeItem.BLADE_SLOT, ModularSlashBladeItem.WAKIZASHI_MODULE);
        tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT, ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
        tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE + "_material", "awakened_soul_inscription/sakura_crystal");
        tag.putBoolean(SenbonzakuraAwakening.TAG_UNLOCKED, true);
        var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setBroken(false); state.setSealed(false);
        actor.setItemSlot(EquipmentSlot.MAINHAND, blade);
        var spawn = level.getSharedSpawnPos();
        double x = spawn.getX() + .5, y = level.getMaxBuildHeight() - 40, z = spawn.getZ() + .5;
        actor.moveTo(x, y, z, 0, 0);
        Zombie target = new Zombie(level); target.setNoAi(true); target.setNoGravity(true);
        target.moveTo(x, y, z + 20, 0, 0);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000); level.addFreshEntity(target);
        try {
            check(SoulActivityService.collect(actor) == null, "equipping an awakened blade creates no idle HUD cue");
            state.updateComboSeq(actor, ComboStateRegistry.COMBO_B1.getId());
            check(SoulActivityService.collect(actor) == null, "ordinary committed miss creates no HUD cue");
            SakuraStanceController.fractureHit(actor, blade);
            var fracture = SoulActivityService.collect(actor);
            check(fracture != null && fracture.triggered() == SoulActivityService.bit(SENBONZAKURA)
                    && fracture.sustained() == 0, "accepted fracture lights only Sakura");
            check(fracture.blade().equals(tag.getUUID(SoulSkillState.BLADE_ID)) && fracture.slot() == actor.getInventory().selected,
                    "single-soul cue carries server-assigned weapon identity");
            check(SoulActivityService.collect(actor) == null, "no idle heartbeat after a transient skill");
            SakuraStanceController.fractureHit(actor, blade);
            check(SoulActivityService.collect(actor) == null, "cooldown-denied fracture cannot refresh HUD");
            tag.putBoolean(SakuraStanceRules.DISPERSED, true);
            check(SoulActivityService.collect(actor) == null, "static dispersed back shards do not keep HUD visible");
            target.moveTo(x, y, z + 2, 0, 0);
            state.updateComboSeq(actor, ComboStateRegistry.COMBO_B2.getId()); target.invulnerableTime = 0;
            AttackManager.doMeleeAttack(actor, target, true, false, 1F);
            var concerto = SoulActivityService.collect(actor);
            check(concerto != null && concerto.triggered() == SoulActivityService.bit(SENBONZAKURA)
                    && concerto.sustained() == SoulActivityService.bit(SENBONZAKURA), "real native hit and accepted shard queue reveal sustained Sakura");
            check(SoulActivityService.collect(actor) == null, "unchanged sustained state sends no heartbeat");
            SakuraShardAttackService.cancel(actor);
            var ended = SoulActivityService.collect(actor);
            check(ended != null && ended.triggered() == 0 && ended.sustained() == 0, "ending queued work sends an explicit clear for client fade");
            SoulActivityService.triggered(actor, blade, SENBONZAKURA);
            actor.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            check(SoulActivityService.collect(actor) == null, "unequipping discards undelivered activity");
            actor.setItemSlot(EquipmentSlot.MAINHAND, blade);
            check(SoulActivityService.collect(actor) == null, "re-equipping cannot replay stale activity");
            LogUtils.getLogger().info("SOUL_ACTIVITY_SMOKE_PASS: real native miss/hit, accepted fracture, denied cooldown, owner weapon binding, static shards idle, pending work and explicit completion, no heartbeat, no stale re-equip cue");
        } finally {
            SakuraShardAttackService.cancel(actor); target.discard(); actor.setItemSlot(EquipmentSlot.MAINHAND, previous);
            SoulActivityService.collect(actor);
        }
    }
    private static void check(boolean okay, String message) { if (!okay) throw new IllegalStateException(message); }
    private SoulActivitySmoke() {}
}
