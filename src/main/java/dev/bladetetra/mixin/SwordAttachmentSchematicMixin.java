package dev.bladetetra.mixin;

import dev.bladetetra.compat.attachments.SwordAttachmentSchematics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.mickelus.tetra.data.SchematicStore;

/** Runs before registry material expansion/listeners, on server and client reloads. */
@Mixin(value = SchematicStore.class, remap = false)
public abstract class SwordAttachmentSchematicMixin {
    @Inject(method = "processData", at = @At("TAIL"))
    private void bladeTetra$mountSwordAttachments(CallbackInfo callback) {
        SwordAttachmentSchematics.adapt(((SchematicStore) (Object) this).getData());
    }
}
