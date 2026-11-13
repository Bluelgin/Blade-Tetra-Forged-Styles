package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.DeadThoughtDivineImprinting;
import dev.bladetetra.network.MikageVisitorDialoguePacket;
import dev.bladetetra.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * Mikage's postgame Divine-Domain conversation branch.
 *
 * <p>Kept separate from map/combat code so story wording can be edited without
 * touching the ritual state machine. The client resolves dialogue and option keys.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageDivineDialogue {
    private static final String NODE = "blade_tetra_mikage_divine_dialogue_node";
    private static final String DIVINE_CHALLENGE = "blade_tetra_divine_challenge";
    private static final String SEAL_EARNED = "blade_tetra_dead_thought_divine_seal";
    private static final String DOMAIN_CLEARED = "blade_tetra_divine_domain_cleared";

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getFrom().equals(DivineDomainManager.DIVINE_REALM)
                || !event.getTo().equals(ChallengeManager.MIRROR_REALM)) {
            return;
        }
        if (player.getPersistentData().getBoolean(DivineDomainProtectionEvents.FAILED_RETURN)) {
            player.getPersistentData().remove(DivineDomainProtectionEvents.FAILED_RETURN);
            return;
        }
        // Only the explicit successful-return path restores the visitor challenge
        // and clears the Divine-Domain marker before teleporting. Commands or
        // third-party teleporters must not accidentally unlock the return dialogue.
        if (!player.getPersistentData().contains(ChallengeManager.PLAYER_CHALLENGE)
                || player.getPersistentData().contains(DIVINE_CHALLENGE)) {
            return;
        }
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(DOMAIN_CLEARED, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static boolean handleChoice(ServerPlayer player, String choice) {
        if (!choice.startsWith("divine_")) {
            return false;
        }
        if (choice.equals("divine_lore")) {
            open(player);
            return true;
        }

        String current = player.getPersistentData().getString(NODE);
        String next = switch (current) {
            case "divine_intro1" -> choice.equals("divine_next") ? "divine_intro2" : null;
            case "divine_intro2" -> choice.equals("divine_next") ? "divine_intro3" : null;
            case "divine_intro3" -> choice.equals("divine_next") ? "divine_intro4" : null;
            case "divine_return1" -> choice.equals("divine_next") ? "divine_return2" : null;
            case "divine_seal1" -> choice.equals("divine_next") ? "divine_seal2" : null;
            case "divine_seal2" -> choice.equals("divine_next") ? "divine_seal3" : null;
            case "divine_seal3" -> choice.equals("divine_next") ? "divine_seal4" : null;
            case "divine_seal4" -> choice.equals("divine_next") ? "divine_seal5" : null;
            case "divine_complete1" -> choice.equals("divine_next") ? "divine_complete2" : null;
            case "divine_complete2" -> choice.equals("divine_next") ? "divine_complete3" : null;
            default -> null;
        };
        if (next != null) {
            send(player, next);
            return true;
        }

        if (choice.equals("divine_enter") && canEnterFrom(current)) {
            DivineDomainManager.tryEnterFromVisitor(player);
            // Failed preconditions leave the current dialogue usable so the player
            // can retry or back out instead of being stranded with an empty NODE.
            if (player.level().dimension().equals(DivineDomainManager.DIVINE_REALM)
                    && player.getPersistentData().contains(DIVINE_CHALLENGE)) {
                player.getPersistentData().remove(NODE);
            }
            return true;
        }
        if (choice.equals("divine_back") && canLeaveDialogueFrom(current)) {
            player.getPersistentData().remove(NODE);
            DivineDomainManager.backToTopics(player);
            return true;
        }
        return true;
    }

    public static void open(ServerPlayer player) {
        if (!player.getPersistentData().contains(ChallengeManager.PLAYER_CHALLENGE)
                || !player.level().dimension().equals(ChallengeManager.MIRROR_REALM)) {
            return;
        }
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.getBoolean("blade_tetra_mikage_cleared")) {
            return;
        }
        if (persisted.getBoolean(DeadThoughtDivineImprinting.PLAYER_BOUND)) {
            send(player, "divine_complete1");
        } else if (persisted.getBoolean(SEAL_EARNED)) {
            send(player, "divine_seal1");
        } else if (persisted.getBoolean(DOMAIN_CLEARED)) {
            send(player, "divine_return1");
        } else {
            send(player, "divine_intro1");
        }
    }

    private static boolean canEnterFrom(String node) {
        return "divine_intro4".equals(node)
                || "divine_return2".equals(node)
                || "divine_seal5".equals(node)
                || "divine_complete3".equals(node);
    }

    private static boolean canLeaveDialogueFrom(String node) {
        return canEnterFrom(node);
    }

    private static void send(ServerPlayer player, String node) {
        player.getPersistentData().putString(NODE, node);
        DialogueLine line = line(node);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MikageVisitorDialoguePacket(node, line.text, "", line.expression,
                        line.options));
    }

    private static DialogueLine line(String node) {
        return switch (node) {
            case "divine_intro1" -> next(
                    "screen.blade_tetra.divine.line.intro1", "neutral");
            case "divine_intro2" -> next(
                    "screen.blade_tetra.divine.line.intro2", "distant");
            case "divine_intro3" -> next(
                    "screen.blade_tetra.divine.line.intro3", "serious");
            case "divine_intro4" -> end(
                    "screen.blade_tetra.divine.line.intro4",
                    "serious");

            case "divine_return1" -> next(
                    "screen.blade_tetra.divine.line.return1", "soft");
            case "divine_return2" -> end(
                    "screen.blade_tetra.divine.line.return2",
                    "distant");

            case "divine_seal1" -> next(
                    "screen.blade_tetra.divine.line.seal1", "soft");
            case "divine_seal2" -> next(
                    "screen.blade_tetra.divine.line.seal2", "serious");
            case "divine_seal3" -> next(
                    "screen.blade_tetra.divine.line.seal3", "distant");
            case "divine_seal4" -> next(
                    "screen.blade_tetra.divine.line.seal4", "serious");
            case "divine_seal5" -> end(
                    "screen.blade_tetra.divine.line.seal5",
                    "soft");

            case "divine_complete1" -> next(
                    "screen.blade_tetra.divine.line.complete1", "neutral");
            case "divine_complete2" -> next(
                    "screen.blade_tetra.divine.line.complete2", "soft");
            case "divine_complete3" -> end(
                    "screen.blade_tetra.divine.line.complete3", "distant");
            default -> end("screen.blade_tetra.divine.line.empty", "neutral");
        };
    }

    private static DialogueLine next(String text, String expression) {
        return new DialogueLine(text, expression,
                List.of(new MikageVisitorDialoguePacket.Option("divine_next", "screen.blade_tetra.divine.next")));
    }

    private static DialogueLine end(String text, String expression) {
        return new DialogueLine(text, expression, List.of(
                new MikageVisitorDialoguePacket.Option(
                        "divine_enter", DivineDomainWipNotice.entryLabel()),
                new MikageVisitorDialoguePacket.Option("divine_back", "screen.blade_tetra.divine.back")));
    }

    private record DialogueLine(String text, String expression,
            List<MikageVisitorDialoguePacket.Option> options) {
    }

    private MikageDivineDialogue() {
    }
}
