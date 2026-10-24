package dev.bladetetra.aura;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Only this projectile contact bypasses hurt cooldown. Native damage type and all other tags stay intact. */
final class SwordAuraDamageSource extends DamageSource {
    SwordAuraDamageSource(Entity projectile, LivingEntity attacker) {
        super(projectile.damageSources().indirectMagic(projectile, attacker).typeHolder(), projectile, attacker);
    }
    @Override public boolean is(TagKey<DamageType> tag) {
        return DamageTypeTags.BYPASSES_COOLDOWN.equals(tag) || super.is(tag);
    }
}
