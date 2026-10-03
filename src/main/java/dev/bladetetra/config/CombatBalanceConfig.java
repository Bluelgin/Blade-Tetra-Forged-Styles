package dev.bladetetra.config;

import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.CombatBalanceRules;
import net.minecraftforge.common.ForgeConfigSpec;

/** Part of the existing world-owned SERVER spec; values are read for each damage request. */
public final class CombatBalanceConfig {
    public static ForgeConfigSpec.DoubleValue GLOBAL, STANDARD, IAIDO, DANGAKU, RENGEKI, SLASH_ART, SUMMONED_SWORD;

    static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Damage multipliers for player-owned Blade Tetra modular blades only.",
                "仅影响玩家使用的模块化拔刀剑；总倍率 × 流派倍率 × 攻击类型倍率。",
                "All defaults are 1.0 (unchanged). Reflected damage inherits its original scaling.",
                "默认 1.0，不改现有平衡；继承已结算伤害的效果不会再次缩放。")
                .translation("config.blade_tetra.combatBalance").push("combatBalance");
        GLOBAL = multiplier(builder, "globalDamageMultiplier", "All supported modular-blade damage.", "所有受支持的模块刀伤害。乘以对应流派和攻击类型倍率。");
        STANDARD = multiplier(builder, "standardDamageMultiplier", "Standard style damage.", "本传流伤害倍率。");
        IAIDO = multiplier(builder, "iaidoDamageMultiplier", "Iaido style damage.", "居合流伤害倍率。");
        DANGAKU = multiplier(builder, "dangakuDamageMultiplier", "Dangaku style damage.", "断岳流伤害倍率。");
        RENGEKI = multiplier(builder, "rengekiDamageMultiplier", "Rengeki style damage.", "连舞流伤害倍率。");
        SLASH_ART = multiplier(builder, "slashArtDamageMultiplier", "Slash Art damage, including supported Super SA and its projectiles.", "SA 与受支持的 Super SA 伤害倍率，包含其斩波和幻影剑，不再叠乘普通幻影剑倍率。");
        SUMMONED_SWORD = multiplier(builder, "summonedSwordDamageMultiplier", "Summoned swords outside Slash Arts; does not also multiply SA projectiles.", "非 SA 幻影剑伤害倍率，不与 SA 倍率重复叠乘。");
        builder.pop();
    }

    private static ForgeConfigSpec.DoubleValue multiplier(ForgeConfigSpec.Builder builder, String key, String english, String chinese) {
        return builder.comment(english, chinese, "0 = no damage, 1 = unchanged, 0.75 = 25% less. Range: 0..10. Read on the next hit; no restart required.")
                .translation("config.blade_tetra.combatBalance." + key).defineInRange(key, 1.0D, 0.0D, 10.0D);
    }

    public static double multiplier(BladeStyle style, CombatBalanceRules.AttackKind kind) {
        double styleScale = switch (style) {
            case STANDARD -> STANDARD.get();
            case IAIDO -> IAIDO.get();
            case DANGAKU -> DANGAKU.get();
            case RENGEKI -> RENGEKI.get();
        };
        double attackScale = switch (kind) {
            case ORDINARY -> 1.0D;
            case SLASH_ART -> SLASH_ART.get();
            case SUMMONED_SWORD -> SUMMONED_SWORD.get();
        };
        return CombatBalanceRules.multiplier(GLOBAL.get(), styleScale, attackScale);
    }

    private CombatBalanceConfig() {}
}
