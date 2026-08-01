package dev.bladetetra.compat;

import dev.bladetetra.config.GameplayConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/** Optional reflection bridge to Contract Blade Core (maid_weapon). */
public final class ContractBladeCompat {
    private static final String LEGACY_API_CLASS =
            "com.maidweapon.forge.api.EmbeddedSpiritApi";
    private static final String INTRINSIC_API_CLASS =
            "com.maidweapon.forge.api.IntrinsicSpiritApi";
    private static final String AKATSUKI_ID = "blade_tetra:akatsuki";
    private static final String LEGACY_AKATSUKI_MODEL_ID = "maid_weapon:akatsuki";

    private static Method bindMethod;
    private static Method dormantMethod;
    private static Method boundMethod;
    private static Method ensureIntrinsicMethod;
    private static Method activateIntrinsicMethod;
    private static Method hasIntrinsicMethod;
    private static Method clearLegacyIntrinsicModelMethod;
    private static boolean lookupDone;

    public static boolean isLoaded() {
        return ModList.get().isLoaded("maid_weapon");
    }

    public static boolean bindAkatsuki(Player owner, ItemStack blade) {
        return syncAkatsuki(owner, blade, true);
    }

    /** Legacy-only helper retained for older Contract Blade Core builds. */
    public static void setDormant(ItemStack blade, boolean dormant) {
        if (!resolve() || dormantMethod == null) return;
        try {
            dormantMethod.invoke(null, blade, dormant);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public static boolean hasAkatsuki(ItemStack blade) {
        if (!resolve()) return false;
        try {
            if (hasIntrinsicMethod != null) {
                return (Boolean) hasIntrinsicMethod.invoke(
                        null, blade, AKATSUKI_ID);
            }
            return boundMethod != null && (Boolean) boundMethod.invoke(
                    null, blade, AKATSUKI_ID);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    /** Selects Akatsuki's intrinsic channel, with a dormant legacy fallback. */
    public static boolean syncAkatsuki(
            Player owner,
            ItemStack blade,
            boolean active) {
        if (!resolve()) return false;
        try {
            if (ensureIntrinsicMethod != null && activateIntrinsicMethod != null) {
                boolean ensured = (Boolean) ensureIntrinsicMethod.invoke(
                        null, owner, blade, AKATSUKI_ID, "赤月");
                if (ensured && clearLegacyIntrinsicModelMethod != null) {
                    clearLegacyIntrinsicModelMethod.invoke(
                            null, owner, blade, AKATSUKI_ID,
                            LEGACY_AKATSUKI_MODEL_ID);
                }
                return ensured && (Boolean) activateIntrinsicMethod.invoke(
                        null, owner, blade, AKATSUKI_ID, active);
            }

            boolean bound = boundMethod != null && (Boolean) boundMethod.invoke(
                    null, blade, AKATSUKI_ID);
            if (active && !bound && bindMethod != null) {
                bound = (Boolean) bindMethod.invoke(
                        null, owner, blade, AKATSUKI_ID, "赤月");
            }
            if (bound && dormantMethod != null) {
                dormantMethod.invoke(null, blade, !active);
            }
            return bound;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static boolean resolve() {
        if (!GameplayConfig.ENABLE_CONTRACT_BLADE_INTEGRATION.get()) return false;
        if (lookupDone) return bindMethod != null || ensureIntrinsicMethod != null;
        lookupDone = true;
        if (!isLoaded()) return false;
        try {
            Class<?> legacyApi = Class.forName(
                    LEGACY_API_CLASS, false,
                    ContractBladeCompat.class.getClassLoader());
            bindMethod = legacyApi.getMethod(
                    "bindPresetSpirit", Player.class, ItemStack.class,
                    String.class, String.class);
            dormantMethod = legacyApi.getMethod(
                    "setDormant", ItemStack.class, boolean.class);
            boundMethod = legacyApi.getMethod(
                    "isSpirit", ItemStack.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            bindMethod = null;
            dormantMethod = null;
            boundMethod = null;
        }

        try {
            Class<?> intrinsicApi = Class.forName(
                    INTRINSIC_API_CLASS, false,
                    ContractBladeCompat.class.getClassLoader());
            ensureIntrinsicMethod = intrinsicApi.getMethod(
                    "ensureIntrinsicSpirit", Player.class, ItemStack.class,
                    String.class, String.class);
            activateIntrinsicMethod = intrinsicApi.getMethod(
                    "setIntrinsicSpiritActive", Player.class, ItemStack.class,
                    String.class, boolean.class);
            hasIntrinsicMethod = intrinsicApi.getMethod(
                    "hasIntrinsicSpirit", ItemStack.class, String.class);
            clearLegacyIntrinsicModelMethod = intrinsicApi.getMethod(
                    "clearIntrinsicSpiritModelIfEquals",
                    Player.class, ItemStack.class,
                    String.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            ensureIntrinsicMethod = null;
            activateIntrinsicMethod = null;
            hasIntrinsicMethod = null;
            clearLegacyIntrinsicModelMethod = null;
        }
        return bindMethod != null || ensureIntrinsicMethod != null;
    }

    private ContractBladeCompat() {
    }
}
