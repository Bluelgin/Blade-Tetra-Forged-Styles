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
        return new CustomStyleSpec("Unnamed Style", draft.actions);
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
    }

    private StyleRecordingSession() {}
}
