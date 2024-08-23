package com.safjnest.core.audio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.safjnest.core.audio.types.AudioType;
import com.safjnest.util.SafJNest;
import com.safjnest.util.log.BotLogger;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.FileUpload;

import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.YoutubeCallback;
import com.github.kiulian.downloader.downloader.YoutubeProgressCallback;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.request.RequestVideoStreamDownload;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.AudioFormat;
import com.github.kiulian.downloader.model.videos.formats.Format;

/**
 * This class schedules tracks for the audio player.
 * It contains the queue, the events and the methods to handle them.
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @since 1.0
 */
public class TrackScheduler extends AudioEventAdapter {
    private static final double MAX_YT_DOWNLOAD_SIZE = 5;
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
        return (currentTrackIndex == -1) || !isQueuePaused;
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
        play(getCurrent());
    }

    public void play(AudioTrack track, AudioType type) {
        isForced = (type != AudioType.AUDIO);
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
        player.stopTrack();
        
        currentTrackIndex = -1;
        unshuffledQueue = null;
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
        if (player.getPlayingTrack() == null) return;

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

    public AudioTrack getCurrent() {
        fixIndex();
        return queue.get(currentTrackIndex).makeClone();
    }

    public AudioTrack getPrevious() {
        if(moveCursor(getQueue().size(), true) == null)
            moveCursor(-1);
        return getCurrent();
    }

    public boolean checkIndex(int index) {
        if (queue.isEmpty() || index < 0 || index >= queue.size()) {
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
        return getCurrent();
    }

    public AudioTrack moveCursor(int value, boolean checkQueue) {
        if (!checkQueue || (checkQueue && currentTrackIndex == -1 && !getQueue().isEmpty())) {
            return moveCursor(value);
        }
        return null;
    }

    

    public void setMessage(QueueMessage message) {
        lastMessageSent = message;
    }

    public QueueMessage getMessage() {
        return lastMessageSent;
    }

    public void deleteMessage() {
        if (lastMessageSent != null) {
            lastMessageSent.delete();
            lastMessageSent = null;
        }
    }


    public boolean isQueueable(AudioTrack track) {
        return track.getUserData(TrackData.class) != null && track.getUserData(TrackData.class).isQueueable();
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
            int toMove = isRepeat || !isQueueable(track) ? 0 : 1;
            long trackPosition = (isRepeat || !isQueueable(track)) && (checkIndex(currentTrackIndex)) ? queue.get(currentTrackIndex).getPosition() : 0;

            /*
            System.out.println("track: " + track.getInfo().title 
                          + " | isRepeat: " + isRepeat 
                          + " | isForced: " + isForced 
                          + " | isQueuePaused: " + isQueuePaused 
                          + " | isQueuable: " + isQueueable(track) 
                          + " | current position: " + (queue.get(currentTrackIndex) != null ? queue.get(currentTrackIndex).getPosition() : 0)
                          + " | toMove: " + toMove 
                          + " | position: " + trackPosition);
            */

            isForced = false;

            AudioTrack toPlay = moveCursor(toMove);

            if(toPlay != null) play(toPlay, trackPosition, false);
            if(lastMessageSent != null) lastMessageSent.update();
            if(!isQueueable(track) && isQueuePaused) pause(true);
        } 

        // STOPPED: The player was stopped.
        if (endReason == AudioTrackEndReason.STOPPED) {
            
        }

        // REPLACED: Another track started playing while this had not finished
        if(endReason == AudioTrackEndReason.REPLACED) {
            if(isForced && isQueueable(track)) {
                queue.set(currentTrackIndex, track);
            }
        }

        //terminator3LeMacchineRibelli(endReason);

    }

    /**
     * @deprecated
     */
    @Deprecated
    public void terminator3LeMacchineRibelli(AudioTrackEndReason endReason) {
        // CLEANUP: Player hasn't been queried for a while, if you want you can put a clone of this back to your queue
        if(endReason == AudioTrackEndReason.CLEANUP) {
            BotLogger.debug("The time of thread has come to an end.");
            //Player.distruzione_demoniaca();
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        BotLogger.error("Track exception: " + exception.getMessage());
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        BotLogger.error("Track stuck: " + track.getInfo().title + " | " + thresholdMs + "ms");
    }

    




    @Override
    public String toString() {
        return "TrackScheduler [currentTrackIndex=" + currentTrackIndex + ", isRepeat=" + isRepeat + ", isForced=" 
            + isForced + ", isQueuePaused=" + isQueuePaused + ",\nqueue(" + queue.size() + ")=" + queue.toString() +"]";
    }


	public void movePosition(int seconds) {
		movePosition((long) (seconds * 1000));
	}

    public void movePosition(long milliseconds) {
        if (player.getPlayingTrack() != null) {
            player.getPlayingTrack().setPosition(player.getPlayingTrack().getPosition() + milliseconds);
        }
	}

    public String getLyrics(AudioTrack track) {
        if(!track.getSourceManager().getSourceName().equals("spotify")) return "Lyrics are only available for Spotify tracks";
        
        AudioLyrics audioLyrics = PlayerManager.get().loadLyrics(track);
        if(audioLyrics == null) return "No lyrics found for this track";
        
        List<AudioLyrics.Line> lines = audioLyrics.getLines();
        StringBuilder sb = new StringBuilder();
        if (lines != null) {
            for (AudioLyrics.Line line : lines) {
                sb.append(line.getLine()).append("\n");
            }
        }
        return sb.toString();
    }
    

    private long estimateFileSize(VideoInfo video, Format format) {
        int bitrate = format instanceof AudioFormat ? ((AudioFormat)format).averageBitrate() : format.bitrate();
        long duration = video.details().lengthSeconds();
        return (bitrate * duration) / 8; //in bytes
    }

    public void downloadTrackAudio(AudioTrack track, InteractionHook hook) {
        if(!track.getSourceManager().getSourceName().equals("youtube")) {
            hook.sendMessage("Can only download youtube videos.").queue();
            return;
        }

        Message message = hook.sendMessage("Downloading audio...").complete();

        String videoId = track.getIdentifier();
        YoutubeDownloader downloader = new YoutubeDownloader();
        RequestVideoInfo request = new RequestVideoInfo(videoId)
            .callback(new YoutubeCallback<VideoInfo>() {
                @Override
                public void onFinished(VideoInfo video) {
                    if(!video.details().isDownloadable()) {
                        BotLogger.info("Download of video `" + video.details().title() + "` rejected for being not downladable.");
                        message.editMessage("Content cannot be downloaded.").queue();
                        return;
                    }

                    AudioFormat format = video.bestAudioFormat();

                    double fileSizeInMB = SafJNest.round(estimateFileSize(video, format) / (1024.0 * 1024.0), 1);
                    double maxDownlaodSize = Math.min(MAX_YT_DOWNLOAD_SIZE, hook.getInteraction().getGuild().getMaxFileSize());
                    if(fileSizeInMB > maxDownlaodSize) {
                        BotLogger.info("Download of video `" + video.details().title() + "` rejected for being too big: " + fileSizeInMB + " Mb");
                        message.editMessage("Video size too big (" + fileSizeInMB + " Mb), please keep it under " + maxDownlaodSize + " Mb.").queue();
                        return;
                    }

                    OutputStream os = new ByteArrayOutputStream();

                    RequestVideoStreamDownload downloadRequest = new RequestVideoStreamDownload(format, os)
                        .callback(new YoutubeProgressCallback<Void>() {
                            @Override
                            public void onDownloading(int progress) {
                                //System.out.printf("Downloaded %d%%\n", progress);
                            }

                            @Override
                            public void onFinished(Void unused) {
                                BotLogger.info("Finished downloading youtube video: " + video.details().title());

                                FileUpload fileUpload = FileUpload.fromData(((ByteArrayOutputStream) os).toByteArray(), video.details().title() + "." + format.extension().value());
                                message.editMessage("Here is your audio:").setAttachments(fileUpload).queue();
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                BotLogger.error("Error during download of youtube video: " + throwable.getMessage());
                                message.editMessage("Error during download: " + throwable.getMessage()).queue();
                            }
                        })
                        .async();

                        downloader.downloadVideoStream(downloadRequest);
                }

                @Override
                public void onError(Throwable throwable) {
                    BotLogger.error("Error during video info retrieval: " + throwable.getMessage());
                }
            })
            .async();

        downloader.getVideoInfo(request);
    }
}