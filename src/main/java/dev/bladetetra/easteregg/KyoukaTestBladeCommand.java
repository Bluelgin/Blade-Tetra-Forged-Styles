package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.visual.SayaPresetSkin;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.items.modular.IModularItem;

/**
 * Operator-only test convenience: create a real initialized Tetra Kyouka blade.
 * A raw /give of an empty modular stack does not necessarily initialize its
 * module/capability state, so tests should use the same item APIs as production.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class KyoukaTestBladeCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("blade_tetra_test_kyouka")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    ItemStack blade = createDiagnosticBlade();
                    if (blade.isEmpty()) {
                        context.getSource().sendFailure(Component.translatable("command.blade_tetra.kyouka_test.failure"));
                        return 0;
                    }
                    if (!player.getInventory().add(blade)) player.drop(blade, false);
                    context.getSource().sendSuccess(() ->
                            Component.translatable("command.blade_tetra.kyouka_test.success"), false);
                    return 1;
                }));
    }

    /**
     * Internal V-teleport diagnostic build only. This is the exact same initialized
     * awakened blade for the creative tab and the existing OP command. Not a raw
     * modular item: Tetra modules and SlashBlade state are initialized first.
     */
    public static ItemStack createDiagnosticBlade() {
        ModularSlashBladeItem item = ModItems.MODULAR_SLASHBLADE.get();
        ItemStack blade = item.createDefaultStack();
        IModularItem.putModuleInSlot(blade,
                ModularSlashBladeItem.BLADE_SLOT,
                ModularSlashBladeItem.BLADE_MODULE,
                ModularSlashBladeItem.BLADE_MODULE + "_material",
                "katana_blade/amethyst");
        IModularItem.putModuleInSlot(blade,
                ModularSlashBladeItem.INSCRIPTION_SLOT,
                ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE,
                ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE + "_material",
                "awakened_soul_inscription/proudsoul_sphere");
        IModularItem.updateIdentifier(blade);
        item.syncDerivedBladeState(blade);
        SayaPresetSkin.apply(blade, SayaPresetSkin.KYOUKA);
        if (!KyoukaAwakening.unlockForDebug(blade)) return ItemStack.EMPTY;
        blade.setHoverName(Component.literal("[TEST] 镜花水月 · V残影诊断")
                .withStyle(ChatFormatting.AQUA));
        return blade;
    }

    private KyoukaTestBladeCommand() {}
}
