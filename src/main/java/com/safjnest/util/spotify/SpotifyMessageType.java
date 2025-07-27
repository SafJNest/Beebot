package com.safjnest.util.spotify;

public enum SpotifyMessageType {
  ALBUMS("Albums"),
  TRACKS("Tracks"),
  ARTISTS("Artists");

  private String label;

  SpotifyMessageType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return this.label;
  }

  public String toButtonId() {
    return "spotify-type-" + this.name().toLowerCase();
  }
}
