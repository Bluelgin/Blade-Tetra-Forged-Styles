package dev.bladetetra.mixin;

import dev.bladetetra.storage.BladeStackStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only touches serialized modular blades; live tags and share tags keep their public schema. */
@Mixin(value = ItemStack.class, remap = false)
public abstract class BladeStackStorageMixin {
    // This project does not ship a refmap. Include both verified 1.20.1 names
    // so development (Mojmap) and distributed Forge (SRG) use the same hooks.
    @ModifyVariable(method = {"of", "m_41712_"}, at = @At("HEAD"), argsOnly = true)
    private static CompoundTag bladeTetra$expandStoredBlade(CompoundTag saved) {
        return BladeStackStorage.decode(saved);
    }

    @Inject(method = {"save", "m_41739_"}, at = @At("RETURN"))
    private void bladeTetra$compactStoredBlade(CompoundTag destination,
                                              CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag saved = callback.getReturnValue();
        CompoundTag compact = BladeStackStorage.encode(saved);
        if (compact == saved) return;
        // Preserve ItemStack.save's contract: callers may ignore its return value.
        saved.put("tag", compact.getCompound("tag"));
        if (compact.contains("ForgeCaps")) saved.put("ForgeCaps", compact.get("ForgeCaps"));
        else saved.remove("ForgeCaps");
    }
}
