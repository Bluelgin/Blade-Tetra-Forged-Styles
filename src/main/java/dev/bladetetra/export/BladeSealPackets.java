package dev.bladetetra.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class BladeSealPackets {
    private static final int MAX_SNAPSHOT = 24_576;
    static final int MAX_APPEARANCE_TAG = 65_536;
    private static final Gson JSON = new GsonBuilder()
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer()).create();

    static String encodeSnapshot(BladeExportSnapshot snapshot) {
        String encoded = JSON.toJson(snapshot);
        if (encoded.length() > MAX_SNAPSHOT) throw new IllegalArgumentException("Blade export exceeds size limit");
        return encoded;
    }

    static BladeExportSnapshot decodeSnapshot(String data) {
        return JSON.fromJson(data, BladeExportSnapshot.class);
    }

    public record Preview(UUID token, Component title, boolean ready, String summary,
                          boolean omitsAwakening) {
        private static final int MAX_SUMMARY = 512;

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeComponent(title);
            buf.writeBoolean(ready);
            buf.writeUtf(summary, MAX_SUMMARY);
            buf.writeBoolean(omitsAwakening);
        }

        public static Preview decode(FriendlyByteBuf buf) {
            return new Preview(buf.readUUID(), buf.readComponent(), buf.readBoolean(),
                    buf.readUtf(MAX_SUMMARY), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> BladeSealScreen.open(this)));
            ctx.setPacketHandled(true);
        }
    }

    public record Confirm(UUID token, boolean acknowledgeAwakeningLoss) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeBoolean(acknowledgeAwakeningLoss);
        }

        public static Confirm decode(FriendlyByteBuf buf) {
            return new Confirm(buf.readUUID(), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> {
                if (ctx.getSender() != null) BladeSealSessions.confirm(ctx.getSender(), token,
                        acknowledgeAwakeningLoss);
            });
            ctx.setPacketHandled(true);
        }
    }

    public record Result(UUID token, String snapshot, String appearanceTag) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeUtf(snapshot, MAX_SNAPSHOT);
            buf.writeUtf(appearanceTag, MAX_APPEARANCE_TAG);
        }

        public static Result decode(FriendlyByteBuf buf) {
            return new Result(buf.readUUID(), buf.readUtf(MAX_SNAPSHOT), buf.readUtf(MAX_APPEARANCE_TAG));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> BladeSealScreen.receive(this)));
            ctx.setPacketHandled(true);
        }
    }

    private BladeSealPackets() {}
}
