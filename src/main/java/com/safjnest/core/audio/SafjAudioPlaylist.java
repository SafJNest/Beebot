package com.safjnest.core.audio;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class SafjAudioPlaylist implements AudioPlaylist {
    private final String name;
    private final List<AudioTrack> tracks;
    private final AudioTrack selectedTrack;

    public SafjAudioPlaylist(String name, List<AudioTrack> tracks, AudioTrack selectedTrack) {
        this.name = name;
        this.tracks = tracks;
        this.selectedTrack = selectedTrack;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<AudioTrack> getTracks() {
        return tracks;
    }

    @Override
    public AudioTrack getSelectedTrack() {
        return selectedTrack;
    }

    @Override
    public boolean isSearchResult() {
        return false;
    }
}
