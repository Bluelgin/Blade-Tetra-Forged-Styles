package dev.bladetetra.export;

import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.visual.MaterialAppearance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Single-use authorization bound to the current workbench menu and its weapon. */
final class BladeSealSessions {
    private static final int LIFETIME_TICKS = 20 * 180;
    private static final Map<ServerPlayer, Draft> DRAFTS = new WeakHashMap<>();

    private record Draft(UUID token, BladeSealWorkbench.WorkbenchMenu menu, ItemStack blade,
                         ItemStack frozen, BladeExportSnapshot original,
                         BladeExportSnapshot exported, ItemStack exportedBlade,
                         boolean omitsAwakening, MaterialAppearance appearance, long expiresAt) {}

    static boolean open(ServerPlayer player, BladeSealWorkbench.WorkbenchMenu menu) {
        if (!BladeExportSnapshot.mayExport(player)) {
            DRAFTS.remove(player);
            player.sendSystemMessage(Component.translatable("blade_tetra.export.permission"));
            return false;
        }
        try {
            var preview = begin(player, menu);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), preview);
            return true;
        } catch (IllegalArgumentException | IllegalStateException error) {
            player.sendSystemMessage(Component.translatable("blade_tetra.export.blocked", error.getMessage()));
            return false;
        }
    }

    static BladeSealPackets.Preview begin(ServerPlayer player, BladeSealWorkbench.WorkbenchMenu menu) {
        DRAFTS.remove(player);
        if (!BladeExportSnapshot.mayExport(player) || menu != player.containerMenu || !menu.stillValid(player)) {
            throw new IllegalStateException("No eligible Blade Sealing Workbench is open");
        }
        var blade = menu.blade();
        var snapshot = BladeExportSnapshot.captureOwnedStack(blade.copy());
        var projection = BladeAwakeningExportPolicy.project(snapshot, blade);
        var report = BladeExportPreflight.inspect(projection.snapshot(), projection.blade());
        UUID token = UUID.randomUUID();
        if (!report.ready()) {
            return new BladeSealPackets.Preview(token, menu.containerId, blade.getHoverName().copy(),
                    false, report.summary(), projection.omitsAwakening(), "", "");
        }
        String encoded = BladeSealPackets.encodeSnapshot(projection.snapshot());
        String artTag = appearanceTag(projection.blade());
        DRAFTS.put(player, new Draft(token, menu, blade, blade.copy(), snapshot,
                    projection.snapshot(), projection.blade(), projection.omitsAwakening(),
                    MaterialAppearance.fromStack(blade), player.server.getTickCount() + LIFETIME_TICKS));
        return new BladeSealPackets.Preview(token, menu.containerId, blade.getHoverName().copy(),
                true, report.summary(), projection.omitsAwakening(), encoded, artTag);
    }

    static void confirm(ServerPlayer player, UUID token, boolean acknowledgeAwakeningLoss) {
        String encoded = "";
        String artTag = "";
        try {
            var snapshot = takeConfirmed(player, token, acknowledgeAwakeningLoss);
            if (snapshot != null) {
                artTag = appearanceTag(snapshot.blade());
                encoded = BladeSealPackets.encodeSnapshot(snapshot.data());
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            LogUtils.getLogger().warn("Could not encode approved Blade Seal snapshot", failure);
            artTag = "";
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BladeSealPackets.Result(token, encoded, artTag));
    }

    private static String appearanceTag(ItemStack blade) {
        var tag = blade.getTag();
        String encoded = tag == null ? "{}" : tag.toString();
        if (encoded.length() > BladeSealPackets.MAX_APPEARANCE_TAG) {
            throw new IllegalArgumentException("Blade appearance data exceeds the export limit");
        }
        return encoded;
    }

    record Approved(BladeExportSnapshot data, ItemStack blade) {}

    static Approved takeConfirmed(ServerPlayer player, UUID token) {
        return takeConfirmed(player, token, false);
    }

    static Approved takeConfirmed(ServerPlayer player, UUID token, boolean acknowledgeAwakeningLoss) {
        var draft = DRAFTS.get(player);
        if (draft == null || !draft.token().equals(token)) return null;
        // Declining awakening loss is not a valid confirmation; retain the draft
        // so the player can choose consent without having to re-open the tool.
        if (draft.omitsAwakening() && !acknowledgeAwakeningLoss) return null;
        DRAFTS.remove(player);
        if (!BladeExportSnapshot.mayExport(player) || draft.expiresAt() < player.server.getTickCount()
                || player.containerMenu != draft.menu() || !draft.menu().stillValid(player)
                || draft.menu().blade() != draft.blade() || !ItemStack.matches(draft.frozen(), draft.blade())) {
            return null;
        }
        var current = BladeExportSnapshot.captureOwnedStack(draft.blade().copy());
        return draft.original().equals(current)
                && draft.appearance().equals(MaterialAppearance.fromStack(draft.blade()))
                ? new Approved(draft.exported(), draft.exportedBlade().copy()) : null;
    }

    static void close(ServerPlayer player, BladeSealWorkbench.WorkbenchMenu menu) {
        var draft = DRAFTS.get(player);
        if (draft != null && draft.menu() == menu) DRAFTS.remove(player);
    }

    private BladeSealSessions() {}
}
