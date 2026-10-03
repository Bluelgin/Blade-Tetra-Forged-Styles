package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.CombatBalanceConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.entity.IShootable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Supplier;

/** Server-side ownership snapshots, not persistent stat overrides or global SlashBlade nerfs. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class CombatBalanceRuntime {
    private static final String STYLE = "blade_tetra_balance_style";
    private static final String KIND = "blade_tetra_balance_kind";
    private static final String SA_COMBO = "blade_tetra_balance_sa_combo";
    private static final String SA_UNTIL = "blade_tetra_balance_sa_until";
    private static final ThreadLocal<LivingEntity> CHARGING = new ThreadLocal<>();

    public static <T> T slashArt(LivingEntity user, Supplier<T> action) {
        LivingEntity previous = CHARGING.get();
        CHARGING.set(user);
        try { return action.get(); }
        finally { if (previous == null) CHARGING.remove(); else CHARGING.set(previous); }
    }

    public static void ordinaryCombo(LivingEntity user) {
        user.getPersistentData().remove(SA_COMBO);
        user.getPersistentData().remove(SA_UNTIL);
    }

    public static void selectedSlashArt(LivingEntity user, ResourceLocation combo) {
        if (user.level().isClientSide() || !(user instanceof Player)
                || !(user.getMainHandItem().getItem() instanceof ModularSlashBladeItem)
                || combo == null || "none".equals(combo.getPath())) return;
        user.getPersistentData().putString(SA_COMBO, combo.toString());
        user.getPersistentData().putLong(SA_UNTIL, user.level().getGameTime() + 300L);
    }

    public static void prepareCombo(ISlashBladeState state, LivingEntity user, ResourceLocation next) {
        if (user.level().isClientSide() || !(user instanceof Player)
                || !(user.getMainHandItem().getItem() instanceof ModularSlashBladeItem)) return;
        if (CHARGING.get() == user || isSlashArt(user, state)) {
            if (next != null && !"none".equals(next.getPath())) {
                user.getPersistentData().putString(SA_COMBO, next.toString());
                user.getPersistentData().putLong(SA_UNTIL, user.level().getGameTime() + 300L);
            } else ordinaryCombo(user);
        }
    }

    private static boolean isSlashArt(LivingEntity user, ISlashBladeState state) {
        if (CHARGING.get() == user) return true;
        if (user instanceof net.minecraft.server.level.ServerPlayer player
                && ForgedSlashArtHandler.activeDamageScale(player) != 1.0F) return true;
        return state != null && user.getPersistentData().getLong(SA_UNTIL) >= user.level().getGameTime()
                && state.getComboSeq().toString().equals(user.getPersistentData().getString(SA_COMBO));
    }

    public static CombatBalanceRules.AttackKind kind(LivingEntity owner, Entity attack) {
        var state = owner.getMainHandItem().getCapability(ModularSlashBladeItem.BLADESTATE).orElse(null);
        if (isSlashArt(owner, state)) return CombatBalanceRules.AttackKind.SLASH_ART;
        return attack instanceof EntityAbstractSummonedSword
                ? CombatBalanceRules.AttackKind.SUMMONED_SWORD : CombatBalanceRules.AttackKind.ORDINARY;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void projectile(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide() || !nativeAttack(entity)
                || !(entity instanceof IShootable shot) || !(shot.getShooter() instanceof Player owner)) return;
        ItemStack blade = owner.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem) || entity.getPersistentData().contains(STYLE)) return;
        // Snapshot identity, not the numerical multiplier: later config edits affect the next hit.
        entity.getPersistentData().putString(STYLE, StyleResolver.resolve(blade).name());
        entity.getPersistentData().putString(KIND, kind(owner, entity).name());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide() || event.getAmount() <= 0
                || InheritedCombatDamage.isInherited(event.getSource())) return;
        Entity direct = event.getSource().getDirectEntity();
        BladeStyle style;
        CombatBalanceRules.AttackKind kind;
        if (direct != null && nativeAttack(direct)) {
            var data = direct.getPersistentData();
            if (!data.contains(STYLE) || !data.contains(KIND)) return;
            try {
                style = BladeStyle.valueOf(data.getString(STYLE));
                kind = CombatBalanceRules.AttackKind.valueOf(data.getString(KIND));
            } catch (IllegalArgumentException invalidMetadata) { return; }
        } else if (event.getSource().getEntity() instanceof Player player
                && (direct == player || direct == null)
                && (event.getSource().is(DamageTypes.PLAYER_ATTACK)
                || event.getSource().typeHolder().unwrapKey()
                .map(key -> BladeTetra.MOD_ID.equals(key.location().getNamespace())).orElse(false))
                && player.getMainHandItem().getItem() instanceof ModularSlashBladeItem) {
            style = StyleResolver.resolve(player.getMainHandItem());
            kind = kind(player, null);
        } else return; // Never scale pets, natural lightning, unmarked projectiles or boss attacks.
        event.setAmount(CombatBalanceRules.damage(event.getAmount(), CombatBalanceConfig.multiplier(style, kind), false));
    }

    /** Explicit authored/delayed damage, with its originating style retained by the caller. */
    public static boolean authoredDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource source,
            float amount, BladeStyle style, CombatBalanceRules.AttackKind kind) {
        float balanced = CombatBalanceRules.damage(amount, CombatBalanceConfig.multiplier(style, kind), false);
        return InheritedCombatDamage.apply(() -> target.hurt(source, balanced));
    }

    private static boolean nativeAttack(Entity entity) {
        return entity instanceof EntitySlashEffect || entity instanceof EntityJudgementCut
                || entity instanceof EntityAbstractSummonedSword;
    }
    private CombatBalanceRuntime() {}
}
