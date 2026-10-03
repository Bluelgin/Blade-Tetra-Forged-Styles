package dev.bladetetra.combat;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.CombatBalanceConfig;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in real Forge/Mixin verification in an isolated world; never runs on ordinary servers. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class CombatBalanceSmokeTest {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.combatBalanceSmoke")) return;
        var level = event.getServer().overworld();
        var player = FakePlayerFactory.getMinecraft(level);
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        try {
            // Native projectile owners are resolved through the level's entity registry.
            level.addNewPlayer(player);
            var config = net.minecraftforge.fml.config.ConfigTracker.INSTANCE.fileMap().get("blade-tetra-server.toml");
            // Arithmetic checks use an isolated spec backing, without racing autosave/watch reloads.
            var temporary = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
            temporary.putAll(config.getConfigData());
            dev.bladetetra.config.GameplayConfig.SPEC.setConfig(temporary);
            for (var value : values()) check(value.get() == 1, "neutral default");
            player.setItemSlot(EquipmentSlot.MAINHAND, blade);
            var target = new Zombie(level);
            var source = level.damageSources().playerAttack(player);
            var hit = new LivingHurtEvent(target, source, 10);
            CombatBalanceRuntime.damage(hit);
            check(hit.getAmount() == 10, "default damage unchanged");
            CombatBalanceConfig.GLOBAL.set(.5);
            for (BladeStyle style : BladeStyle.values()) {
                blade.getOrCreateTag().putString(dev.bladetetra.item.ModularSlashBladeItem.BLADE_SLOT,
                        switch (style) {
                            case STANDARD -> dev.bladetetra.item.ModularSlashBladeItem.ORTHODOX_BLADE_MODULE;
                            case IAIDO -> dev.bladetetra.item.ModularSlashBladeItem.BLADE_MODULE;
                            case DANGAKU -> dev.bladetetra.item.ModularSlashBladeItem.NODACHI_MODULE;
                            case RENGEKI -> dev.bladetetra.item.ModularSlashBladeItem.WAKIZASHI_MODULE;
                        });
                hit = new LivingHurtEvent(target, source, 10);
                CombatBalanceRuntime.damage(hit);
                check(hit.getAmount() == 5, "all four styles scaled once: " + style);
            }
            CombatBalanceConfig.RENGEKI.set(.8);
            blade.getOrCreateTag().putString(dev.bladetetra.item.ModularSlashBladeItem.BLADE_SLOT,
                    dev.bladetetra.item.ModularSlashBladeItem.WAKIZASHI_MODULE);
            CombatBalanceConfig.SLASH_ART.set(.6);
            CombatBalanceConfig.SUMMONED_SWORD.set(.2);
            var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            // Calling the transformed native selector checks the actual Super-SA mixin.
            var selected = SlashArtsRegistry.JUDGEMENT_CUT.get().doArts(SlashArts.ArtsType.Super, player);
            state.setComboSeq(selected);
            check(CombatBalanceRuntime.kind(player, null) == CombatBalanceRules.AttackKind.SLASH_ART,
                    "native Super-SA selection is tracked");
            hit = new LivingHurtEvent(target, source, 10);
            CombatBalanceRuntime.damage(hit);
            check(close(hit.getAmount(), 2.4F), "global x style x SA exactly once: amount=" + hit.getAmount()
                    + " style=" + StyleResolver.resolve(blade) + " global=" + CombatBalanceConfig.GLOBAL.get()
                    + " styleScale=" + CombatBalanceConfig.RENGEKI.get() + " sa=" + CombatBalanceConfig.SLASH_ART.get());
            var sword = new EntityAbstractSummonedSword(mods.flammpfeil.slashblade.SlashBlade.RegistryEvents.SummonedSword, level);
            sword.setShooter(player);
            check(sword.getShooter() == player, "registered native projectile owner");
            CombatBalanceRuntime.projectile(new EntityJoinLevelEvent(sword, level));
            // Switch weapons and clear the animation before the shot hits.
            CombatBalanceRuntime.ordinaryCombo(player);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            var shotSource = new net.minecraft.world.damagesource.DamageSource(source.typeHolder(), sword, player);
            hit = new LivingHurtEvent(target, shotSource, 10);
            CombatBalanceRuntime.damage(hit);
            check(close(hit.getAmount(), 2.4F), "SA projectile retains origin after weapon swap; no sword multiplier");
            CombatBalanceConfig.GLOBAL.set(.25);
            hit = new LivingHurtEvent(target, shotSource, 10);
            CombatBalanceRuntime.damage(hit);
            check(close(hit.getAmount(), 1.2F), "config edits affect next delayed hit without restart");
            InheritedCombatDamage.mark(sword);
            hit = new LivingHurtEvent(target, shotSource, 6);
            CombatBalanceRuntime.damage(hit);
            check(hit.getAmount() == 6, "inherited projectile budget unchanged");
            hit = new LivingHurtEvent(target, source, 10);
            CombatBalanceRuntime.damage(hit);
            check(hit.getAmount() == 10, "other held weapons unchanged");
            player.setItemSlot(EquipmentSlot.MAINHAND, blade);
            var ordinarySword = new EntityAbstractSummonedSword(mods.flammpfeil.slashblade.SlashBlade.RegistryEvents.SummonedSword, level);
            ordinarySword.setShooter(player);
            CombatBalanceRuntime.projectile(new EntityJoinLevelEvent(ordinarySword, level));
            var ordinarySource = new net.minecraft.world.damagesource.DamageSource(source.typeHolder(), ordinarySword, player);
            hit = new LivingHurtEvent(target, ordinarySource, 10);
            CombatBalanceRuntime.damage(hit);
            check(close(hit.getAmount(), .4F), "ordinary summoned sword uses sword multiplier, not SA multiplier");
            var bossSource = level.damageSources().mobAttack(new Zombie(level));
            hit = new LivingHurtEvent(target, bossSource, 10);
            CombatBalanceRuntime.damage(hit);
            check(hit.getAmount() == 10, "non-player attacks unchanged");
            CombatBalanceConfig.GLOBAL.set(0D);
            hit = new LivingHurtEvent(target, source, 10);
            CombatBalanceRuntime.damage(hit);
            check(hit.getAmount() == 0, "zero multiplier disables supported damage");
            CombatBalanceConfig.GLOBAL.set(.25D);
            var unmarked = new EntityAbstractSummonedSword(mods.flammpfeil.slashblade.SlashBlade.RegistryEvents.SummonedSword, level);
            var unmarkedSource = new net.minecraft.world.damagesource.DamageSource(source.typeHolder(), unmarked, player);
            hit = new LivingHurtEvent(target, unmarkedSource, 10);
            CombatBalanceRuntime.damage(hit);
            check(hit.getAmount() == 10, "unmarked/foreign projectile not captured from current weapon");
            var naturalLightning = new LivingHurtEvent(target, level.damageSources().lightningBolt(), 10);
            CombatBalanceRuntime.damage(naturalLightning);
            check(naturalLightning.getAmount() == 10, "natural lightning unchanged");
            var inheritedHit = new LivingHurtEvent(target, source, 6);
            InheritedCombatDamage.apply(() -> { CombatBalanceRuntime.damage(inheritedHit); return true; });
            check(inheritedHit.getAmount() == 6, "inherited direct budget unchanged");
            try { InheritedCombatDamage.apply(() -> { throw new IllegalStateException("test"); }); }
            catch (IllegalStateException expected) { }
            check(!InheritedCombatDamage.isInherited(source), "exception-safe inherited context cleanup");
            // Verify the real damage pipeline, not just direct handler calls.
            CombatBalanceConfig.GLOBAL.set(.5);
            CombatBalanceConfig.RENGEKI.set(1D);
            Zombie realTarget = new Zombie(level);
            float before = realTarget.getHealth();
            check(realTarget.hurt(source, 10), "real player hit accepted");
            check(realTarget.getHealth() < before && realTarget.getHealth() > before - 6,
                    "real Forge hit pipeline uses reduced damage");
            if (net.minecraftforge.fml.ModList.get().isLoaded("configured")) {
                verifyConfigured();
            }
            dev.bladetetra.config.GameplayConfig.SPEC.setConfig(config.getConfigData());
            verifyFileReload(player, target, source);
            LogUtils.getLogger().info("COMBAT_BALANCE_SMOKE_PASS: all styles, SA, delayed projectiles, inheritance, live edits, real hit");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("COMBAT_BALANCE_SMOKE_FAIL", failure);
            throw new IllegalStateException("Combat balance diagnostic failed", failure);
        } finally {
            var config = net.minecraftforge.fml.config.ConfigTracker.INSTANCE.fileMap().get("blade-tetra-server.toml");
            dev.bladetetra.config.GameplayConfig.SPEC.setConfig(config.getConfigData());
            for (var value : values()) value.set(1D);
            CombatBalanceRuntime.ordinaryCombo(player);
            event.getServer().halt(false);
        }
    }
    private static net.minecraftforge.common.ForgeConfigSpec.DoubleValue[] values() {
        return new net.minecraftforge.common.ForgeConfigSpec.DoubleValue[]{CombatBalanceConfig.GLOBAL,
                CombatBalanceConfig.STANDARD, CombatBalanceConfig.IAIDO, CombatBalanceConfig.DANGAKU,
                CombatBalanceConfig.RENGEKI, CombatBalanceConfig.SLASH_ART, CombatBalanceConfig.SUMMONED_SWORD};
    }
    private static void verifyConfigured() throws Exception {
        var config = net.minecraftforge.fml.config.ConfigTracker.INSTANCE.fileMap().get("blade-tetra-server.toml");
        Class<?> wrapperClass = Class.forName("com.mrcrayfish.configured.impl.forge.ForgeConfig");
        Object wrapper = wrapperClass.getConstructor(net.minecraftforge.fml.config.ModConfig.class).newInstance(config);
        Object root = wrapperClass.getMethod("getRoot").invoke(wrapper);
        Class<?> entryClass = Class.forName("com.mrcrayfish.configured.api.IConfigEntry");
        var groups = (java.util.List<?>) entryClass.getMethod("getChildren").invoke(root);
        Object balance = null;
        for (Object group : groups) {
            if ("combatBalance".equals(entryClass.getMethod("getEntryName").invoke(group))) balance = group;
        }
        check(balance != null, "Configured exposes combatBalance group");
        check("config.blade_tetra.combatBalance".equals(entryClass.getMethod("getTranslationKey").invoke(balance)),
                "Configured reads localized group key");
        var entries = (java.util.List<?>) entryClass.getMethod("getChildren").invoke(balance);
        check(entries.size() == 7, "Configured exposes all seven multipliers");
        Class<?> valueClass = Class.forName("com.mrcrayfish.configured.api.IConfigValue");
        for (Object entry : entries) {
            Object value = entryClass.getMethod("getValue").invoke(entry);
            String translation = (String) entryClass.getMethod("getTranslationKey").invoke(entry);
            check(translation.startsWith("config.blade_tetra.combatBalance."), "Configured reads value translation");
            check(!(boolean) valueClass.getMethod("requiresWorldRestart").invoke(value), "Configured permits live world edits");
            check(!(boolean) valueClass.getMethod("requiresGameRestart").invoke(value), "Configured permits live game edits");
            check((boolean) valueClass.getMethod("isValid", Object.class).invoke(value, .75D), "Configured accepts valid multiplier");
            check(!(boolean) valueClass.getMethod("isValid", Object.class).invoke(value, 11D), "Configured rejects out-of-range multiplier");
        }
        LogUtils.getLogger().info("COMBAT_BALANCE_CONFIGURED_PASS: seven editable translated SERVER values, no restart flags");
    }

    private static void verifyFileReload(net.minecraft.server.level.ServerPlayer player, Zombie target,
            net.minecraft.world.damagesource.DamageSource source) throws Exception {
        var config = net.minecraftforge.fml.config.ConfigTracker.INSTANCE.fileMap().get("blade-tetra-server.toml");
        // Independent file writer emulates an external TOML editor, not ConfigValue.set().
        try (var editor = com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(config.getFullPath())
                .sync().preserveInsertionOrder().build()) {
            editor.load();
            editor.set("combatBalance.globalDamageMultiplier", .375D);
            editor.save();
        }
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (CombatBalanceConfig.GLOBAL.get() != .375D && System.nanoTime() < deadline) Thread.sleep(50);
        check(CombatBalanceConfig.GLOBAL.get() == .375D, "Forge file watcher reloads multiplier without restart");
        var nextHit = new LivingHurtEvent(target, source, 10);
        CombatBalanceRuntime.damage(nextHit);
        check(close(nextHit.getAmount(), 3.75F), "reloaded TOML applies to next hit");
        LogUtils.getLogger().info("COMBAT_BALANCE_FILE_RELOAD_PASS: actual TOML edit / Forge watcher / next hit");
    }
    private static boolean close(float a, float b) { return Math.abs(a - b) < .0001F; }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private CombatBalanceSmokeTest() {}
}
