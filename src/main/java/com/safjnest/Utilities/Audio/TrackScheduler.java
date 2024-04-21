package com.safjnest.Utilities.Audio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;

import java.util.Collections;
import java.util.LinkedList;

/**
 * This class schedules tracks for the audio player.
 * It contains the queue, the events and the methods to handle them.
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
    private boolean isForced;
    private boolean isQueuePaused;

    private QueueMessage lastMessageSent;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedList<>();
        this.currentTrackIndex = -1;
        this.unshuffledQueue = null;
        this.isRepeat = false;
        this.isForced = false;
        this.isQueuePaused = false;
        this.lastMessageSent = null;
    }


    public LinkedList<AudioTrack> getQueue() {
        return queue;
    }

    public int getIndex() {
        return currentTrackIndex;
    }

    public void setIndex(int index) {
        currentTrackIndex = index;
    }

    public AudioPlayer getPlayer() {
        return player;
    }

    public boolean isPlaying() {
        return player.getPlayingTrack() != null;
    }

    public boolean isPaused() {
        return player.isPaused() && isQueuePaused;
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



    public boolean canPlay() {
        return !isQueuePaused && currentTrackIndex == -1;
    }

    public void play(AudioTrack track, boolean forced) {
        if(player.isPaused())
            player.setPaused(false);
 
        player.startTrack(track, !forced);
    }

    public void play(AudioTrack track, long position, boolean forced) {
        if(player.isPaused())
            player.setPaused(false);

        track.setPosition(position);
        player.startTrack(track, !forced);
    }

    public void play(AudioTrack track) {
        play(track, false);
    }

    public void play() {
        play(getcurrent());
    }

    public void play(AudioTrack track, AudioType type) {
        isForced = type == AudioType.SOUND;
        play(track, isForced);
    }


    public void queue(AudioTrack track) {
        queue.offer(track);
    }

    public void addTrackToFront(AudioTrack track) {
        if (currentTrackIndex < queue.size() - 1) {
            queue.add(currentTrackIndex + 1, track);
        } else {
            queue.offer(track);
        }
    }

    public void clearQueue() {
        queue.clear();
        unshuffledQueue = null;
        player.stopTrack();
        currentTrackIndex = -1;
        isForced = false;
        isRepeat = false;
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



    public void pause(boolean pause) {
        TrackData data = player.getPlayingTrack().getUserData(TrackData.class);
        
        player.setPaused(pause);

        if (data != null && data.isQueueable()) {
            isQueuePaused = pause;
        }
    }

    public void stop() {
        player.stopTrack();
        setIndex(-1);
    }

    public void fixIndex() {
        if (currentTrackIndex == -1) {
            currentTrackIndex = queue.isEmpty() ? -1 : 0;
        }
    }

    public AudioTrack getcurrent() {
        fixIndex();
        return queue.get(currentTrackIndex).makeClone();
    }

    public boolean checkIndex(int index) {
        if (index < 0 || index >= queue.size()) {
            return false;
        }
        return queue.get(index) != null;
    }

    public AudioTrack moveCursor(int value) {
        if (!checkIndex(currentTrackIndex + value)) {
            currentTrackIndex = -1;
            return null;
        }
        currentTrackIndex += value;
        return getcurrent();
    }

    public AudioTrack moveCursor(int value, boolean checkQueue) {
        if(checkQueue && currentTrackIndex == -1 && !getQueue().isEmpty()) {
            return moveCursor(value);
        }
        return null;
    }

    

    public void setMessage(QueueMessage message) {
        lastMessageSent = message;
    }

    public void deleteMessage() {
        if (lastMessageSent != null)
            lastMessageSent.delete();
    }





    @Override
    public void onPlayerPause(AudioPlayer player) {
        //
    }

    @Override
    public void onPlayerResume(AudioPlayer player) {

    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {

    }

    /**
     * This method handles all the events that are fired when a track ends.
     * <ul>
     * <li>{@code FINISHED}: A track finished or died by an exception (mayStartNext = true).</li>
     * <li>{@code LOAD_FAILED}: Loading of a track failed (mayStartNext = true).</li>
     * <li>{@code STOPPED}: The player was stopped.</li>
     * <li>{@code REPLACED}: Another track started playing while this had not finished.</li>
     * <li>{@code CLEANUP}: Player hasn't been queried for a while, if you want you can put a clone of this back to your queue.</li>
     * </ul>
     */
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // FINISHED: A track finished or died by an exception (mayStartNext = true).
        // LOAD_FAILED: Loading of a track failed (mayStartNext = true).
        if (endReason.mayStartNext) {
            int toMove = isRepeat ? 0 : 1;
            long offset = 0;
            if (isForced && !isRepeat && !isQueuePaused) {
                toMove = 0;
                offset = getcurrent() != null ? getcurrent().getPosition() : 0;
            }
            
            isForced = false;

            AudioTrack toPlay = moveCursor(toMove);
            if (toPlay != null) 
                play(toPlay, offset, false);

            lastMessageSent.update();
        } 

        // STOPPED: The player was stopped.
        if (endReason == AudioTrackEndReason.STOPPED) {
            
        }

        // REPLACED: Another track started playing while this had not finished
        if(endReason == AudioTrackEndReason.REPLACED) {
            if(isForced && track.getUserData(TrackData.class).isQueueable()) {
                queue.set(currentTrackIndex, track);
            }
        }

        // CLEANUP: Player hasn't been queried for a while, if you want you can put a clone of this back to your queue
        if(endReason == AudioTrackEndReason.CLEANUP) {
            System.out.println("The time of thread has come to an end.");
        }

        if (track.getUserData(TrackData.class).isQueueable()) {
            track.setPosition(0);
            queue.set(currentTrackIndex, track);
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        System.out.println("Track exception");
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        System.out.println("Track stuck");
    }

    




    @Override
    public String toString() {
        return "TrackScheduler [currentTrackIndex=" + currentTrackIndex + ", isRepeat=" + isRepeat + ", isForced=" 
            + isForced + ", isQueuePaused=" + isQueuePaused + ",\nqueue(" + queue.size() + ")=" + queue.toString() +"]";
    }
}