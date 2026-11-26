package dev.btsealed.probe;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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

public final class SealedFusionReceiverSmoke {
    private static final ResourceLocation H = SlashArtsRegistry.DRIVE_HORIZONTAL.getId();
    private static final ResourceLocation V = SlashArtsRegistry.DRIVE_VERTICAL.getId();
    private static final ResourceLocation JC = SlashArtsRegistry.JUDGEMENT_CUT.getId();
    private static final ResourceLocation DISPATCHER = ResourceLocation.parse("bt_export_runtime:programmatic_fusion");

    static void verify(ServerLevel world) {
        var normal = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "SealedFusionA"));
        var just = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "SealedFusionB"));
        var probe = new Probe(List.of(normal, just));
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            world.getChunk(0, 0);
            normal.moveTo(4, 160, 4, 0, 0);
            just.moveTo(9, 160, 4, 0, 0);
            var sword = equip(world, normal, "bt_export_fusion");
            equip(world, just, "bt_export_fusion");
            ((ItemSlashBlade) sword.getItem()).releaseUsing(sword, world, normal, sword.getUseDuration() - 40);
            require(state(normal).getProudSoulCount() == 55, "Native Normal cost is not 45");
            state(just).doChargeAction(just, state(just).getFullChargeTicks(just));
            advance(world, 145, normal, just);
            probe.check(normal, List.of(new Cast(H, SlashArts.ArtsType.Success), new Cast(V, SlashArts.ArtsType.Success)), List.of(H, V));
            probe.check(just, List.of(new Cast(H, SlashArts.ArtsType.Jackpot), new Cast(V, SlashArts.ArtsType.Jackpot)), List.of(H, V));
            require(state(normal).getProudSoulCount() == 55, "B debited a second charge");

            probe.reset();
            equip(world, normal, "bt_export_fusion");
            probe.cancel = V;
            state(normal).doChargeAction(normal, 40);
            advance(world, 145, normal);
            probe.check(normal, List.of(new Cast(H, SlashArts.ArtsType.Success), new Cast(V, SlashArts.ArtsType.Success)), List.of(H));

            probe.reset();
            equip(world, normal, "bt_export_fusion");
            probe.cancel = H;
            state(normal).doChargeAction(normal, 40);
            advance(world, 145, normal);
            probe.check(normal, List.of(new Cast(H, SlashArts.ArtsType.Success)), List.of());

            probe.reset();
            equip(world, normal, "bt_export_fusion");
            state(normal).doChargeAction(normal, 40);
            advance(world, 4, normal);
            normal.setItemInHand(InteractionHand.MAIN_HAND, normal.getMainHandItem().copy());
            advance(world, 145, normal);
            probe.check(normal, List.of(new Cast(H, SlashArts.ArtsType.Success)), List.of(H));

            probe.reset();
            equip(world, normal, "bt_export_fusion_super");
            state(normal).setKillCount(1000);
            state(normal).setDefaultBewitched(true);
            SuperSlashArts.releaseSSA(normal);
            require(state(normal).getComboSeq().equals(ComboStateRegistry.JUDGEMENT_CUT_END.getId()),
                    "Native Super did not commit Judgement Cut End");
            advance(world, 145, normal);
            probe.check(normal, List.of(new Cast(JC, SlashArts.ArtsType.Super),
                    new Cast(V, SlashArts.ArtsType.Success)), List.of(V));
            LogUtils.getLogger().info("BLADE_EXPORT_NATIVE_FUSION_RECEIVER_PASS");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            normal.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            just.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            advance(world, 1);
        }
    }

    private static ItemStack equip(ServerLevel world, ServerPlayer player, String id) {
        var saved = ExportRuntimeProbe.createNamedBlade(world, id).save(new CompoundTag());
        var blade = ItemStack.of(saved);
        blade.enchant(Enchantments.SHARPNESS, 1);
        player.setOnGround(true);
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        var state = state(player);
        require(DISPATCHER.equals(state.getSlashArtsKey()), "Saving and loading lost the portable dispatcher");
        state.setBroken(false);
        state.setSealed(false);
        state.setDamage(0);
        state.setProudSoulCount(100);
        state.setComboSeq(ComboStateRegistry.NONE.getId());
        state.setLastActionTime(world.getGameTime());
        return blade;
    }

    private static ISlashBladeState state(ServerPlayer player) {
        return player.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
    }

    private static void advance(ServerLevel level, int ticks, ServerPlayer... players) {
        for (int i = 0; i < ticks; i++) {
            var server = level.getServer();
            server.getWorldData().overworldData().setGameTime(level.getGameTime() + 1);
            for (ServerPlayer player : players) {
                player.getMainHandItem().inventoryTick(level, player, player.getInventory().selected, true);
            }
            MinecraftForge.EVENT_BUS.post(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("Sealed fusion: " + reason);
    }

    private record Cast(ResourceLocation art, SlashArts.ArtsType tier) {}

    public static final class Probe {
        private final List<ServerPlayer> players;
        private final Map<ServerPlayer, List<Cast>> releases = new HashMap<>();
        private final Map<ServerPlayer, List<ResourceLocation>> drives = new HashMap<>();
        private ResourceLocation cancel;

        private Probe(List<ServerPlayer> players) { this.players = players; }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onRelease(SlashBladeEvent.PerformSlashArtEvent event) {
            if (!(event.getEntityLiving() instanceof ServerPlayer player) || !players.contains(player)) return;
            var state = event.getSlashBladeState();
            require(DISPATCHER.equals(state.getSlashArtsKey()), "A source context overwrote the saved SA ID");
            var art = SlashArts.getRegistryKey(state.getSlashArts());
            releases.computeIfAbsent(player, p -> new ArrayList<>()).add(new Cast(art, event.getType()));
            require(state.doChargeAction(player, 40).equals(ComboStateRegistry.NONE.getId()), "Release recursively cast");
            if (art.equals(cancel)) event.setCanceled(true);
        }

        @SubscribeEvent
        public void onDrive(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof EntityDrive drive
                    && drive.getOwner() instanceof ServerPlayer player && players.contains(player)) {
                var art = SlashArts.getRegistryKey(state(player).getSlashArts());
                drives.computeIfAbsent(player, p -> new ArrayList<>()).add(art);
            }
        }

        void reset() {
            releases.clear();
            drives.clear();
            cancel = null;
        }

        void check(ServerPlayer player, List<Cast> arts, List<ResourceLocation> entities) {
            require(arts.equals(releases.getOrDefault(player, List.of()))
                    && entities.equals(drives.getOrDefault(player, List.of())),
                    "Expected " + arts + " with " + entities + ", got " + releases.get(player) + " with " + drives.get(player));
        }
    }

    private SealedFusionReceiverSmoke() {}
}
