package com.safjnest.util.spotify;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.model.spotify.spotifyTrackStreaming;

//nome
//artista
//album
//album type

//"ts": "2015-06-06T16:00:31Z"
//"ms_played": 177818
//"master_metadata_track_name": "See You Again (feat. Charlie Puth)"
//"master_metadata_album_artist_name": "Wiz Khalifa"
//"master_metadata_album_album_name": "See You Again (feat. Charlie Puth)"
//"spotify_track_uri": "spotify:track:7wqSzGeodspE3V6RBD5W8L" | (null if podcast)

public class Spotify {
    private static final String spotifyPath = "rsc" + File.separator + "my_spotify_data.zip";

    public static void printTracks() {
        try {
            long startTime = System.currentTimeMillis();
            List<spotifyTrackStreaming> tracks = readTrackInfoFromZip(spotifyPath);
            long endTime = System.currentTimeMillis();
            System.out.println("Time taken to read tracks: " + (endTime - startTime) + " ms");
            System.out.println("Total tracks: " + tracks.size());
            tracks.forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<spotifyTrackStreaming> readTrackInfoFromZip(String zipFilePath) throws IOException {
        File zipFile = new File(spotifyPath);

        List<spotifyTrackStreaming> trackList = new ArrayList<>();
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);

        JsonFactory factory = new JsonFactory();
        factory.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(zipFile), 32 * 1024);

            ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".json")) {    
                    try (JsonParser parser = factory.createParser(zis)) {
                        if (parser.nextToken() == JsonToken.START_ARRAY) {
                            while (parser.nextToken() == JsonToken.START_OBJECT) {

                                JsonNode node = mapper.readTree(parser);
                                
                                if (node.path("master_metadata_track_name").isNull()) {
                                    continue;
                                }

                                spotifyTrackStreaming track = new spotifyTrackStreaming(
                                    node.path("ts").asText(),
                                    node.path("ms_played").asLong(0),
                                    node.path("master_metadata_track_name").asText(),
                                    node.path("master_metadata_album_artist_name").asText(),
                                    node.path("master_metadata_album_album_name").asText(),
                                    node.path("spotify_track_uri").asText().substring(14)
                                );
                                trackList.add(track);
                            }
                        }
                    }
                }
            }
        }

        return trackList;
    }
}
