package com.safjnest.Utilities.Audio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import net.dv8tion.jda.api.JDA;

import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;

/**
 * 
 * This class schedules tracks for the audio player, handles the queue and manages
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
  private final PlayerManager pm;
  private final String guildId;
  private final Deque<AudioTrack> stack = new ArrayDeque<AudioTrack>();

  public TrackScheduler(AudioPlayer player, PlayerManager pm, String guildId) {
    this.player = player;
    this.pm = pm;
    this.guildId = guildId;
    this.queue = new LinkedList<>();
  }

  /**
   * Add the track to the {@link com.safjnest.Utilities.Audio.TrackScheduler#queue queue}
   * @param track Comes from {@link com.safjnest.Commands.Audio.PlayYoutube Play} or {@link com.safjnest.Commands.Audio.PlaySound PlaySound}
   */
  public void addQueue(AudioTrack track) {
    if (!player.startTrack(track, true)) {
      queue.add(track);
    }
  }

  public void addTopQueue(AudioTrack track) {
    queue.add(0, track);
    if(pm.getPlayer().getPlayingTrack() != null)
      queue.add(1, pm.getPlayer().getPlayingTrack().makeClone());
    nextTrack();
  }

  public void forcePlay(AudioTrack track) {
    player.startTrack(track, false);
  }

  /**
   * When a new track is required from 
   * {@link com.safjnest.Commands.Audio.PlayYoutube Play} 
   * or {@link com.safjnest.Commands.Audio.PlaySound PlaySound}
   * the method polls the first track in the {@link com.safjnest.Utilities.Audio.TrackScheduler#queue queue}
   * @return
   * {@code AudioTrack}
   * @throws InterruptedException
   */
  public void nextTrack() {
    forcePlay(queue.poll());
  }

  public void prevTrack() {
    addTopQueue(stack.poll().makeClone());
  }

  @Override
  public void onPlayerPause(AudioPlayer player) {

  }

  @Override
  public void onPlayerResume(AudioPlayer player) {
    
  }

  @Override
  public void onTrackStart(AudioPlayer player, AudioTrack track) {
    // A track started playing
  }

  @Override
  public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
      if(endReason.name().equals("CLEANUP")){
        System.out.println("The time of thread has come to an end.");
        pm.terminator3LeMacchineRibelli();
      }

      System.out.println(endReason);

      if(endReason.name().equals("REPLACED")) {
        stack.push(track);
        System.out.println("stack:" + stack.size());
      }  
      else if(endReason.mayStartNext) {
        System.out.println(stack.size());
        stack.push(track);
        nextTrack();
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

  public boolean isPlaying(JDA jda) {
    return jda.getGuildById(guildId).getSelfMember().getVoiceState().inAudioChannel() && player.getPlayingTrack() != null;
  }

  public int getQueueSize() { 
    return queue.size();
  }
}