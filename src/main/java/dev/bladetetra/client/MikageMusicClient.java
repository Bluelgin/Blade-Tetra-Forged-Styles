package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Owns the non-positional, per-player soundtrack for the active Mikage challenge. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageMusicClient {
    private static final ResourceLocation MUSIC_EVENT = new ResourceLocation(
            BladeTetra.MOD_ID, "music.mikage_battle");
    private static final ResourceLocation MIRROR_REALM =
            new ResourceLocation(BladeTetra.MOD_ID, "mirror_realm");
    private static MikageBattleMusic active;
    private static long activeChallengeId;
    private static int musicSuppressionTicks;
    private static int replayCooldown;

    public static void start(long challengeId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientVisualConfig.ENABLE_MIKAGE_MUSIC.get()) {
            stopImmediately();
            return;
        }
        if (!minecraft.getSoundManager().getAvailableSounds().contains(MUSIC_EVENT)) {
            stopImmediately();
            return;
        }
        if (active != null && !active.isStopped() && activeChallengeId == challengeId) {
            return;
        }
        stopImmediately();
        minecraft.getMusicManager().stopPlaying();
        activeChallengeId = challengeId;
        active = new MikageBattleMusic();
        minecraft.getSoundManager().play(active);
        musicSuppressionTicks = 100;
    }

    /** Continuous HUD sync calls this as a recovery path after reloads or packet loss. */
    public static void ensurePlaying(long challengeId) {
        if (challengeId == 0L || !ClientVisualConfig.ENABLE_MIKAGE_MUSIC.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (active == null || active.isStopped() || activeChallengeId != challengeId) {
            start(challengeId);
            return;
        }
        if (replayCooldown <= 0 && active.age > 20
                && !minecraft.getSoundManager().isActive(active)) {
            // A rejected/invalidated streaming instance cannot reliably be queued again.
            // Replace it so resource reloads and engine restarts recover cleanly.
            minecraft.getSoundManager().stop(active);
            active = new MikageBattleMusic();
            minecraft.getSoundManager().play(active);
            replayCooldown = 40;
        }
    }

    public static void fadeOut() {
        if (active != null) {
            active.beginFadeOut();
        }
        activeChallengeId = 0L;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || active == null) {
            return;
        }
        if (replayCooldown > 0) replayCooldown--;
        Minecraft minecraft = Minecraft.getInstance();
        if (active.isStopped()) {
            active = null;
            activeChallengeId = 0L;
            return;
        }
        if (!ClientVisualConfig.ENABLE_MIKAGE_MUSIC.get()
                || minecraft.player == null
                || minecraft.level == null
                || !minecraft.level.dimension().location().equals(MIRROR_REALM)) {
            stopImmediately();
            return;
        }
        if (--musicSuppressionTicks <= 0) {
            minecraft.getMusicManager().stopPlaying();
            musicSuppressionTicks = 100;
        }
    }

    private static void stopImmediately() {
        if (active != null) {
            Minecraft.getInstance().getSoundManager().stop(active);
            active = null;
        }
        activeChallengeId = 0L;
    }

    private static final class MikageBattleMusic extends AbstractTickableSoundInstance {
        private static final int FADE_IN_TICKS = 40;
        private static final int FADE_OUT_TICKS = 30;
        private int age;
        private int fadeOutTicks = -1;

        MikageBattleMusic() {
            // A dedicated boss track behaves more like a streamed record than ambient
            // vanilla music. RECORDS keeps it audible while vanilla music is suppressed.
            super(ModSounds.MIKAGE_BATTLE_MUSIC.get(), SoundSource.RECORDS,
                    RandomSource.create());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            // SoundEngine normally discards zero-volume sounds before their first tick.
            // Start inaudibly above zero, then perform the intended two-second fade-in.
            volume = 0.001F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        void beginFadeOut() {
            if (fadeOutTicks < 0) {
                fadeOutTicks = FADE_OUT_TICKS;
            }
        }

        @Override
        public void tick() {
            age++;
            float configuredVolume = ClientVisualConfig.MIKAGE_MUSIC_VOLUME.get().floatValue();
            float fadeIn = Math.min(1.0F, age / (float) FADE_IN_TICKS);
            if (fadeOutTicks >= 0) {
                if (fadeOutTicks-- <= 0) {
                    stop();
                    return;
                }
                volume = configuredVolume * fadeIn
                        * (fadeOutTicks / (float) FADE_OUT_TICKS);
            } else {
                volume = configuredVolume * fadeIn;
            }
        }
    }

    private MikageMusicClient() {
    }
}
