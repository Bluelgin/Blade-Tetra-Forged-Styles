package dev.bladetetra.forging;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.bladetetra.combat.LegacyFusionHandler;
import dev.bladetetra.combat.ProgrammaticFusionAttunement;
import dev.bladetetra.combat.ProgrammaticFusionHandler;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Forge server smoke, advancing the real native inventory and event paths for two fake players. */
final class NativeFusionSmoke {
    private static final ResourceLocation H = SlashArtsRegistry.DRIVE_HORIZONTAL.getId();
    private static final ResourceLocation V = SlashArtsRegistry.DRIVE_VERTICAL.getId();
    private static final ResourceLocation MISSING = new ResourceLocation("fusion_missing_addon", "void_cut");

    static void verify(ServerPlayer player) {
        var level = player.serverLevel();
        var other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FusionOther"));
        var probe = new Probe(player, other);
        long originalTime = level.getGameTime();
        ItemStack originalBlade = player.getMainHandItem();
        boolean creative = player.getAbilities().instabuild;
        boolean onGround = player.onGround();
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            level.getChunk(0, 0);
            player.moveTo(4, 160, 4, 0, 0);
            other.moveTo(9, 160, 4, 0, 0);
            player.setOnGround(true);
            other.setOnGround(true);
            check(ProgrammaticFusionHandler.available(H) && ProgrammaticFusionHandler.available(V)
                    && !ProgrammaticFusionHandler.available(MISSING), "live SA registry");

            equip(player, H, V);
            equip(other, V, H);
            player.getAbilities().instabuild = false;
            probe.reenter = true;
            var primary = player.getMainHandItem();
            ((ItemSlashBlade) primary.getItem()).releaseUsing(primary, level, player,
                    primary.getUseDuration() - 40);
            check(state(player).getProudSoulCount() == 55, "one normal release cost");
            state(other).doChargeAction(other, state(other).getFullChargeTicks(other));
            advance(level, 3, player, other);
            primary.inventoryTick(level, player, player.getInventory().selected, true);
            advance(level, 142, player, other);
            probe.expect(player, List.of(H, V), List.of(H, V));
            probe.expect(other, List.of(V, H), List.of(V, H));
            check(probe.types(player).equals(List.of(SlashArts.ArtsType.Success, SlashArts.ArtsType.Success)),
                    "Normal event tier");
            check(probe.types(other).equals(List.of(SlashArts.ArtsType.Jackpot, SlashArts.ArtsType.Jackpot)),
                    "Just event tier");
            check(state(player).getProudSoulCount() == 55, "B did not charge again");
            check(probe.recursions == 4, "recursive attempts were checked on all four events");
            probe.reenter = false;

            List<Case> cases = List.of(
                    new Case(H, MISSING, List.of(H), List.of(H)),
                    new Case(MISSING, V, List.of(V), List.of(V)),
                    new Case(MISSING, MISSING, List.of(), List.of()),
                    new Case(null, V, List.of(V), List.of(V)));
            for (Case test : cases) {
                probe.reset();
                equip(player, test.a(), test.b());
                state(player).doChargeAction(player, 40);
                advance(level, 145, player);
                probe.expect(player, test.events(), test.drives());
            }

            probe.reset();
            equip(player, H, V);
            state(player).setKillCount(1000);
            player.getMainHandItem().setHoverName(net.minecraft.network.chat.Component.literal("Fusion Super"));
            check(state(player).getSlashArts().doArts(SlashArts.ArtsType.Super, player)
                    .equals(ComboStateRegistry.NONE.getId()), "selector alone cannot bypass Super conditions");
            SuperSlashArts.releaseSSA(player);
            advance(level, 145, player);
            // Both real base drive arts inherit the generic Super selector.
            probe.expect(player, List.of(H, V), List.of(H, V));
            check(probe.types(player).equals(List.of(SlashArts.ArtsType.Success, SlashArts.ArtsType.Success)),
                    "Super inherited entries run their real Normal variants");

            probe.reset();
            equip(player, SlashArtsRegistry.JUDGEMENT_CUT.getId(), V);
            state(player).setKillCount(1000);
            player.getMainHandItem().setHoverName(net.minecraft.network.chat.Component.literal("Fusion Super"));
            SuperSlashArts.releaseSSA(player);
            check(state(player).getComboSeq().equals(ComboStateRegistry.JUDGEMENT_CUT_END.getId()),
                    "explicit native Super combo committed");
            check(probe.types(player).equals(List.of(SlashArts.ArtsType.Super)), "true Super event tier");
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            advance(level, 1);

            probe.reset();
            equip(player, H, V);
            probe.cancelRelease = H;
            check(state(player).doChargeAction(player, 40).equals(ComboStateRegistry.NONE.getId()),
                    "cancelled release");
            advance(level, 145, player);
            probe.expect(player, List.of(H), List.of());

            probe.reset();
            equip(player, H, V);
            probe.cancelMotion = H;
            check(state(player).doChargeAction(player, 40).equals(ComboStateRegistry.NONE.getId()),
                    "selected A was not committed");
            advance(level, 145, player);
            probe.expect(player, List.of(H), List.of());

            probe.reset();
            equip(player, H, V);
            state(player).doChargeAction(player, 40);
            advance(level, 4, player);
            state(player).updateComboSeq(player, ComboStateRegistry.NONE.getId());
            advance(level, 145, player);
            probe.expect(player, List.of(H), List.of(H));

            probe.reset();
            equip(player, H, V);
            probe.cancelMotion = V;
            state(player).doChargeAction(player, 40);
            advance(level, 145, player);
            probe.expect(player, List.of(H, V), List.of(H));

            probe.reset();
            equip(player, H, V);
            state(player).doChargeAction(player, 40);
            advance(level, 4, player);
            player.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem().copy());
            advance(level, 145);
            probe.expect(player, List.of(H), List.of(H));

