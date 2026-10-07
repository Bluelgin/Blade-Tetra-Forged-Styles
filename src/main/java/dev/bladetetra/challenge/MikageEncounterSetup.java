package dev.bladetetra.challenge;

import dev.bladetetra.registry.ModItems;
import dev.bladetetra.config.GameplayConfig;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;

import java.util.Collection;

/** Party scaling and equipment setup; retains the existing visitor configuration. */
final class MikageEncounterSetup {
    private MikageEncounterSetup() {}

    static void configureForParty(MikageEntity owner, Collection<ServerPlayer> party, boolean reminiscence,
            long challengeId) {
        // Challenge bosses are managed explicitly by ChallengeManager. Mark the combat
        // form as persistent as well as the visitor form so vanilla mob despawning can
        // never remove Mikage while a challenge is in progress.
        owner.setPersistenceRequired();
        int players = Math.max(1, party.size());
        PowerCalibration calibration = GameplayConfig.MIKAGE_AUTO_DIFFICULTY_SCALING.get()
                ? calibrateParty(party) : PowerCalibration.BASE;
        double scale = 1.0D + Math.max(0, players - 1)
                * GameplayConfig.MIKAGE_PARTY_HEALTH_PER_PLAYER.get();
        scale *= calibration.healthScale;
        if (reminiscence) {
            scale *= GameplayConfig.MIKAGE_REMINISCENCE_HEALTH_MULTIPLIER.get();
        }
        owner.getAttribute(Attributes.MAX_HEALTH).setBaseValue(
                GameplayConfig.MIKAGE_BASE_HEALTH.get() * scale);
        owner.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(
                GameplayConfig.MIKAGE_BASE_DAMAGE.get()
                        * (reminiscence
                                ? GameplayConfig.MIKAGE_REMINISCENCE_DAMAGE_MULTIPLIER.get()
                                : 1.0D)
                        * (1.0D + Math.max(0, players - 1)
                                * GameplayConfig.MIKAGE_PARTY_DAMAGE_PER_PLAYER.get())
                        * calibration.damageScale);
        owner.combatDirector().skillSpeedMultiplier = calibration.skillSpeedScale;
        owner.setHealth(owner.getMaxHealth());
        owner.setItemSlot(EquipmentSlot.MAINHAND,
                ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
        owner.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            state.setColorCode(0xD51F3F);
            state.setEffectColorInverse(false);
            state.setTargetEntityId(-1);
        });
        owner.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        owner.getPersistentData().putLong("blade_tetra_challenge", challengeId);
        owner.getPersistentData().putBoolean("blade_tetra_reminiscence", reminiscence);
        owner.getPersistentData().putDouble("blade_tetra_power_health_scale",
                calibration.healthScale);
        owner.getPersistentData().putDouble("blade_tetra_power_damage_scale",
                calibration.damageScale);
        owner.getPersistentData().putDouble("blade_tetra_power_skill_scale",
                calibration.skillSpeedScale);
        owner.bossBar().setName(Component.translatable(reminiscence
                ? "entity.blade_tetra.mikage.echo" : "entity.blade_tetra.mikage"));
    }

    static void configureVisitor(MikageEntity owner, long challengeId) {
        owner.markVisitorGuide();
        owner.getPersistentData().putBoolean("blade_tetra_visitor_guide", true);
        owner.getPersistentData().putLong("blade_tetra_challenge", challengeId);
        owner.setNoAi(true);
        owner.setInvulnerable(true);
        owner.setPersistenceRequired();
        owner.setItemSlot(EquipmentSlot.MAINHAND,
                ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
        owner.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        owner.bossBar().setVisible(false);
    }

    private static PowerCalibration calibrateParty(Collection<ServerPlayer> party) {
        if (party.isEmpty()) {
            return PowerCalibration.BASE;
        }
        double attackSum = 0.0D;
        double strongestAttack = 0.0D;
        double defenseSum = 0.0D;
        for (ServerPlayer player : party) {
            double attack = safeScore(player.getAttributeValue(Attributes.ATTACK_DAMAGE),
                    1.0D, GameplayConfig.MIKAGE_MAX_SCORED_WEAPON_DAMAGE.get());
            var strength = player.getEffect(MobEffects.DAMAGE_BOOST);
            if (strength != null && strength.getDuration() >= 0
                    && strength.getDuration() < 600) {
                attack = Math.max(1.0D, attack - 3.0D * (strength.getAmplifier() + 1));
            }
            attackSum += attack;
            strongestAttack = Math.max(strongestAttack, attack);

            double health = safeScore(player.getMaxHealth(), 20.0D,
                    GameplayConfig.MIKAGE_MAX_SCORED_PLAYER_HEALTH.get());
            double armor = safeScore(player.getArmorValue(), 0.0D,
                    GameplayConfig.MIKAGE_MAX_SCORED_ARMOR.get());
            double toughness = safeScore(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                    0.0D, GameplayConfig.MIKAGE_MAX_SCORED_ARMOR_TOUGHNESS.get());
            double absorption = safeScore(player.getAbsorptionAmount(), 0.0D,
                    GameplayConfig.MIKAGE_MAX_SCORED_PLAYER_HEALTH.get());
            double defense = 0.15D + 0.40D * health / 20.0D
                    + 0.30D * armor / 20.0D + 0.15D * toughness / 8.0D
                    + 0.10D * absorption / 20.0D;
            var resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance != null && (resistance.getDuration() < 0
                    || resistance.getDuration() >= 600)) {
                defense += Math.min(1.0D, 0.25D * (resistance.getAmplifier() + 1));
            }
            defenseSum += Math.max(1.0D, defense);
        }

        double averageAttack = attackSum / party.size();
        double teamAttack = Math.max(averageAttack, strongestAttack * 0.85D);
        double maxAttack = GameplayConfig.MIKAGE_MAX_SCORED_WEAPON_DAMAGE.get();
        double offenseProgress = Mth.clamp((teamAttack - 20.0D)
                / Math.max(1.0D, maxAttack - 20.0D), 0.0D, 1.0D);
        double defenseProgress = Mth.clamp((defenseSum / party.size() - 1.0D) / 3.0D,
                0.0D, 1.0D);
        return new PowerCalibration(
                1.0D + offenseProgress
                        * (GameplayConfig.MIKAGE_MAX_EQUIPMENT_HEALTH_SCALE.get() - 1.0D),
                1.0D + defenseProgress
                        * (GameplayConfig.MIKAGE_MAX_DEFENSE_DAMAGE_SCALE.get() - 1.0D),
                1.0D + offenseProgress
                        * (GameplayConfig.MIKAGE_MAX_SKILL_SPEED_SCALE.get() - 1.0D));
    }

    private static double safeScore(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return maximum;
        }
        return Mth.clamp(value, minimum, maximum);
    }

    private record PowerCalibration(double healthScale, double damageScale,
            double skillSpeedScale) {
        static final PowerCalibration BASE = new PowerCalibration(1.0D, 1.0D, 1.0D);
    }}
