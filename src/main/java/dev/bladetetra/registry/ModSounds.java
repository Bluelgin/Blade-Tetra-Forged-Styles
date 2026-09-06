package dev.bladetetra.registry;

import dev.bladetetra.BladeTetra;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BladeTetra.MOD_ID);

    public static final RegistryObject<SoundEvent> MIKAGE_BATTLE_MUSIC = SOUND_EVENTS.register(
            "music.mikage_battle",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(BladeTetra.MOD_ID, "music.mikage_battle")));

    private ModSounds() {
    }
}
