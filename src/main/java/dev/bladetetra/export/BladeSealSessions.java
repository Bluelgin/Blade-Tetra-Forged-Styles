package dev.bladetetra.export;

import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.visual.MaterialAppearance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Single-use authorization bound to the currently held blade and the server session. */
final class BladeSealSessions {
    private static final int LIFETIME_TICKS = 20 * 180;
    private static final Map<ServerPlayer, Draft> DRAFTS = new WeakHashMap<>();

    private record Draft(UUID token, InteractionHand bladeHand, ItemStack blade,
                         ItemStack frozen, BladeExportSnapshot original,
                         BladeExportSnapshot exported, ItemStack exportedBlade,
                         boolean omitsAwakening, MaterialAppearance appearance, long expiresAt) {}

    static boolean open(ServerPlayer player, InteractionHand toolHand) {
        if (!BladeExportSnapshot.mayExport(player)) {
            DRAFTS.remove(player);
            player.sendSystemMessage(Component.translatable("blade_tetra.export.permission"));
            return false;
        }
        try {
            var preview = begin(player, toolHand);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), preview);
            return true;
        } catch (IllegalArgumentException | IllegalStateException error) {
            player.sendSystemMessage(Component.translatable("blade_tetra.export.blocked", error.getMessage()));
            return false;
        }
    }

    static BladeSealPackets.Preview begin(ServerPlayer player, InteractionHand toolHand) {
        DRAFTS.remove(player);
        if (!(player.getItemInHand(toolHand).getItem() instanceof BladeSealItem)) {
            throw new IllegalArgumentException("Hold the seal and the blade in opposite hands");
        }
        var bladeHand = other(toolHand);
        var blade = player.getItemInHand(bladeHand);
        var snapshot = BladeExportSnapshot.capture(player, bladeHand);
        var projection = BladeAwakeningExportPolicy.project(snapshot, blade);
        var report = BladeExportPreflight.inspect(projection.snapshot(), projection.blade());
        UUID token = UUID.randomUUID();
        // No authorization token is stored when the current build is known to be non-portable.
        if (report.ready()) {
            DRAFTS.put(player, new Draft(token, bladeHand, blade, blade.copy(), snapshot,
                    projection.snapshot(), projection.blade(), projection.omitsAwakening(),
                    MaterialAppearance.fromStack(blade), player.server.getTickCount() + LIFETIME_TICKS));
        }
        return new BladeSealPackets.Preview(token, blade.getHoverName().copy(),
                report.ready(), report.summary(), projection.omitsAwakening());
    }

    static void confirm(ServerPlayer player, UUID token, boolean acknowledgeAwakeningLoss) {
        String encoded = "";
        String artTag = "";
        try {
            var snapshot = takeConfirmed(player, token, acknowledgeAwakeningLoss);
            if (snapshot != null) {
                var tag = snapshot.blade().getTag();
                artTag = tag == null ? "{}" : tag.toString();
                if (artTag.length() > BladeSealPackets.MAX_APPEARANCE_TAG) {
                    throw new IllegalArgumentException("Blade appearance data exceeds the export limit");
                }
                encoded = BladeSealPackets.encodeSnapshot(snapshot.data());
            }
        } catch (IllegalArgumentException | IllegalStateException expired) {
            artTag = "";
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BladeSealPackets.Result(token, encoded, artTag));
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
                || player.getItemInHand(draft.bladeHand()) != draft.blade()
                || !ItemStack.matches(draft.frozen(), draft.blade())
                || !(player.getItemInHand(other(draft.bladeHand())).getItem() instanceof BladeSealItem)) {
            return null;
        }
        var current = BladeExportSnapshot.capture(player, draft.bladeHand());
        return draft.original().equals(current)
                && draft.appearance().equals(MaterialAppearance.fromStack(draft.blade()))
                ? new Approved(draft.exported(), draft.exportedBlade().copy()) : null;
    }

    private static InteractionHand other(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private BladeSealSessions() {}
}
