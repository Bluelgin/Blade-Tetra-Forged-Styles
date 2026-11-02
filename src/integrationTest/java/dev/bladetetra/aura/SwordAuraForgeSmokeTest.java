package dev.bladetetra.aura;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.CombatBalanceRuntime;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.customstyle.ComboCompatibilityResolver;
import dev.bladetetra.customstyle.CustomComboRouter;
import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRankCapabilityProvider;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.items.modular.IModularItem;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Opt-in, isolated native Item.use -> real slash -> Lifeng projectile integration. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SwordAuraForgeSmokeTest {
    private static ServerPlayer actor;
    private static int auras;
    private static boolean sssRank;
    private static final Set<Long> slashTicks = new HashSet<>();

    @SubscribeEvent public static void entity(EntityJoinLevelEvent event) {
        if (actor == null || event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof SwordAuraDriveEntity aura && aura.getOwner() == actor) {
            check(aura.getDamage() > 0, "CUSTOM has no extra decorative projectile");
            auras++;
        }
        if (event.getEntity() instanceof EntitySlashEffect slash && slash.getShooter() == actor && slash.getDamage() > 0)
            slashTicks.add(event.getLevel().getGameTime());
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.lifengSmoke")) return;
        try {
            verify(event.getServer().overworld());
            LogUtils.getLogger().info("LIFENG_CUSTOM_SMOKE_PASS");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("LIFENG_CUSTOM_SMOKE_FAIL", failure);
            throw new IllegalStateException("Lifeng CUSTOM smoke failed", failure);
        } finally {
            actor = null;
            event.getServer().halt(false);
        }
    }

    private static void verify(ServerLevel level) {
        actor = FakePlayerFactory.getMinecraft(level);
        level.addNewPlayer(actor);
        actor.teleportTo(0.5, level.getMaxBuildHeight() - 20, 0.5);
        actor.setOnGround(true);
        var blade = blade("standard", ids("combo_a1"));
        equip(blade);
        rank(true);
        use(blade); tick(level, blade, 28);
        check(auras == 0, "unlearned players do not emit aura");
        check(SwordAuraMastery.learn(actor), "learn Lifeng");
        rank(false); reset(blade); use(blade); tick(level, blade, 28);
        check(auras == 0, "rank below SSS does not emit aura");
        rank(true);

        for (String length : List.of("short", "standard", "long")) {
            for (var route : List.of(ids("combo_a1", "combo_a1", "combo_b1"),
                    ids("upperslash", "upperslash", "rapid_slash"),
                    ids("combo_a1", "combo_b1", "combo_a2", "combo_b2", "combo_a3", "combo_c"),
                    List.of(new ResourceLocation("blade_tetra", "iaido_draw"), new ResourceLocation("blade_tetra", "dangaku_sweep")))) {
                blade = blade(length, route); equip(blade);
                check(StyleResolver.resolve(blade) == BladeStyle.CUSTOM, "custom blade resolves");
                var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
                for (int i = 0; i < route.size() * 2; i++) {
                    int before = auras; slashTicks.clear();
                    use(blade);
                    check(state.getComboSeq().equals(route.get(i % route.size())), "authored input order unchanged");
                    tick(level, blade, 28);
                    check(auras > before, "actual ordinary slash emits aura: " + route.get(i % route.size()));
                    check(auras - before == slashTicks.size(), "one damage budget per actual slash tick");
                }
            }
            LogUtils.getLogger().info("LIFENG_CUSTOM_LENGTH_PASS length={}", length);
        }

        // A normal motion ID inside a real SA execution context is still an SA.
        int before = auras;
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        tick(level, blade, 1);
        CombatBalanceRuntime.slashArt(actor, () -> {
            state.updateComboSeq(actor, ComboStateRegistry.COMBO_A1.getId());
            return null;
        });
        tick(level, blade, 28);
        check(auras == before, "SA context cannot masquerade as an ordinary motion");
        CombatBalanceRuntime.ordinaryCombo(actor);
        state.setComboSeq(ComboStateRegistry.COMBO_A1.getId());
        slash(level, 0, true); slash(level, 1, false);
        check(auras == before, "zero-damage and ownerless decoration excluded");

        // Registry presence alone cannot opt an external move into CUSTOM aura.
        var external = new ResourceLocation("lifeng_smoke", "ordinary_cut");
        check(!SwordAuraMotionFilter.permits(external, BladeStyle.CUSTOM), "unadapted addon rejected");
        ComboCompatibilityResolver.registerOrdinaryAction(external);
        check(SwordAuraMotionFilter.permits(external, BladeStyle.CUSTOM), "audited addon ordinary permission reused");
        state.setComboSeq(external); state.setLastActionTime(level.getGameTime());
        tick(level, blade, 1);
        slash(level, 1, true);
        check(auras == before + 1, "audited addon real slash emits aura");
        check(!SwordAuraMotionFilter.permits(ComboStateRegistry.JUDGEMENT_CUT.getId(), BladeStyle.CUSTOM), "SA motion excluded");
    }

    private static ItemStack blade(String length, List<ResourceLocation> route) {
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        IModularItem.putModuleInSlot(blade, ModularSlashBladeItem.BLADE_SLOT, "slashblade/custom_" + length + "_blade",
                "slashblade/custom_" + length + "_blade_material", "custom_" + length + "_blade/iron");
        IModularItem.updateIdentifier(blade);
        blade.getOrCreateTag().put(CustomStyleSpec.STACK_KEY, new CustomStyleSpec("Lifeng smoke", route).encode());
        ModItems.MODULAR_SLASHBLADE.get().syncDerivedBladeState(blade);
        return blade;
    }
    private static void equip(ItemStack blade) {
        actor.getInventory().selected = 0; actor.getInventory().setItem(0, blade);
        reset(blade);
    }
    private static void reset(ItemStack blade) {
        CustomComboRouter.interrupt(actor); CombatBalanceRuntime.ordinaryCombo(actor);
        blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new).setComboSeq(ComboStateRegistry.NONE.getId());
    }
    private static void use(ItemStack blade) {
        blade.getItem().use(actor.level(), actor, InteractionHand.MAIN_HAND); actor.stopUsingItem();
    }
    private static void tick(ServerLevel level, ItemStack blade, int ticks) {
        for (int i = 0; i < ticks; i++) {
            rank(sssRank);
            blade.getItem().inventoryTick(blade, level, actor, 0, true);
            level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + 1);
        }
    }
    private static void rank(boolean sss) {
        sssRank = sss;
        var rank = actor.getCapability(ConcentrationRankCapabilityProvider.RANK_POINT).orElseThrow(IllegalStateException::new);
        rank.setRawRankPoint(sss ? rank.getMaxCapacity() : 0); rank.setLastUpdte(actor.level().getGameTime());
    }
    private static void slash(ServerLevel level, double damage, boolean owned) {
        var slash = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, level);
        slash.setPos(actor.position()); slash.setDamage(damage);
        if (owned) slash.setShooter(actor);
        level.addFreshEntity(slash);
    }
    private static List<ResourceLocation> ids(String... paths) {
        return java.util.Arrays.stream(paths).map(path -> new ResourceLocation("slashblade", path)).toList();
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class FixtureRegistration {
        @SubscribeEvent public static void register(net.minecraftforge.registries.RegisterEvent event) {
            if (!Boolean.getBoolean("blade_tetra.lifengSmoke")) return;
            event.register(mods.flammpfeil.slashblade.registry.combo.ComboState.REGISTRY_KEY, helper -> helper.register(
                    new ResourceLocation("lifeng_smoke", "ordinary_cut"),
                    mods.flammpfeil.slashblade.registry.combo.ComboState.Builder.newInstance().startAndEnd(0, 10).priority(100)
                            .timeout(100).nextOfTimeout(user -> ComboStateRegistry.NONE.getId()).build()));
        }
    }
    private SwordAuraForgeSmokeTest() {}
}
