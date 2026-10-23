package dev.bladetetra.customstyle;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.items.modular.IModularItem;
import java.util.List;

/** Only runs in the explicitly selected, separate input-review world. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class CustomStyleInputReviewSetup {
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("blade_tetra.customStyleInputReview") || !(event.getEntity() instanceof ServerPlayer player)) return;
        player.setGameMode(GameType.CREATIVE);
        for (BlockPos pos : BlockPos.betweenClosed(-32, 99, -32, 32, 99, 32)) player.serverLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        player.teleportTo(0.5, 100, 0.5);
        configure(player, 0);
        StyleRecordingSession.importCode(player, CustomStyleExchange.export(new CustomStyleSpec("Seed draft", route(4))));
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                CustomStyleScrolls.create(new CustomStyleSpec("Held scroll", route(1))));
    }
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("blade_tetra.customStyleInputReview")) return;
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("blade_tetra_input_case")
                .then(net.minecraft.commands.Commands.argument("case", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 5))
                        .executes(context -> { configure(context.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "case")); return 1; })));
    }
    private static void configure(ServerPlayer player, int scenario) {
        player.teleportTo(0.5, 100, 0.5); player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        String length = scenario == 1 ? "short" : scenario == 3 ? "long" : "standard";
        IModularItem.putModuleInSlot(blade, ModularSlashBladeItem.BLADE_SLOT,
                "slashblade/custom_" + length + "_blade",
                "slashblade/custom_" + length + "_blade_material", "custom_" + length + "_blade/iron");
        IModularItem.updateIdentifier(blade);
        blade.getOrCreateTag().put(CustomStyleSpec.STACK_KEY, new CustomStyleSpec("Input review " + scenario, route(scenario)).encode());
        ModItems.MODULAR_SLASHBLADE.get().syncDerivedBladeState(blade);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, blade);
        CustomComboRouter.interrupt(player);
        player.containerMenu.broadcastChanges();
        com.mojang.logging.LogUtils.getLogger().info("CUSTOM_INPUT_CASE case={} blade={} length={}", scenario, blade.getTag().getString("id"), length);
    }
    public static List<net.minecraft.resources.ResourceLocation> route(int scenario) {
        String[] actions = switch (scenario) {
            case 0 -> new String[]{"upperslash", "upperslash", "rapid_slash"};
            case 1 -> new String[]{"combo_a1", "combo_b1", "combo_a2"};
            case 2 -> new String[]{"combo_a1", "combo_a1", "combo_b1"};
            case 3 -> new String[]{"combo_a1", "combo_b1", "combo_a2", "combo_b2", "combo_a3", "combo_c"};
            case 4 -> new String[]{"combo_a1"};
            default -> new String[]{"rapid_slash", "combo_a1"};
        };
        return java.util.Arrays.stream(actions).map(path -> new net.minecraft.resources.ResourceLocation("slashblade", path)).toList();
    }
    private CustomStyleInputReviewSetup() {}
}
