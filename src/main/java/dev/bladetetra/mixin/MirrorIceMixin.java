package dev.bladetetra.mixin;

import dev.bladetetra.challenge.ChallengeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IceBlock.class)
public abstract class MirrorIceMixin {
    /*
     * This project intentionally has no generated Mixin refmap. Forge's
     * userdev runtime exposes the official name while a packaged Forge game
     * exposes the stable SRG name, so list both explicitly instead of asking
     * Mixin to remap a name it cannot resolve from the release jar.
     */
    @Inject(
            method = { "randomTick", "m_213898_" },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void bladeTetra$keepMirrorIce(BlockState state, ServerLevel level,
            BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (level.dimension().equals(ChallengeManager.MIRROR_REALM)) {
            callback.cancel();
        }
    }
}
