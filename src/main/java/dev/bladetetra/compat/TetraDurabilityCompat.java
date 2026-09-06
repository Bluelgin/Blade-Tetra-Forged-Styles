package dev.bladetetra.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import se.mickelus.tetra.items.modular.IModularItem;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Bridges the modular-item durability hook added in Tetra 6.10.
 *
 * <p>Tetra 6.3 through 6.9 do not expose {@code damageItemImpl}. On those
 * versions SlashBlade's normal damage path remains responsible for unbreaking
 * and the final durability mutation. On 6.10 and newer the hook is invoked so
 * Tetra's damage event and effects such as bloodbound are preserved.</p>
 */
public final class TetraDurabilityCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Method DAMAGE_ITEM_IMPL = findDamageItemImpl();

    private TetraDurabilityCompat() {
    }

    public static <T extends LivingEntity> int preprocessDamage(
            IModularItem modularItem,
            ItemStack stack,
            int amount,
            T entity,
            Consumer<T> onBroken) {
        if (amount <= 0) {
            return 0;
        }
        if (DAMAGE_ITEM_IMPL == null) {
            return amount;
        }

        try {
            return (int) DAMAGE_ITEM_IMPL.invoke(
                    modularItem, stack, amount, entity, onBroken);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Unable to access Tetra modular durability hook", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "Tetra modular durability hook failed", cause);
        }
    }

    private static Method findDamageItemImpl() {
        try {
            return IModularItem.class.getMethod(
                    "damageItemImpl",
                    ItemStack.class,
                    int.class,
                    LivingEntity.class,
                    Consumer.class);
        } catch (NoSuchMethodException ignored) {
            LOGGER.info(
                    "Tetra pre-6.10 detected; using legacy modular durability path");
            return null;
        }
    }
}
