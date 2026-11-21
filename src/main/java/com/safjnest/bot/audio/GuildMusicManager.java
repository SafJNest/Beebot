package com.safjnest.bot.audio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import com.safjnest.bot.audio.AudioEffects.Effect;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

/**
 * Copilot doenst know what this class does
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @since 5.0
 */
public class GuildMusicManager {
    private final AudioPlayerManager manager;
    private final BooleanSupplier overlapSound;
    private final List<SoundPlayback> activeSounds = new CopyOnWriteArrayList<>();

    private final TrackScheduler trackScheduler;
    private final AudioForwarder audioForwarder;

    private volatile Effect soundEffect;

    record SoundPlayback(UUID id, AudioPlayer player) {}

    public GuildMusicManager(AudioPlayerManager manager, BooleanSupplier overlapSound) {
        this.manager = manager;
        this.overlapSound = overlapSound;
        this.soundEffect = Effect.NONE;

        AudioPlayer musicPlayer = manager.createPlayer();
        trackScheduler = new TrackScheduler(musicPlayer, this::playSound, this::stopEffects);
        musicPlayer.addListener(trackScheduler);

        audioForwarder = new AudioForwarder(musicPlayer, activeSounds, manager.getConfiguration());
    }

    public TrackScheduler getTrackScheduler() {
        return trackScheduler;
    }

    public AudioForwarder getAudioForwarder() {
        return audioForwarder;
    }

    public Effect getSoundEffect() {
        return soundEffect;
    }

    public void setSoundEffect(Effect effect) {
        soundEffect = Objects.requireNonNull(effect);
    }

    public synchronized UUID playSound(AudioTrack track) {
        Objects.requireNonNull(track);

        if (!overlapSound.getAsBoolean())
            stopEffects();

        AudioPlayer player = manager.createPlayer();
        SoundPlayback playback = new SoundPlayback(UUID.randomUUID(), player);
        player.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackEnd(AudioPlayer source, AudioTrack ended, AudioTrackEndReason reason) {
                activeSounds.remove(playback);
            }
        });

        activeSounds.add(playback);
        try {
            player.playTrack(track);
        } catch (RuntimeException failure) {
            activeSounds.remove(playback);
            player.destroy();
            throw failure;
        }

        return playback.id();
    }

    public synchronized boolean stopSound(UUID playbackId) {
        for (SoundPlayback playback : activeSounds) {
            if (playback.id().equals(playbackId) && activeSounds.remove(playback)) {
                playback.player().destroy();

                return true;
            }
        }

        return false;
    }

    public void resetPlayback() {
        stopEffects();
        trackScheduler.clearQueue();
        audioForwarder.closeEncoder();
    }

    /* -------------------------------------------------------------------------- */

    private synchronized void stopEffects() {
        if (activeSounds.isEmpty())
            return;

        List<SoundPlayback> stopped = List.copyOf(activeSounds);
        activeSounds.clear();

        for (SoundPlayback playback : stopped)
            playback.player().destroy();
    }
}
