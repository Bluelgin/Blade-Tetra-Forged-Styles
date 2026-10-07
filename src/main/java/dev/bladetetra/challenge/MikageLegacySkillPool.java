package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.SkillSpec;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import static dev.bladetetra.challenge.MikageEntity.Technique;
import static dev.bladetetra.challenge.mikage.SkillSpec.Tactic.*;

/** Existing skills only. Replace this pool after the new moveset is agreed. */
final class MikageLegacySkillPool {
    static List<SkillSpec> available(MikageEntity owner, ServerPlayer target, int phase) {
        var arena = owner.arenaController();
        var techniques = owner.techniqueRuntime();
        var defense = owner.defenseController();
        double distance = owner.distanceTo(target);
        double vertical = Math.abs(target.getY() - owner.getY());
        boolean lowHealth = target.getHealth() <= target.getMaxHealth() * 0.35F;
        List<Technique> choices = new ArrayList<>();
        if (vertical > 3.5D) {
            choices.add(Technique.DRIVE_FAN);
            choices.add(Technique.SUMMONED_VOLLEY);
            choices.add(Technique.JUDGEMENT_CUT);
        } else if (distance <= 4.5D) {
            choices.add(Technique.STEP_IAIDO);
            choices.add(Technique.BLADE_COMBO);
            choices.add(Technique.CIRCLE_SLASH);
            if (phase >= 2 && !lowHealth) {
                choices.add(Technique.DANGAKU_CLEAVE);
                choices.add(Technique.FLASH_COUNTER);
                choices.add(Technique.SAKURA_END);
            }
        } else if (distance <= 12.0D) {
            choices.add(Technique.STEP_IAIDO);
            if (techniques.mirrorDuelCooldown <= 0) {
                choices.add(Technique.MIRROR_DUEL);
            }
            choices.add(Technique.WAVE_EDGE);
            if (phase >= 2) {
                choices.add(Technique.JUDGEMENT_CUT);
                choices.add(Technique.SUMMONED_VOLLEY);
                if (!lowHealth) {
                    choices.add(Technique.AERIAL_RAIN);
                    choices.add(Technique.DANGAKU_CLEAVE);
                }
            }
        } else {
            choices.add(Technique.WAVE_EDGE);
            choices.add(Technique.DRIVE_FAN);
            choices.add(Technique.SUMMONED_VOLLEY);
            choices.add(Technique.STEP_IAIDO);
            if (phase >= 2 && !lowHealth) {
                choices.add(Technique.JUDGEMENT_CUT);
                choices.add(Technique.AERIAL_RAIN);
            }
        }
        if (phase >= 2) {
            if (techniques.boundarySealCooldown <= 0) {
                choices.add(Technique.BOUNDARY_SEAL);
            }
            if (techniques.moonEchoCooldown <= 0) {
                choices.add(Technique.MOON_ECHO);
            }
            if (arena.toriiCageCooldown <= 0 && !lowHealth) {
                choices.add(Technique.TORII_CAGE);
            }
        }
        if (owner.hasShadowCrossPressure(target)) {
            choices.removeIf(choice -> choice == Technique.WAVE_EDGE
                    || choice == Technique.DRIVE_FAN);
            choices.add(Technique.STEP_IAIDO);
            if (distance <= 6.0D) {
                choices.add(Technique.CIRCLE_SLASH);
                choices.add(Technique.FLASH_COUNTER);
            }
            if (phase >= 2 && techniques.moonEchoCooldown <= 0) {
                choices.add(Technique.MOON_ECHO);
            }
            defense.swordWheelCooldown = Math.min(defense.swordWheelCooldown, 12);
        }
        if (phase == 3 && arena.toriiSweepCooldown <= 0) choices.add(Technique.TORII_SWEEP);
        if (phase == 3) choices.add(Technique.SUPER_JUDGEMENT);
        return choices.stream().distinct().map(MikageLegacySkillPool::spec).toList();
    }

    static Technique technique(String id) {
        for (Technique technique : Technique.values()) if (id(technique).equals(id)) return technique;
        throw new IllegalArgumentException("Unknown legacy skill: " + id);
    }

    static String id(Technique technique) {
        return "blade_tetra:legacy/" + technique.name().toLowerCase(Locale.ROOT);
    }

    static SkillSpec spec(Technique technique) {
        var tactics = EnumSet.noneOf(SkillSpec.Tactic.class);
        double min = 0, max = 12;
        switch (technique) {
            case STEP_IAIDO, MIRROR_DUEL -> { tactics.add(GAP_CLOSE); max = 24; }
            case CIRCLE_SLASH, BLADE_COMBO, SAKURA_END -> { tactics.add(CLOSE); max = 6; }
            case DANGAKU_CLEAVE, TORII_SWEEP, TORII_CAGE -> { tactics.add(GUARD_PRESSURE); max = 8; }
            case FLASH_COUNTER -> { tactics.add(COUNTER); max = 8; }
            case DRIVE_FAN, SUMMONED_VOLLEY -> { tactics.add(RANGED); tactics.add(ANTI_AIR); min = 6; max = 32; }
            case WAVE_EDGE, JUDGEMENT_CUT, SUPER_JUDGEMENT, AERIAL_RAIN -> { tactics.add(RANGED); min = 6; max = 32; }
            case BOUNDARY_SEAL, MOON_ECHO -> { tactics.add(SETUP); max = 32; }
            default -> {}
        }
        return new SkillSpec(id(technique), technique.style.name().toLowerCase(Locale.ROOT), tactics, min, max);
    }

    private MikageLegacySkillPool() {}
}
