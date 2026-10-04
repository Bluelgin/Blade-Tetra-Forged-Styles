package dev.bladetetra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Optional marker: the plugin adapts audited getter receivers after mixin application. */
@Pseudo
@Mixin(targets = {
        "com.mega.revelationfix.common.compat.tetra.effect.BeelzebubEffect",
        "com.mega.revelationfix.common.compat.tetra.effect.CursedBladeEffect",
        "com.mega.revelationfix.common.compat.tetra.effect.DeicideEffect",
        "com.mega.revelationfix.common.compat.tetra.effect.DoomEffect",
        "com.mega.revelationfix.common.compat.tetra.effect.FadingEffect",
        "com.mega.revelationfix.common.compat.tetra.effect.IceBladeEffect",
        "com.mega.revelationfix.common.compat.tetra.effect.MysteriousBladeEffect",
        "com.mega.revelationfix.common.compat.tetra.effect.PositionBladeEffect",
        "com.mega.revelationfix.common.compat.tetra.effect.RulerOfNocturnalEffect",
        "com.mega.revelationfix.common.compat.tetra.effect.ShadowWalkEffect",
        "com.mega.revelationfix.common.compat.tetra.effect.VizirEffect",
        "com.vv.vvaddon.Handler.VVAddonEventHandler",
        "com.inolia_zaicek.more_mod_tetra.Effect.IronSpell.MMTFreezeTetraEffect",
        "com.inolia_zaicek.more_mod_tetra.Effect.IronSpell.MMTManaSiphonTetraEffect"
}, remap = false)
public abstract class ProviderEffectCompatMixin {}
