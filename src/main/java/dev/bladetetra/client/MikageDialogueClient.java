package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;

/** Client-only presentation for Mikage's story dialogue and optional Japanese VO. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageDialogueClient {
    private static final Deque<PendingVoice> PENDING = new ArrayDeque<>();

    public static void show(String translationKey, String voiceEvent, int holdTicks,
            int delayTicks) {
        if (delayTicks > 0) {
            PENDING.addLast(new PendingVoice(voiceEvent, delayTicks));
            return;
        }
        play(voiceEvent);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        int count = PENDING.size();
        for (int i = 0; i < count; i++) {
            PendingVoice pending = PENDING.removeFirst();
            if (pending.ticksRemaining() <= 1) {
                play(pending.voiceEvent());
            } else {
                PENDING.addLast(new PendingVoice(pending.voiceEvent(),
                        pending.ticksRemaining() - 1));
            }
        }
    }

    private static void play(String voiceEvent) {
        if (voiceEvent == null || voiceEvent.isBlank()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation event = new ResourceLocation(BladeTetra.MOD_ID, voiceEvent);
        if (!minecraft.getSoundManager().getAvailableSounds().contains(event)) {
            return;
        }
        minecraft.getSoundManager().play(new SimpleSoundInstance(event,
                SoundSource.VOICE, 1.0F, 1.0F, RandomSource.create(), false, 0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                0.0D, 0.0D, 0.0D, true));
    }

    private record PendingVoice(String voiceEvent, int ticksRemaining) {
    }

    private MikageDialogueClient() {
    }
}
