package dev.btsealed.mixin;

import dev.btsealed.runtime.PortableFusion;
import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The native Super gate and cost run before fusion takes over release. */
@Mixin(value = SuperSlashArts.class, remap = false)
public abstract class PortableFusionSuperMixin {
    @Redirect(method = "lambda$releaseSSA$6", at = @At(value = "INVOKE",
            target = "Lmods/flammpfeil/slashblade/slasharts/SlashArts;doArts(Lmods/flammpfeil/slashblade/slasharts/SlashArts$ArtsType;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/resources/ResourceLocation;"))
    private static ResourceLocation btSealed$fusionSuper(SlashArts art, SlashArts.ArtsType type,
            LivingEntity user) {
        return PortableFusion.ENGINE.releaseSuper(art, type, user);
    }
}
