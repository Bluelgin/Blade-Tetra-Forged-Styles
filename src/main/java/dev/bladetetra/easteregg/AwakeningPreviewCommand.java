package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Operator visual replay of an already-awakened held blade; cannot grant an unlock. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class AwakeningPreviewCommand {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("blade_tetra_ritual_preview")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var soul = SoulLegacyState.active(player.getMainHandItem());
                    if (soul == SoulLegacyState.Legacy.NONE || !AwakenedBladeCombat.replacesLegacy()) {
                        context.getSource().sendFailure(Component.translatable("message.blade_tetra.ritual.need_awakened"));
                        return 0;
                    }
                    AwakeningPresentationService.success(player, soul);
                    return 1;
                }));
    }
    private AwakeningPreviewCommand() {}
}
