package com.safjnest.Utilities.Audio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;

import java.util.Collections;
import java.util.LinkedList;

/**
 * 
 * This class schedules tracks for the audio player, handles the queue and
 * manages
 * all the tracks.
 * <p>
 * Handles all the events that could occur during the listening:
 * <ul>
 * <li>start a track</li>
 * <li>stop a track</li>
 * <li>pause the track</li>
 * <li>resume the track</li>
 * <li>catch a TrackException</li>
 * </ul>
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @since 1.0
 */
public class TrackScheduler extends AudioEventAdapter {

    private final AudioPlayer player;
    private final LinkedList<AudioTrack> queue;
    private LinkedList<AudioTrack> unshuffledQueue;
    private int currentTrackIndex;
    private boolean isRepeat;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedList<>();
        this.currentTrackIndex = -1;
        this.unshuffledQueue = null;
        this.isRepeat = false;
    }

    public void play(AudioTrack track) {
        player.startTrack(track, true);
    }

    public void playForce(AudioTrack track) {
        player.startTrack(track, false);
    }

    public void playForce(AudioTrack track, long position) {
        track.setPosition(position);
        player.startTrack(track, false);
    }

    public void queue(AudioTrack track) {
        queue.offer(track);
        if (currentTrackIndex == -1) {
            currentTrackIndex = queue.size() - 1;
        }
        play(getCurrentTrack());
    }

    public void queueNoPlay(AudioTrack track) {
        queue.offer(track);
        if (currentTrackIndex == -1) {
            currentTrackIndex = queue.size() - 1;
        }
    }

    public void addTrackToFront(AudioTrack track) {
        if (currentTrackIndex < queue.size() - 1) {
            queue.add(currentTrackIndex + 1, track);
        } else {
            queue.offer(track);
        }
    }

    public AudioTrack getCurrentTrack() {
        if (currentTrackIndex != -1 && currentTrackIndex < queue.size()) {
            return queue.get(currentTrackIndex).makeClone();
        }
        return null;
    }

    public AudioTrack nextTrack() {
        if (queue.isEmpty() || currentTrackIndex >= queue.size() - 1) {
            currentTrackIndex = -1;
            return null;
        }
        currentTrackIndex = currentTrackIndex + 1;
        return getCurrentTrack();
    }

    public AudioTrack prevTrack() {
        if (queue.isEmpty()) {
            return null;
        }

        if (currentTrackIndex == -1) {
            currentTrackIndex = queue.size() - 1;
        } else if (currentTrackIndex > 0) {
            currentTrackIndex--;
        } else {
            return null;
        }

        return getCurrentTrack();
    }

    public void playForceNext() {
        playForce(nextTrack());
    }

    public void playForcePrev() {
        playForce(prevTrack());
    }

    @Override
    public void onPlayerPause(AudioPlayer player) {

    }

    @Override
    public void onPlayerResume(AudioPlayer player) {

    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {

    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        System.out.println(endReason);

        if (endReason.mayStartNext) {
            if (isRepeat) {
                playForce(track.makeClone());
                return;
            }
            play(nextTrack());
        }

        // endReason == FINISHED: A track finished or died by an exception (mayStartNext
        // = true).
        // endReason == LOAD_FAILED: Loading of a track failed (mayStartNext = true).
        // endReason == STOPPED: The player was stopped.
        // endReason == REPLACED: Another track started playing while this had not
        // finished
        // endReason == CLEANUP: Player hasn't been queried for a while, if you want you
        // can put a clone of this back to your queue
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        System.out.println("Track exception");
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        System.out.println("Track stuck");
    }

    public boolean isPlaying() {
        return // jda.getGuildById(guildId).getSelfMember().getVoiceState().inAudioChannel() &&
            player.getPlayingTrack() != null;
    }

    public LinkedList<AudioTrack> getQueue() {
        return queue;
    }

    public int getIndex() {
        return currentTrackIndex;
    }

    public AudioPlayer getPlayer() {
        return player;
    }

    public void shuffleQueue() {
        if (!queue.isEmpty()) {
            unshuffledQueue = new LinkedList<>(queue);
            Collections.shuffle(queue);

            if (currentTrackIndex != -1) {
                currentTrackIndex = queue.indexOf(unshuffledQueue.get(currentTrackIndex));
            }

        }
    }

    public void unshuffleQueue() {
        if (unshuffledQueue != null) {
            if (currentTrackIndex != -1) {
                currentTrackIndex = unshuffledQueue.indexOf(queue.get(currentTrackIndex));
            }
            
            queue.clear();
            queue.addAll(unshuffledQueue);
            unshuffledQueue = null;
        }
    }

    public void setRepeat(boolean repeat) {
        isRepeat = repeat;
    }

    public boolean isRepeat() {
        return isRepeat;
    }

    public boolean isShuffled() {
        return unshuffledQueue != null;
    }

}