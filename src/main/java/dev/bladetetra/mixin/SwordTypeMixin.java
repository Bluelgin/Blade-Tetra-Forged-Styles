package dev.bladetetra.mixin;

import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.SwordType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumSet;

/**
 * Restricts the anvil-name shortcut for Blade Tetra's modular blade only.
 *
 * <p>Resharped treats any enchanted, custom-named blade as bewitched. Modular
 * blades instead require their blade state to be explicitly marked as
 * default-bewitched, which is done by a proudsoul inscription. Existing
 * converted blades which already carry that state remain valid.</p>
 */
@Mixin(value = SwordType.class, remap = false)
public abstract class SwordTypeMixin {
    @Inject(method = "from", at = @At("RETURN"))
    private static void bladeTetra$restrictModularBewitchedSource(
            ItemStack stack,
            CallbackInfoReturnable<EnumSet<SwordType>> callback) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        boolean defaultBewitched = stack.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.isDefaultBewitched())
                .orElse(false);
        if (!defaultBewitched) {
            callback.getReturnValue().remove(SwordType.BEWITCHED);
        }
    }
}
