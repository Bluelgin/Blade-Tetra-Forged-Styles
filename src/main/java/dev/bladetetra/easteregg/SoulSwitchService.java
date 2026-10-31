package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.kyouka.KyoukaMirrorSessions;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.network.SoulSwitchPacket;
import dev.bladetetra.network.SoulSwitchReply;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SoulSwitchService {
    private static final String NEXT = "blade_tetra_next_soul_switch";
    private static final String REQUEST_AT = "blade_tetra_soul_request_at";

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)
                || !AwakenedBladeCombat.replacesLegacy() || !SoulSkillState.fusion(p.getMainHandItem())) return;
        var tag = p.getMainHandItem().getOrCreateTag();
        if (!tag.hasUUID(SoulSkillState.BLADE_ID)) {
            tag.putUUID(SoulSkillState.BLADE_ID, UUID.randomUUID());
            p.containerMenu.broadcastChanges();
        }
    }

    public static void request(ServerPlayer p, SoulSwitchPacket request) {
        if (!request.valid() || !AwakenedBladeCombat.replacesLegacy() || !p.isAlive()
                || p.isSpectator() || p.containerMenu != p.inventoryMenu
                || p.getInventory().selected != request.slot()) return;
        var blade = p.getMainHandItem();
        var tag = blade.getTag();
        if (!SoulSkillState.fusion(blade) || tag == null || !tag.hasUUID(SoulSkillState.BLADE_ID)
                || !request.blade().equals(tag.getUUID(SoulSkillState.BLADE_ID))) return;
        var current = SoulSkillState.selected(blade);
        if (request.previous() != current.ordinal()) return;
        long now = p.getServer().overworld().getGameTime();
        var playerTag = p.getPersistentData();
        // Bound failed feedback too; malformed or repeated requests cannot flood other players with VFX.
        if (playerTag.contains(REQUEST_AT) && now < playerTag.getLong(REQUEST_AT) + 4) return;
        playerTag.putLong(REQUEST_AT, now);
        if (now < playerTag.getLong(NEXT) || now < tag.getLong(NEXT)) {
            reply(p, request, current.ordinal(), SoulSwitchReply.COOLDOWN); return;
        }
        var next = SoulLegacyState.inscribed(blade).stream().filter(s -> s != current).findFirst().orElse(current);
        if (!SoulLegacyState.isActive(blade, next)) {
            reply(p, request, current.ordinal(), SoulSwitchReply.DORMANT); return;
        }
        if (AkatsukiExecution.switchBlocked(p) || SakuraInfiniteDanceSA.phase(p) != SakuraInfiniteDanceSA.Phase.IDLE
                || KyoukaMirrorSessions.switchBlocked(p) || SakuraShardAttackService.switchBlocked(p)) {
            reply(p, request, current.ordinal(), SoulSwitchReply.BUSY); return;
        }
        // Existing controllers own cleanup and cooldowns; selection never resets native Rank or combo state.
        KyoukaMirrorSessions.releaseForSwitch(p);
        RaikiriActionObserver.clear(p.getUUID(), p.getServer());
        tag.putInt(SoulSkillState.SELECTED, next.ordinal());
        tag.putLong(NEXT, now + SoulSkillState.SWITCH_TICKS);
        playerTag.putLong(NEXT, now + SoulSkillState.SWITCH_TICKS);
        p.containerMenu.broadcastChanges();
        reply(p, request, next.ordinal(), SoulSwitchReply.SUCCESS);
        var color = switch (next) {
            case AKATSUKI -> new Vector3f(.92F, .16F, .32F);
            case KYOUKA -> new Vector3f(.48F, .88F, 1F);
            case SENBONZAKURA -> new Vector3f(1F, .55F, .75F);
            default -> new Vector3f(.64F, .56F, 1F);
        };
        var at = p.getEyePosition().add(p.getLookAngle().scale(.55)).add(0, -.45, 0);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            p.serverLevel().sendParticles(new DustParticleOptions(color, .7F),
                    at.x + Math.cos(angle) * .22, at.y + Math.sin(angle) * .22, at.z,
                    1, 0, 0, 0, 0);
        }
        var particle = switch (next) {
            case AKATSUKI -> ParticleTypes.CRIMSON_SPORE;
            case KYOUKA -> ParticleTypes.END_ROD;
            case SENBONZAKURA -> ParticleTypes.CHERRY_LEAVES;
            default -> ParticleTypes.ELECTRIC_SPARK;
        };
        p.serverLevel().sendParticles(particle, at.x, at.y, at.z, 4, .12, .12, .12, .015);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, .35F, .85F + next.ordinal() * .12F);
    }

    private static void reply(ServerPlayer p, SoulSwitchPacket request, int soul, int result) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new SoulSwitchReply(request.slot(), request.blade(), p.level().dimension().location(), soul, result));
    }
    private SoulSwitchService() {}
}
