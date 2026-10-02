package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.ability.SummonedSwordArts;
import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.capability.mobeffect.IMobEffectState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Pin the small native surface we adapt; a dependency upgrade must not silently change it. */
class SpeedEfficiencyHookContractTest {
    @Test void normalChargeIsStillAnInheritedDefault() throws Exception {
        assertTrue(ISlashBladeState.class.getMethod("getFullChargeTicks", LivingEntity.class).isDefault());
        assertThrows(NoSuchMethodException.class,
                () -> SlashBladeState.class.getDeclaredMethod("getFullChargeTicks", LivingEntity.class));
    }
    @Test void summonedSwordScheduleEntrypointHasTheExpectedShape() throws Exception {
        var method = SummonedSwordArts.class.getDeclaredMethod("lambda$onInputChange$4", long.class, int.class, IInputState.class);
        assertFalse(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }
    @Test void superSaScheduleEntrypointHasTheExpectedShape() throws Exception {
        var method = SuperSlashArts.class.getDeclaredMethod("lambda$onInputChange$5", long.class,
                RandomSource.class, ServerPlayer.class, InputCommandEvent.class, IInputState.class);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }
    @Test void dodgeUsesPublicCapabilityNotNativePrivateFields() throws Exception {
        assertEquals(Optional.class, IMobEffectState.class.getMethod("getAvoidCooldown").getReturnType());
        assertEquals(void.class, IMobEffectState.class.getMethod("setAvoidCooldown", Optional.class).getReturnType());
        assertEquals(int.class, IMobEffectState.class.getMethod("getAvoidCount").getReturnType());
    }
}
