package dev.bladetetra.combat;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.ability.SummonedSwordArts;
import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect;
import mods.flammpfeil.slashblade.event.Scheduler;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.timers.TimerQueue;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

/** Opt-in real Forge/Mixin diagnostic, isolated by -PspeedEfficiencySmoke; never runs in normal worlds. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SpeedEfficiencySmokeTest {
    private SpeedEfficiencySmokeTest() { }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.speedEfficiencySmoke")) return;
        try {
            var player = FakePlayerFactory.getMinecraft(event.getServer().overworld());
            ItemStack blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            player.setItemSlot(EquipmentSlot.MAINHAND, blade);
            var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
            state.setBroken(false); state.setSealed(false);
            player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(128);
            LogUtils.getLogger().info("SPEED_EFFICIENCY_DIAGNOSTIC: state={}, declaring={}, native={}, adapted={}, eligible={}, speed={}",
                    state.getClass().getName(), state.getClass().getMethod("getFullChargeTicks", net.minecraft.world.entity.LivingEntity.class).getDeclaringClass().getName(),
                    state.getFullChargeTicks(player), SpeedEfficiencyRuntime.normalSaTicks(state, player, 9),
                    SpeedEfficiencyRuntime.eligible(player), player.getAttributeValue(Attributes.ATTACK_SPEED));
            check(state.getFullChargeTicks(player) == 6, "normal SA mixin / held state identity");
            var stand = new net.minecraft.world.entity.decoration.ArmorStand(player.level(), 0, 0, 0);
            stand.setItemSlot(EquipmentSlot.MAINHAND, blade.copy());
            check(stand.getAttribute(Attributes.ATTACK_SPEED) == null, "diagnostic mob lacks attack speed");
            check(SpeedEfficiencyRuntime.reduction(stand) == 0, "missing mob attribute safely stays native");
            var input = player.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
            long now = player.level().getGameTime();
            var inputEvent = new InputCommandEvent(player, input, EnumSet.noneOf(InputCommand.class),
                    EnumSet.of(InputCommand.M_DOWN));
            SpeedEfficiencyInputHandler.beforeInput(inputEvent);
            // Invoke the actual transformed native scheduler entrypoints. Reflection
            // belongs ONLY to this opt-in diagnostic, not the production feature.
            Method sword = SummonedSwordArts.class.getDeclaredMethod("lambda$onInputChange$4", long.class, int.class, IInputState.class);
            sword.setAccessible(true);
            sword.invoke(SummonedSwordArts.getInstance(), now, 1, input);
            Method superSa = SuperSlashArts.class.getDeclaredMethod("lambda$onInputChange$5", long.class,
                    RandomSource.class, ServerPlayer.class, InputCommandEvent.class, IInputState.class);
            superSa.setAccessible(true);
            superSa.invoke(null, now, player.getRandom(), player, inputEvent, input);
            Map<String, Long> deadlines = deadlines(input.getScheduler());
            for (String name : new String[]{"SpiralSwords", "StormSwords", "BlisteringSwords", "HeavyRainSwords"})
                check(deadlines.getOrDefault(name, -1L) == now + 6, "single adapted sword callback: " + name);
            check(deadlines.getOrDefault("chargeSuperSA", -1L) == now + 14, "Super SA callback");
            check(deadlines.getOrDefault("sendPartical", -1L) == now + 4, "Super SA preparation VFX");
            check(deadlines.size() == 6, "no duplicate timers");

            var effects = player.getCapability(CapabilityMobEffect.MOB_EFFECT).orElseThrow(IllegalStateException::new);
            effects.setAvoidCount(0); effects.setAvoidCooldown(java.util.Optional.empty());
            player.setOnGround(true);
            input.getCommands().clear(); input.getCommands().addAll(EnumSet.of(InputCommand.SPRINT, InputCommand.FORWARD, InputCommand.ON_GROUND));
            var dodge = new InputCommandEvent(player, input, EnumSet.noneOf(InputCommand.class), input.getCommands().clone());
            MinecraftForge.EVENT_BUS.post(dodge);
            check(effects.getAvoidCount() == 1, "native ground dodge still executes");
            check(effects.getAvoidCooldown().orElse(-1L) == now + 16, "ground recovery uses native public capability");
            check(effects.doAvoid(now) == 2 && effects.doAvoid(now) == 3 && effects.doAvoid(now) == 0,
                    "native three-dodge limit preserved");
            check(effects.getAvoidCooldown().orElse(-1L) == now + 16, "extra attempts cannot shorten recovery again");
            check(effects.doAvoid(now+16) == 1, "native recovery really reopens after the adapted cycle");

            Scheduler probe = new Scheduler();
            int[] probeCalls = {0};
            SpeedEfficiencyRuntime.schedule(probe, "chargeSuperSA", now+20,
                    (actor, queue, when) -> probeCalls[0]++, player, now);
            check(deadlines(probe).get("chargeSuperSA") == now+14, "accelerated callback keeps its scheduled time");
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            for (var queued : entries(probe)) queued.callback.handle(player,
                    new TimerQueue<>(Scheduler.SB_CALLBACKS), now+14);
            check(probeCalls[0] == 0, "switching blades cancels accelerated preparation");
            check(state.getFullChargeTicks(player) == 9, "nonmodular held item stays native");
            check(SpeedEfficiencyRuntime.scheduledAt(player, "chargeSuperSA", now+20, now) == now+20,
                    "nonmodular Super SA stays native");
            LogUtils.getLogger().info("SPEED_EFFICIENCY_SMOKE_PASS: normal=6, swords=6, super=14, vfx=4, ground=16; native fallback=9/20");
            event.getServer().halt(false);
        } catch (Exception failure) {
            LogUtils.getLogger().error("SPEED_EFFICIENCY_SMOKE_FAIL", failure);
            event.getServer().halt(false);
            throw new IllegalStateException("Speed efficiency runtime smoke failed", failure);
        }
    }

    private static Map<String, Long> deadlines(Scheduler scheduler) throws ReflectiveOperationException {
        Map<String, Long> result = new TreeMap<>();
        for (var entry : entries(scheduler)) {
            if (result.put(entry.id, entry.triggerTime) != null) throw new IllegalStateException("Duplicate timer " + entry.id);
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    private static Queue<TimerQueue.Event<net.minecraft.world.entity.LivingEntity>> entries(Scheduler scheduler)
            throws ReflectiveOperationException {
        Field schedulerQueue = Scheduler.class.getDeclaredField("queue"); schedulerQueue.setAccessible(true);
        Object timerQueue = schedulerQueue.get(scheduler);
        Field events = TimerQueue.class.getDeclaredField("queue"); events.setAccessible(true);
        return (Queue<TimerQueue.Event<net.minecraft.world.entity.LivingEntity>>) events.get(timerQueue);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
