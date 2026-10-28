package dev.bladetetra.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import org.spongepowered.asm.mixin.Mixin;

/** Marker for the narrow post-apply bridge: Mixin 0.8.5 cannot inject interface default methods. */
@Mixin(value = ISlashBladeState.class, remap = false)
public interface NativeBladeCommitMixin {}
