package net.dungeon_difficulty.mixin;

import net.dungeon_difficulty.Platform;
import net.dungeon_difficulty.config.ConfigServer;
import net.dungeon_difficulty.logic.DifficultyHandler;
import net.dungeon_difficulty.logic.ScalingGoal;
import net.dungeon_difficulty.util.LanguageUtil;
import net.dungeon_difficulty.logic.Difficulty;
import net.dungeon_difficulty.logic.PatternMatching;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
// import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Unique
    private static final int ANNOUNCEMENT_MEMORY = 2;

    @Inject(method = "tick", at = @At("TAIL"))
    private void pre_tick(CallbackInfo ci) {
        var world = (ServerWorld) ((Object)this);
        var config = ConfigServer.fetch().announcement;

        if (!config.enabled) return;

        int check_interval = config.check_interval_seconds * 20;
        for (var player: world.getPlayers()) {
            if (player.isSpectator()) continue;

            if ((player.age + player.getId()) % check_interval != 0) continue;

            var locationData = PatternMatching.LocationData.create(world, player.getBlockPos());
            var difficultyResult = PatternMatching.getDifficultyResult(locationData, null, ScalingGoal.ENTITY, world);
            var history = ((DifficultyHandler)player).getLastDifficultyAnnouncements();

            var previousAnnouncements = ((DifficultyHandler)player).getLastDifficultyAnnouncements();
            if (difficultyResult != null && difficultyResult.difficulty().isValid()) {
                announce(difficultyResult, player);
            } else {
                if (history.isEmpty() || !history.get(history.size() - 1).equals(Difficulty.Announcement.EMPTY)) {
                    history.add(Difficulty.Announcement.EMPTY);

                    if (history.size() > config.history_size) {
                        history.remove(0);
                    }
                }
            }
        }
    }

    @Unique
    private void announce(PatternMatching.DifficultySearchResult difficultyResult, ServerPlayerEntity player) {
        var config = ConfigServer.fetch().announcement;
        var history = ((DifficultyHandler) player).getLastDifficultyAnnouncements();

        String currentKey = (difficultyResult.matchId() != null)
                ? difficultyResult.matchId().toString()
                : difficultyResult.locationData().dimensionId().toString();

        boolean isGenericMatch = (difficultyResult.matchId() == null);

        if (isGenericMatch && !history.isEmpty()) {
            var lastEntry = history.get(history.size() - 1);

            if (lastEntry.dimensionId().equals(difficultyResult.locationData().dimensionId().toString())) {
                return;
            }
        }

        if (!history.isEmpty()) {
            var lastSeen = history.get(history.size() - 1);
            if (lastSeen.locationName().equals(currentKey)) return;
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            var previous = history.get(i);

            if (previous.locationName().equals(currentKey)) {
                long timeDiff = player.age - previous.timestamp();
                long cooldownTicks = config.reannounce_cooldown_seconds * 20L;

                if (timeDiff < cooldownTicks) return;

                break;
            }
        }

        var announcement = new Difficulty.Announcement(
                difficultyResult.difficulty(),
                player.age,
                difficultyResult.locationData().dimensionId().toString(),
                difficultyResult.matchId()
        );

        history.add(announcement);
        if (history.size() > config.history_size) history.remove(0);

        var difficulty = difficultyResult.difficulty();
        var title = "Dungeon";
        if (difficultyResult.match() != null) {
            var match = difficultyResult.match();

            if (match.matchingStructure() != null) {
                var id = match.matchingStructure().getKey().get().getValue();
                title = LanguageUtil.translateId("structure", id.toString());
            }
            else if (match.matchingBiome() != null && match.matchingBiome().getKey().isPresent()) {
                var id = match.matchingBiome().getKey().get().getValue();
                title = LanguageUtil.translateId("biome", id.toString());
            }
            else {
                title = LanguageUtil.translateId("dimension", difficultyResult.locationData().dimensionId().toString());
            }
        } else {
            var biome = difficultyResult.locationData().biome().biomeEntry();
            if (biome.getKey().isPresent()) {
                var id = biome.getKey().get().getValue();
                title = LanguageUtil.translateId("biome", id.toString());
            }
        }

        Platform.util().sendVanillaPacket(player, new TitleS2CPacket(Text.translatable(title)));
        Platform.util().sendVanillaPacket(player, new SubtitleS2CPacket(Text.translatable(difficulty.typeTranslationKey())
                .append(" " + difficulty.level()))
        );
    }
}
