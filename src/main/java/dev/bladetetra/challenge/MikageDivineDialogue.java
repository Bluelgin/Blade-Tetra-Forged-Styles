package dev.bladetetra.challenge;

import dev.bladetetra.forging.DeadThoughtDivineImprinting;
import dev.bladetetra.network.MikageVisitorDialoguePacket;
import dev.bladetetra.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * Mikage's postgame Divine-Domain conversation branch.
 *
 * <p>Kept separate from map/combat code so story wording can be edited without
 * touching the ritual state machine. Text is intentionally centralized here while
 * the rest of Mikage's visitor conversation continues to use lang keys.</p>
 */
public final class MikageDivineDialogue {
    private static final String NODE = "blade_tetra_mikage_divine_dialogue_node";
    private static final String SEAL_EARNED = "blade_tetra_dead_thought_divine_seal";

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
            player.getPersistentData().remove(NODE);
            DivineDomainManager.tryEnterFromVisitor(player);
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
        } else if (persisted.getBoolean("blade_tetra_divine_domain_cleared")) {
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
                    "我守的从来不是这间屋子。", "neutral");
            case "divine_intro2" -> next(
                    "界门之后，是神域。那里原本让刀下之魂归于寂静。", "distant");
            case "divine_intro3" -> next(
                    "只是如今，积下来的残念已经把那里染坏了。我守着门，也是在阻止门后的东西出来。", "serious");
            case "divine_intro4" -> end(
                    "门后的东西并不认识你。它们认识的，是你手中的刀。若仍要过去，就让那把刀替你回答。",
                    "serious");

            case "divine_return1" -> next(
                    "……你回来了。看来，那些声音没有把你留下。", "soft");
            case "divine_return2" -> end(
                    "神域已经记住你的刀了。若还想听见更深处的回响，就再去一次。只是下一次，它们不会比刚才更安静。",
                    "distant");

            case "divine_seal1" -> next(
                    "……你回来了。看来，那些声音没有把你留下。", "soft");
            case "divine_seal2" -> next(
                    "你把那个也带回来了么。别误会——那还不是一式刀法。", "serious");
            case "divine_seal3" -> next(
                    "只是那些亡魂承认了：你的刀，有资格触碰『命』。", "distant");
            case "divine_seal4" -> next(
                    "你以为那是招式？不。那只是『命』被斩过以后，留下的伤痕。", "serious");
            case "divine_seal5" -> end(
                    "若你真想把它刻进自己的刀里……把刀置于锻台上的挂刀台，握着残印，让你的刀自己记住那道伤痕。",
                    "soft");

            case "divine_complete1" -> next(
                    "原来如此。", "neutral");
            case "divine_complete2" -> next(
                    "你最终还是让那道残念，成为了自己的刀。", "soft");
            case "divine_complete3" -> end(
                    "……那就别忘了，它为什么叫做『死念』。", "distant");
            default -> end("……", "neutral");
        };
    }

    private static DialogueLine next(String text, String expression) {
        return new DialogueLine(text, expression,
                List.of(new MikageVisitorDialoguePacket.Option("divine_next", "继续")));
    }

    private static DialogueLine end(String text, String expression) {
        return new DialogueLine(text, expression, List.of(
                new MikageVisitorDialoguePacket.Option("divine_enter", "进入神域"),
                new MikageVisitorDialoguePacket.Option("divine_back", "返回")));
    }

    private record DialogueLine(String text, String expression,
            List<MikageVisitorDialoguePacket.Option> options) {
    }

    private MikageDivineDialogue() {
    }
}
