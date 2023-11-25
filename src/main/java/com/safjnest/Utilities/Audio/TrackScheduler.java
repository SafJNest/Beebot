package com.safjnest.Utilities.Audio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import net.dv8tion.jda.api.JDA;

import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;

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
  private final PlayerManager pm;
  private final AudioPlayer player;
  
  private final LinkedList<AudioTrack> queue;
  private int currentTrackIndex = -1;

  private final String guildId;

  public TrackScheduler(AudioPlayer player, PlayerManager pm, String guildId) {
    this.player = player;
    this.pm = pm;
    this.guildId = guildId;
    this.queue = new LinkedList<>();
  }

  public void play(AudioTrack track) {
    player.startTrack(track, true);
  }

  public void playForce(AudioTrack track) {
    player.startTrack(track, false);
  }

  public void addQueue(AudioTrack track) {
    queue.offer(track);
    if (currentTrackIndex == -1) {
      currentTrackIndex = queue.size() - 1;
    }
    play(getCurrentTrack());
  }

  public void addTrackToFront(AudioTrack track) {
    if (currentTrackIndex != -1 && currentTrackIndex < queue.size() - 1) {
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
    if(!queue.isEmpty() && currentTrackIndex == -1) {
      currentTrackIndex = queue.size() - 1;
    }
    else if (queue.isEmpty() || currentTrackIndex <= 0) {
      return null;
    }
    currentTrackIndex = currentTrackIndex - 1;
    return getCurrentTrack();
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
      System.out.println("index before" + currentTrackIndex);

      if(endReason.mayStartNext) {
        play(nextTrack());
        System.out.println("index after" + currentTrackIndex);
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


  public LinkedList<AudioTrack> getQueue() {
    return queue;
  }

}