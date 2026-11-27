package dev.btsealed.runtime;

import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

final class PortableBladeCommands {
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            id -> Component.literal("No sealed blade named " + id));

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bladeseal")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("blade", ResourceLocationArgument.id())
                                        .executes(context -> {
                                            var id = ResourceLocationArgument.getId(context, "blade");
                                            var registry = context.getSource().getLevel().registryAccess()
                                                    .registryOrThrow(SlashBladeDefinition.REGISTRY_KEY);
                                            var definition = registry.get(id);
                                            if (definition == null || !PortableProfiles.hasSealedBlade(id)) {
                                                throw UNKNOWN.create(id);
                                            }
                                            ItemStack blade = definition.getBlade();
                                            if (blade.isEmpty()) throw UNKNOWN.create(id);
                                            var targets = EntityArgument.getPlayers(context, "targets");
                                            for (var player : targets) {
                                                var stack = blade.copy();
                                                if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                                                    player.drop(stack, false);
                                                }
                                            }
                                            context.getSource().sendSuccess(
                                                    () -> Component.literal("Gave " + id + " to " + targets.size() + " player(s)"),
                                                    true);
                                            return targets.size();
                                        })))));
    }

    private PortableBladeCommands() {}
}
