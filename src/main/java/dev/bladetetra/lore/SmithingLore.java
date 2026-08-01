package dev.bladetetra.lore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.advancements.Advancement;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** Player-persistent discovery state for the smithing journal. */
public final class SmithingLore {
    public static final String SHOSHIN = "shoshin";
    public static final String BAIREN = "bairen";
    public static final String BANSHO = "bansho";
    public static final String RAIKIRI = "raikiri";
    public static final String AKATSUKI = "akatsuki";
    public static final String KYOUKA = "kyouka";
    public static final String SENBONZAKURA = "senbonzakura";
    public static final String NBT_SAGE = "nbt_sage";
    public static final List<String> CLUES = List.of(
            SHOSHIN, BAIREN, BANSHO, RAIKIRI, AKATSUKI, KYOUKA,
            SENBONZAKURA);

    private static final String ROOT = "BladeTetraSmithingLore";
    private static final String NOTICE_MASK = "CompletionNoticeMask";

    public static boolean isValid(String clue) {
        return CLUES.contains(clue);
    }

    public static boolean knows(Player player, String clue) {
        return loreTag(player).getBoolean(clue);
    }

    public static boolean discover(Player player, String clue) {
        if (!isValid(clue) || knows(player, clue)) {
            return false;
        }
        loreTag(player).putBoolean(clue, true);
        return true;
    }

    public static int discoveredCount(Player player) {
        return (int) CLUES.stream().filter(clue -> knows(player, clue)).count();
    }

    public static boolean completed(ServerPlayer player, String clue) {
        if (!isValid(clue)) return false;
        Advancement advancement = player.server.getAdvancements().getAdvancement(
                ResourceLocation.fromNamespaceAndPath("blade_tetra", clue));
        return advancement != null
                && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    public static int completionMask(ServerPlayer player) {
        int mask = 0;
        for (int i = 0; i < CLUES.size(); i++) {
            if (completed(player, CLUES.get(i))) mask |= 1 << i;
        }
        return mask;
    }

    public static boolean nbtSageCompleted(ServerPlayer player) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(
                ResourceLocation.fromNamespaceAndPath("blade_tetra", NBT_SAGE));
        return advancement != null
                && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** Returns newly completed entries after the journal has established a baseline. */
    public static int consumeCompletionNotices(ServerPlayer player, int currentMask) {
        CompoundTag lore = loreTag(player);
        if (!lore.contains(NOTICE_MASK)) {
            lore.putInt(NOTICE_MASK, currentMask);
            return 0;
        }
        int previous = lore.getInt(NOTICE_MASK);
        int discovered = currentMask & ~previous;
        lore.putInt(NOTICE_MASK, previous | currentMask);
        return discovered;
    }

    private static CompoundTag loreTag(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(
                Player.PERSISTED_NBT_TAG);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        CompoundTag lore = persisted.getCompound(ROOT);
        persisted.put(ROOT, lore);
        return lore;
    }

    private SmithingLore() {
    }
}
