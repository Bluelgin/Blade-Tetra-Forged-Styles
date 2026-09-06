package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialAppearance;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Weather interaction supplied by conductive modular blade components. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConductiveBladeHandler {
    private static final String STRIKE_COOLDOWN =
            "blade_tetra_conductive_strike_cooldown";
    private static final String ATTRACTION_COOLDOWN =
            "blade_tetra_conductive_attraction_cooldown";
    // Rolled once per second: one conductive point averages twenty minutes
    // of exposed thunderstorm time, while six points average 3m20s.
    /** Returns 0-6; one ordinary conductive component contributes one point. */
    public static int conductivityScore(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)) return 0;
        MaterialAppearance appearance = MaterialAppearance.fromStack(stack);
        int score = 0;
        score += conductivity(appearance.blade());
        score += conductivity(appearance.tsuka());
        score += conductivity(appearance.tsuba());
        score += conductivity(appearance.saya());
        score += conductivity(appearance.habaki());
        score += conductivity(appearance.kashira());
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(
                ModularSlashBladeItem.FULLER_SLOT, Tag.TAG_STRING)) {
            score += conductivity(appearance.fuller());
        }
        return Math.min(6, score);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END
                || player.level().isClientSide()
                || player.tickCount % 20 != 0
                || !(player.level() instanceof ServerLevel level)
                || !level.isThundering()
                || !level.canSeeSky(player.blockPosition())
                || !GameplayConfig.ENABLE_PLAYER_LIGHTNING_ATTRACTION.get()) {
            return;
        }

        ItemStack blade = player.getMainHandItem();
        int score = conductivityScore(blade);
        if (score <= 0) return;
        long now = level.getGameTime();
        if (player.getPersistentData().getLong(ATTRACTION_COOLDOWN) > now
                || player.getRandom().nextInt(
                        GameplayConfig.ATTRACTION_ROLL_DENOMINATOR.get()) >= score) {
            return;
        }

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(player.getX(), player.getY(), player.getZ());
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            bolt.setCause(serverPlayer);
        }
        level.addFreshEntity(bolt);
        player.getPersistentData().putLong(ATTRACTION_COOLDOWN, now + 200L);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLightningDamage(LivingHurtEvent event) {
        if (!event.getSource().is(DamageTypes.LIGHTNING_BOLT)) return;
        ItemStack blade = event.getEntity().getMainHandItem();
        if (conductivityScore(blade) > 0) {
            // The blade grounds part of the charge, keeping the ritual risky
            // without making an unlucky strike an almost certain death.
            event.setAmount(event.getAmount()
                    * GameplayConfig.LIGHTNING_DAMAGE_MULTIPLIER.get().floatValue());
        }
    }

    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        ItemStack blade = event.getBlade();
        // Awakened Raikiri uses its deterministic chain circuit instead of the
        // ordinary conductive blade's random thunderstorm strike.
        if (SoulLegacyState.isActive(blade, SoulLegacyState.Legacy.RAIKIRI)) return;
        if (event.getUser().level().isClientSide()
                || !(event.getUser().level() instanceof ServerLevel level)
                || !GameplayConfig.ENABLE_OFFENSIVE_LIGHTNING.get()
                || !level.isThundering()
                || !level.canSeeSky(event.getTarget().blockPosition())) {
            return;
        }

        int score = conductivityScore(blade);
        if (score <= 0) return;
        long now = level.getGameTime();
        if (blade.getOrCreateTag().getLong(STRIKE_COOLDOWN) > now) return;

        double chance = Math.min(
                GameplayConfig.OFFENSIVE_CHANCE_CAP.get(),
                score * GameplayConfig.OFFENSIVE_CHANCE_PER_POINT.get());
        if (event.getUser().getRandom().nextDouble() >= chance) return;

        LightningBolt visualBolt = EntityType.LIGHTNING_BOLT.create(level);
        if (visualBolt != null) {
            visualBolt.moveTo(
                    event.getTarget().getX(),
                    event.getTarget().getY(),
                    event.getTarget().getZ());
            visualBolt.setVisualOnly(true);
            level.addFreshEntity(visualBolt);
        }
        float damage = (float) Math.min(
                GameplayConfig.OFFENSIVE_DAMAGE_CAP.get(),
                GameplayConfig.OFFENSIVE_DAMAGE_BASE.get()
                        + score * GameplayConfig.OFFENSIVE_DAMAGE_PER_POINT.get());
        event.getTarget().hurt(level.damageSources().lightningBolt(), damage);
        blade.getOrCreateTag().putLong(
                STRIKE_COOLDOWN,
                now + GameplayConfig.OFFENSIVE_COOLDOWN_TICKS.get());
    }

    private static int conductivity(String material) {
        if (containsAny(material,
                "dragonsteel_lightning", "lightning", "signalum")) {
            return 2;
        }
        if (containsAny(material,
                "copper", "silver", "gold", "electrum", "bronze", "brass",
                "constantan", "aluminum", "aluminium")) {
            return 1;
        }
        return 0;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private ConductiveBladeHandler() {
    }
}
