package dev.bladetetra.network;

import dev.bladetetra.customstyle.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Bounded exchange requests and server-produced diagnostics. No client-supplied permissions. */
public final class CustomStyleExchangePacket {
    public enum Mode { PREVIEW, IMPORT, EXPORT_DRAFT, EXPORT_SCROLL }
    public record Request(int requestId, Mode mode, int expectedDraft, String code) {
        public static void encode(Request p, FriendlyByteBuf b) {
            b.writeInt(p.requestId); b.writeEnum(p.mode); b.writeInt(p.expectedDraft);
            b.writeUtf(p.code, CustomStyleExchange.MAX_CODE_LENGTH);
        }
        public static Request decode(FriendlyByteBuf b) {
            return new Request(b.readInt(), b.readEnum(Mode.class), b.readInt(), b.readUtf(CustomStyleExchange.MAX_CODE_LENGTH));
        }
        public static void handle(Request p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get(); var player = context.getSender();
            if (player != null) context.enqueueWork(() -> {
                if (!player.isAlive()) return;
                CustomStyleDependencyResolver.Preview preview = null;
                String error = "", code = "";
                if (p.mode == Mode.PREVIEW || p.mode == Mode.IMPORT) {
                    preview = CustomStyleDependencyResolver.inspect(p.code);
                    if (p.mode == Mode.IMPORT) {
                        if (p.expectedDraft != StyleRecordingSession.snapshot(player).hashCode()) error = "draft_changed";
                        else if (!preview.allowed() || !StyleRecordingSession.importCode(player, p.code)) error = "rejected";
                        else { CustomStyleDraftSyncPacket.send(player, false); }
                    }
                } else {
                    CustomStyleSpec spec = p.mode == Mode.EXPORT_DRAFT ? StyleRecordingSession.exportable(player)
                            : CustomStyleScrolls.read(player.getMainHandItem());
                    if (p.mode == Mode.EXPORT_SCROLL && spec == null) spec = CustomStyleScrolls.read(player.getOffhandItem());
                    if (!ComboCompatibilityResolver.playable(spec)) error = p.mode == Mode.EXPORT_SCROLL ? "hold_scroll" : "rejected";
                    else {
                        code = CustomStyleExchange.exportWithDependencies(spec);
                        preview = CustomStyleDependencyResolver.inspect(code);
                    }
                }
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new Reply(p.requestId, p.mode, error, code, preview));
            });
            context.setPacketHandled(true);
        }
    }
    public record Reply(int requestId, Mode mode, String error, String code, CustomStyleDependencyResolver.Preview preview) {
        public static void encode(Reply p, FriendlyByteBuf b) {
            b.writeInt(p.requestId); b.writeEnum(p.mode); b.writeUtf(p.error, 64); b.writeUtf(p.code, CustomStyleExchange.MAX_CODE_LENGTH);
            b.writeBoolean(p.preview != null);
            if (p.preview == null) return;
            b.writeEnum(p.preview.formatStatus()); b.writeBoolean(p.preview.style() != null);
            if (p.preview.style() == null) return;
            b.writeUtf(p.preview.style().name(), CustomStyleSpec.MAX_NAME_LENGTH);
            b.writeVarInt(p.preview.actions().size());
            for (var info : p.preview.actions()) {
                b.writeUtf(info.action().toString(), 128); b.writeUtf(info.modId(), 64); b.writeUtf(info.provider(), 96);
                b.writeUtf(info.installedVersion(), 96); b.writeUtf(info.requiredVersion(), 96); b.writeEnum(info.status());
            }
            b.writeVarInt(p.preview.missingDeclaredMods().size());
            p.preview.missingDeclaredMods().forEach(id -> b.writeUtf(id, 64));
        }
        public static Reply decode(FriendlyByteBuf b) {
            int id = b.readInt(); Mode mode = b.readEnum(Mode.class); String error = b.readUtf(64), code = b.readUtf(CustomStyleExchange.MAX_CODE_LENGTH);
            CustomStyleDependencyResolver.Preview preview = null;
            if (b.readBoolean()) {
                var format = b.readEnum(CustomStyleExchange.Status.class);
                if (!b.readBoolean()) preview = new CustomStyleDependencyResolver.Preview(format, null, List.of(), List.of());
                else {
                    String name = b.readUtf(CustomStyleSpec.MAX_NAME_LENGTH); int size = bounded(b.readVarInt(), 6);
                    List<CustomStyleDependencyResolver.ActionInfo> actions = new ArrayList<>();
                    for (int i = 0; i < size; i++) actions.add(new CustomStyleDependencyResolver.ActionInfo(
                            new ResourceLocation(b.readUtf(128)), b.readUtf(64), b.readUtf(96), b.readUtf(96), b.readUtf(96),
                            b.readEnum(CustomStyleDependencyResolver.Status.class)));
                    int missingCount = bounded(b.readVarInt(), 12); List<String> missing = new ArrayList<>();
                    for (int i = 0; i < missingCount; i++) missing.add(b.readUtf(64));
                    preview = new CustomStyleDependencyResolver.Preview(format,
                            new CustomStyleSpec(name, actions.stream().map(CustomStyleDependencyResolver.ActionInfo::action).toList()), List.copyOf(actions), List.copyOf(missing));
                }
            }
            return new Reply(id, mode, error, code, preview);
        }
        public static void handle(Reply p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.bladetetra.client.CustomStyleExchangeScreen.accept(p)));
            context.setPacketHandled(true);
        }
    }
    private static int bounded(int count, int maximum) {
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid exchange count");
        return count;
    }
    private CustomStyleExchangePacket() {}
}
