package dev.bladetetra.customstyle;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.bladetetra.BladeTetra;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Temporary native-text authoring controls. Astra's eventual GUI may call the
 * same server APIs; no client can bypass registry/action validation.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CustomStyleCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("blade_tetra_style")
                .requires(source -> source.hasPermission(0))
                .then(Commands.literal("show").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    var snap = StyleRecordingSession.snapshot(player);
                    player.sendSystemMessage(Component.literal(
                            snap.name() + " (" + snap.actions().size() + "/" + snap.maximum() + ")")
                            .withStyle(ChatFormatting.GOLD));
                    for (int i = 0; i < snap.actions().size(); i++) {
                        player.sendSystemMessage(Component.literal(
                                (i + 1) + ". " + snap.actions().get(i)).withStyle(ChatFormatting.GRAY));
                    }
                    return 1;
                }))
                .then(Commands.literal("undo").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    StyleRecordingSession.undo(player);
                    return inform(player, "undone");
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    StyleRecordingSession.clear(player);
                    return inform(player, "cleared");
                }))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    var player = ctx.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(ctx, "name");
                                    return inform(player, StyleRecordingSession.rename(player, name)
                                            ? "renamed" : "invalid_name");
                                })))
                .then(Commands.literal("finish").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    CustomStyleSpec style = StyleRecordingSession.finish(player);
                    if (style == null) return inform(player, "empty");
                    give(player, CustomStyleScrolls.create(style));
                    return inform(player, "finished");
                }))
                .then(Commands.literal("export").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    ItemStack stack = player.getMainHandItem();
                    CustomStyleSpec style = CustomStyleScrolls.read(stack);
                    if (style == null) style = CustomStyleScrolls.read(player.getOffhandItem());
                    if (style == null) return inform(player, "hold_scroll");
                    String code = CustomStyleExchange.export(style);
                    player.sendSystemMessage(Component.translatable(
                            "message.blade_tetra.style_exchange.exported")
                            .withStyle(ChatFormatting.GOLD));
                    player.sendSystemMessage(Component.literal(code).withStyle(s -> s
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))));
                    return 1;
                }))
                .then(Commands.literal("import")
                        .then(Commands.argument("code", StringArgumentType.word()).executes(ctx -> {
                            var player = ctx.getSource().getPlayerOrException();
                            String code = StringArgumentType.getString(ctx, "code");
                            var result = CustomStyleExchange.importValidated(code);
                            if (!result.accepted()) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.blade_tetra.style_exchange." + result.status().name()
                                                .toLowerCase(java.util.Locale.ROOT))
                                        .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            give(player, CustomStyleScrolls.create(result.style()));
                            return inform(player, "imported");
                        }))));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.containerMenu.broadcastChanges();
    }

    private static int inform(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.translatable(
                "message.blade_tetra.style_exchange." + message)
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private CustomStyleCommands() {}
}
