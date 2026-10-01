package dev.bladetetra.combat;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyFusionProjectileImpactTest {
    @Test
    void FlyingSummonedSwordWithNullHitDoesNotCrashOrReadProjectileState() {
        ProjectileImpactEvent event = noProjectileAccess(null);
        assertDoesNotThrow(() -> LegacyFusionHandler.onProjectileImpact(event));
        assertEquals(ProjectileImpactEvent.ImpactResult.DEFAULT, event.getImpactResult());
    }

    @Test
    void NullHitPreservesOtherListenersImpactDecision() {
        ProjectileImpactEvent event = noProjectileAccess(null);
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        LegacyFusionHandler.onProjectileImpact(event);
        assertEquals(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, event.getImpactResult());
    }

    @Test
    void MissAndBlockHitsAreNotInterceptedByCosmeticEntityFiltering() {
        for (HitResult.Type type : new HitResult.Type[]{HitResult.Type.MISS, HitResult.Type.BLOCK}) {
            ProjectileImpactEvent event = noProjectileAccess(hit(type));
            assertDoesNotThrow(() -> LegacyFusionHandler.onProjectileImpact(event));
            assertEquals(ProjectileImpactEvent.ImpactResult.DEFAULT, event.getImpactResult());
        }
    }

    @Test
    void EntityHitWithoutVisualMarkerKeepsNativeImpactBehavior() {
        ProjectileImpactEvent event = new ProjectileImpactEvent(null, hit(HitResult.Type.ENTITY));
        LegacyFusionHandler.onProjectileImpact(event);
        assertEquals(ProjectileImpactEvent.ImpactResult.DEFAULT, event.getImpactResult());
    }

    private static HitResult hit(HitResult.Type type) {
        return new HitResult(Vec3.ZERO) {
            @Override
            public Type getType() { return type; }
        };
    }

    private static ProjectileImpactEvent noProjectileAccess(HitResult hit) {
        return new ProjectileImpactEvent(null, hit) {
            @Override
            public Projectile getProjectile() {
                throw new AssertionError("No entity hit: projectile state must not be inspected");
            }
        };
    }
}