            LogUtils.getLogger().info("NATIVE_FUSION_SMOKE_PASS: live SA registry, source identity, "
                    + "normal/Just/Super, one cost, native drives, cancellations, interruption, "
                    + "missing addon, non-SA metadata, recursion and two players");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            player.setItemSlot(EquipmentSlot.MAINHAND, originalBlade);
            other.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            advance(level, 1);
            player.getAbilities().instabuild = creative;
            player.setOnGround(onGround);
            level.getServer().getWorldData().overworldData().setGameTime(originalTime);
        }
    }

    private static void equip(ServerPlayer player, ResourceLocation a, ResourceLocation b) {
        ItemStack stack = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        ManualFusionSmoke.fit(stack, "saya", source("fusion_smoke_a", a));
        ManualFusionSmoke.fit(stack, "tsuba", source("fusion_smoke_b", b));
        check(ProgrammaticFusionAttunement.confirm(stack), "test pair confirmation");
        stack.enchant(Enchantments.SHARPNESS, 1);
        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        ISlashBladeState state = state(player);
        LegacyFusionHandler.sync(stack, state);
        state.setBroken(false);
        state.setSealed(false);
        state.setDamage(0);
        state.setProudSoulCount(100);
        state.setComboSeq(ComboStateRegistry.NONE.getId());
        state.setLastActionTime(player.level().getGameTime());
    }

    private static LegacyImprintKind source(String id, ResourceLocation art) {
        var base = LegacyImprintKind.WOODEN_PUPPET;
        return new LegacyImprintKind(id, H, base.model(), base.texture(), base.material(),
                base.defaultProfile(), base.baseAttack(), base.maxDamage(), art, List.of(H));
    }

    private static ISlashBladeState state(ServerPlayer player) {
        return player.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .orElseThrow(IllegalStateException::new);
    }

    private static void advance(ServerLevel level, int ticks, ServerPlayer... players) {
        for (int i = 0; i < ticks; i++) {
            var server = level.getServer();
            server.getWorldData().overworldData().setGameTime(level.getGameTime() + 1);
            for (ServerPlayer player : players) {
                player.getMainHandItem().inventoryTick(level, player, player.getInventory().selected, true);
            }
            LegacyFusionHandler.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Native fusion: " + message);
    }

    private record Case(ResourceLocation a, ResourceLocation b, List<ResourceLocation> events,
            List<ResourceLocation> drives) {}

    public static final class Probe {
        private final List<ServerPlayer> players;
        private final Map<ServerPlayer, List<Cast>> releases = new HashMap<>();
        private final Map<ServerPlayer, List<ResourceLocation>> drives = new HashMap<>();
        ResourceLocation cancelRelease;
        ResourceLocation cancelMotion;
        boolean reenter;
        int recursions;

        Probe(ServerPlayer... players) {
            this.players = List.of(players);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onRelease(SlashBladeEvent.PerformSlashArtEvent event) {
            if (!(event.getEntityLiving() instanceof ServerPlayer player) || !players.contains(player)) return;
            var state = event.getSlashBladeState();
            check(state.getSlashArtsKey().equals(ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId()),
                    "source callback mutated the stored key");
            ResourceLocation art = SlashArts.getRegistryKey(state.getSlashArts());
            releases.computeIfAbsent(player, ignored -> new ArrayList<>()).add(new Cast(art, event.getType()));
            if (reenter) {
                check(state.doChargeAction(player, 40).equals(ComboStateRegistry.NONE.getId()), "reentry escaped guard");
                recursions++;
            }
            if (art.equals(cancelRelease)) event.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onMotion(BladeMotionEvent event) {
            if (cancelMotion != null && players.contains(event.getEntity()) && cancelMotion.equals(event.getCombo())) event.setCanceled(true);
        }

        @SubscribeEvent
        public void onDrive(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof EntityDrive drive
                    && drive.getOwner() instanceof ServerPlayer player && players.contains(player)) {
                ResourceLocation id = SlashArts.getRegistryKey(state(player).getSlashArts());
                drives.computeIfAbsent(player, ignored -> new ArrayList<>()).add(id);
            }
        }

        void expect(ServerPlayer player, List<ResourceLocation> arts, List<ResourceLocation> entities) {
            List<ResourceLocation> actualArts = releases.getOrDefault(player, List.of()).stream().map(Cast::id).toList();
            List<ResourceLocation> actualEntities = drives.getOrDefault(player, List.of());
            check(actualArts.equals(arts) && actualEntities.equals(entities),
                    "expected events " + arts + " and drives " + entities + ", got "
                            + actualArts + " and " + actualEntities);
        }

        List<SlashArts.ArtsType> types(ServerPlayer player) {
            return releases.getOrDefault(player, List.of()).stream().map(Cast::type).toList();
        }

        void reset() {
            releases.clear();
            drives.clear();
            cancelRelease = null;
            cancelMotion = null;
        }
    }

    private record Cast(ResourceLocation id, SlashArts.ArtsType type) {}
    private NativeFusionSmoke() {}
}
