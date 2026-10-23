package dev.bladetetra.customstyle;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only, bounded draft; a committed style is carried by a native Tetra scroll. */
public final class StyleRecordingSession {
    private static final Map<UUID, Draft> DRAFTS = new HashMap<>();

    public enum CaptureResult { ADDED, NO_RECENT_ACTION, ALREADY_CAPTURED, FULL }

    public static CaptureResult capture(ServerPlayer player) {
        RecentComboTracker.LastAction last = RecentComboTracker.mostRecent(player);
        if (last == null || !ComboCompatibilityResolver.playable(last.id())) return CaptureResult.NO_RECENT_ACTION;
        Draft draft = DRAFTS.computeIfAbsent(player.getUUID(), ignored -> new Draft());
        if (draft.sequence == last.sequence()) return CaptureResult.ALREADY_CAPTURED;
        if (draft.actions.size() >= CustomStyleSpec.MAX_ACTIONS) return CaptureResult.FULL;
        draft.actions.add(last.id());
        draft.sequence = last.sequence();
        return CaptureResult.ADDED;
    }

    public static CustomStyleSpec finish(ServerPlayer player) {
        Draft draft = DRAFTS.remove(player.getUUID());
        if (draft == null || draft.actions.isEmpty()) return null;
        return new CustomStyleSpec(draft.name, draft.actions);
    }

    /** Immutable state for future screens and synchronized read-only previews. */
    public record Snapshot(String name, List<ResourceLocation> actions, int maximum) {}

    public static Snapshot snapshot(ServerPlayer player) {
        Draft draft = DRAFTS.get(player.getUUID());
        return draft == null
                ? new Snapshot("Unnamed Style", List.of(), CustomStyleSpec.MAX_ACTIONS)
                : new Snapshot(draft.name, List.copyOf(draft.actions), CustomStyleSpec.MAX_ACTIONS);
    }

    public static boolean rename(ServerPlayer player, String name) {
        if (name == null || name.isBlank()
                || name.length() > CustomStyleSpec.MAX_NAME_LENGTH
                || name.chars().anyMatch(Character::isISOControl)) return false;
        DRAFTS.computeIfAbsent(player.getUUID(), ignored -> new Draft()).name = name.strip();
        return true;
    }

    /** Edits only previously captured positions; never creates arbitrary moves. */
    public static boolean move(ServerPlayer player, int index, int offset) {
        Draft draft = DRAFTS.get(player.getUUID());
        if (draft == null || (offset != -1 && offset != 1)
                || index < 0 || index >= draft.actions.size()
                || index + offset < 0 || index + offset >= draft.actions.size()) {
            return false;
        }
        java.util.Collections.swap(draft.actions, index, index + offset);
        return true;
    }

    public static boolean remove(ServerPlayer player, int index) {
        Draft draft = DRAFTS.get(player.getUUID());
        if (draft == null || index < 0 || index >= draft.actions.size()) return false;
        draft.actions.remove(index);
        return true;
    }

    public static void clear(ServerPlayer player) {
        DRAFTS.remove(player.getUUID());
    }

    public static int count(ServerPlayer player) {
        Draft draft = DRAFTS.get(player.getUUID());
        return draft == null ? 0 : draft.actions.size();
    }

    public static void undo(ServerPlayer player) {
        Draft draft = DRAFTS.get(player.getUUID());
        if (draft != null && !draft.actions.isEmpty()) {
            draft.actions.remove(draft.actions.size() - 1);
        }
    }

    public static void discard(UUID id) {
        DRAFTS.remove(id);
    }

    private static final class Draft {
        private final List<ResourceLocation> actions = new ArrayList<>();
        private long sequence = -1;
        private String name = "Unnamed Style";
    }

    private StyleRecordingSession() {}
}
