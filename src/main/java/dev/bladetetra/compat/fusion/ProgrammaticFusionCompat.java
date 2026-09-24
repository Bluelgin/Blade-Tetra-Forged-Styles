package dev.bladetetra.compat.fusion;

import net.minecraftforge.fml.ModList;

/**
 * Loads exact third-party Slash Art dictionaries only when their owning mod is
 * present. Dictionary classes never reference third-party Java types: they only
 * register resource IDs, semantic fallback profiles, and presentation policy
 * metadata, keeping every integration optional and classloader-safe. Known IDs
 * can execute through registry-only soft overlap without linking add-on types.
 */
public final class ProgrammaticFusionCompat {
    public static void registerLoadedAddons() {
        ModList mods = ModList.get();
        if (mods.isLoaded(SjapFusionProfiles.MOD_ID)) {
            SjapFusionProfiles.register();
        }
        if (mods.isLoaded(YakumoFusionProfiles.MOD_ID)) {
            YakumoFusionProfiles.register();
        }
        if (mods.isLoaded(LastSmithFusionProfiles.MOD_ID)) {
            LastSmithFusionProfiles.register();
        }
        if (mods.isLoaded(RecastingFusionProfiles.MOD_ID)) {
            RecastingFusionProfiles.register();
        }

        // CialloBlade and HF Blade use SlashBlade-native Slash Arts for their
        // named blades, so the native dictionary already covers them without an
        // add-on-specific registration layer.
    }

    private ProgrammaticFusionCompat() {
    }
}
