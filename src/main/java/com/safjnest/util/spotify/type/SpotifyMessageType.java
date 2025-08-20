package com.safjnest.util.spotify.type;

public enum SpotifyMessageType {
  ALBUMS("albums"),
  TRACKS("tracks"),
  ARTISTS("artists");

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
