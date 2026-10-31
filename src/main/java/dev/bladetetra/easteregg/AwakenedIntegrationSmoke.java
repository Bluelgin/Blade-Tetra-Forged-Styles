package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.kyouka.WaterMirrorEffect;
import dev.bladetetra.network.AwakeningPresentationPacket;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Extends the existing opt-in Sakura smoke with cross-soul/save admission checks. */
final class AwakenedIntegrationSmoke {
    static void verify(ServerLevel level, ServerPlayer actor) {
        ItemStack previous = actor.getMainHandItem();
        Zombie target = new Zombie(level);
        target.setNoAi(true); target.setNoGravity(true);
        target.moveTo(actor.getX(), actor.getY(), actor.getZ() + 1, 0, 0);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.setHealth(1000); level.addFreshEntity(target);
        try {
            String[] materials = {"blood_crystal", "proudsoul_sphere", "sakura_crystal", "raikiri"};
            String[] unlocks = {AkatsukiAwakening.TAG_UNLOCKED, KyoukaAwakening.TAG_UNLOCKED,
                    SenbonzakuraAwakening.TAG_UNLOCKED, BladeLegacyEasterEggs.TAG_RAIKIRI_UNLOCKED};
            for (int i = 0; i < materials.length; i++) {
                ItemStack blade = blade(materials[i]);
                blade.getOrCreateTag().putBoolean(unlocks[i], true);
                var soul = SoulLegacyState.Legacy.values()[i + 1];
                blade = ItemStack.of(blade.save(new CompoundTag()));
                check(SoulLegacyState.active(blade) == soul && SoulLegacyState.isActive(blade, soul),
                        "old awakening save round trip: " + soul);
                actor.setItemSlot(EquipmentSlot.MAINHAND, blade);
                var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                        .orElseThrow(IllegalStateException::new);
                state.setBroken(false); state.setSealed(false);
                state.updateComboSeq(actor, ComboStateRegistry.COMBO_A1.getId());
                target.invulnerableTime = 0;
                float before = target.getHealth();
                AttackManager.doMeleeAttack(actor, target, true, false, 1F);
                check(target.getHealth() < before, "real native attack in combined runtime: " + soul);
                if (soul == SoulLegacyState.Legacy.AKATSUKI)
                    check(blade.getOrCreateTag().hasUUID("blade_tetra_akatsuki_execution_id"),
                            "legacy Final Moon damage observer admitted native hit");
                check(new AwakeningPresentationPacket(UUID.randomUUID(), actor.getUUID(),
                        blade.getOrCreateTag().getString("id"), level.dimension().location(),
                        level.getGameTime(), actor.getInventory().selected, soul.ordinal()).valid(),
                        "shared ritual packet accepts soul " + soul);
            }
            for (String pair : new String[]{"akatsuki_kyouka", "akatsuki_senbonzakura", "akatsuki_raikiri",
                    "kyouka_senbonzakura", "kyouka_raikiri", "senbonzakura_raikiri"}) {
                var saved = ItemStack.of(blade(pair).save(new CompoundTag()));
                check(SoulLegacyState.inscribed(saved).size() == 2, "dual inscription save retained: " + pair);
            }
            SoulSwitchSmoke.verify(level, actor);
            check(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .containsKey(new ResourceLocation("blade_tetra", "akatsuki_execution")),
                    "legacy Akatsuki damage type retained");
            check(WaterMirrorEffect.ARMOR.isPresent(), "Kyouka effect registered alongside other souls");
            LogUtils.getLogger().info("AWAKENED_INTEGRATION_SMOKE_PASS: four saved awakenings, real native hits, legacy Final Moon admission, four ritual packets, six dual inscription saves and damage/effect registration; not full execution/dual/client gameplay QA");
        } finally {
            actor.setItemSlot(EquipmentSlot.MAINHAND, previous); target.discard();
        }
    }
    private static ItemStack blade(String material) {
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        var tag = blade.getOrCreateTag();
        tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT, ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
        tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE + "_material", "awakened_soul_inscription/" + material);
        blade.getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(state -> { state.setBroken(false); state.setSealed(false); });
        return blade;
    }
    private static void check(boolean pass, String message) {
        if (!pass) throw new IllegalStateException(message);
    }
    private AwakenedIntegrationSmoke() { }
}
