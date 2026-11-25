package dev.bladetetra.compat;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class LegacyWorldMigrationSmoke {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) throws Exception {
        if (!Boolean.getBoolean("blade_tetra.legacyWorldMigrationSmoke")) return;
        var server = event.getServer();
        var overworld = server.overworld();
        for (String realm : List.of("mirror_realm", "divine_domain", "easter_realm")) {
            var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("blade_tetra", realm));
            ServerLevel retired = server.getLevel(key);
            require(retired != null, "Missing legacy dimension: " + realm);
            var courtyard = new BlockPos(34, 146, -48);
            retired.setBlockAndUpdate(courtyard, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            checkEntities(retired);
            checkItems();
            var player = new ServerPlayer(server, retired, new GameProfile(UUID.randomUUID(), "OldWorldTest"));
            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
            var savedPlayer = TagParser.parseTag("""
                    {ForgeData:{blade_tetra_challenge_id:14L,blade_tetra_easter_entry_ticket:1b,
                    blade_tetra_easter_pending_return:1b,blade_tetra_gate_lock_until:12L,
                    blade_tetra_divine_challenge:14L,blade_tetra_divine_origin_x:34,
                    blade_tetra_divine_origin_z:-48,blade_tetra_origin_dimension:"minecraft:overworld",
                    blade_tetra_origin_x:3.5d,blade_tetra_origin_y:90d,blade_tetra_origin_z:5.5d,
                    blade_tetra_origin_yaw:42f,blade_tetra_origin_pitch:12f,unrelated:"preserve"}}
                    """);
            player.getPersistentData().merge(savedPlayer.getCompound("ForgeData"));
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            retired.addNewPlayer(player);
            LegacyMikageCompatibility.recoverOnLogin(new PlayerEvent.PlayerLoggedInEvent(player));
            require(player.level() == overworld && player.getX() == 3.5 && player.getZ() == 5.5
                    && player.getYRot() == 42, "Saved origin was not restored: " + realm);
            require(retired.getBlockState(courtyard).is(Blocks.POLISHED_BLACKSTONE_BRICKS),
                    "Migration changed the courtyard: " + realm);
            CompoundTag preserved = player.getPersistentData().copy();
            require("preserve".equals(preserved.getString("unrelated"))
                    && !preserved.contains("blade_tetra_challenge_id")
                    && !preserved.contains("blade_tetra_divine_challenge")
                    && !preserved.contains("blade_tetra_origin_dimension")
                    && player.getInventory().getItem(0).is(Items.DIAMOND), "Incorrect selective NBT migration");
            LegacyMikageCompatibility.recover(player);
            require(preserved.equals(player.getPersistentData()) && player.getX() == 3.5, "Migration was not idempotent");
            player.discard();
        }
        server.saveEverything(true, true, true);
        LogUtils.getLogger().info("LEGACY_WORLD_MIGRATION_PASS");
        server.halt(false);
    }

    private static void checkEntities(ServerLevel level) throws Exception {
        for (String id : List.of("mikage", "mikage_echo", "mikage_phantom_sword", "mikage_divine_companion",
                "mikage_gate_core", "mikage_gate_sword")) {
            var tag = TagParser.parseTag("""
                    {id:"blade_tetra:%s",Pos:[34.5d,147.0d,-47.5d],Rotation:[0.0f,0.0f],
                    Health:80.0f,ForgeData:{blade_tetra_challenge:14L}}
                    """.formatted(id));
            Entity decoded = EntityType.create(tag, level).orElseThrow();
            require(decoded instanceof LegacyMikageCompatibility.RetiredEntity, "Unexpected entity for " + id);
            level.addLegacyChunkEntities(java.util.stream.Stream.of(decoded));
            require(level.getEntity(decoded.getUUID()) == null, "Retired entity became active: " + id);
        }
    }

    private static void checkItems() throws Exception {
        for (String id : List.of("boundary_gate_charm", "sword_ghost_remnant", "broken_oni_mask",
                "karmic_puppet", "dead_thought_soul_seal")) {
            var tag = TagParser.parseTag("{id:\"blade_tetra:" + id + "\",Count:1b,tag:{old_marker:42}}");
            var loaded = ItemStack.of(tag);
            require(!loaded.isEmpty() && loaded.save(new CompoundTag()).equals(tag), "Old item data changed: " + id);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private LegacyWorldMigrationSmoke() {}
}
