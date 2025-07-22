package com.safjnest.util.spotify;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.model.spotify.SpotifyTrackStreaming;
import com.safjnest.sql.SpotifyDBHandler;
import com.safjnest.model.spotify.SpotifyAlbum;
import com.safjnest.model.spotify.SpotifyTrack;

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

public class SpotifyHandler {
    private static final String SPOTIFY_API_BASE_URL = "https://api.spotify.com/v1";
    private static final String spotifyPath = "rsc" + File.separator + "my_spotify_data.zip";
    
    public static List<Map.Entry<SpotifyTrack, Long>> getTracks(List<SpotifyTrackStreaming> streamings){
        return streamings.stream()
            .filter(streaming -> streaming.getMsPlayed() >= 30000)
            .collect(Collectors.groupingBy(
                streaming -> streaming.getTrack(),
                Collectors.counting()
            )).entrySet().stream().toList();
    }

    //get tracks with play count from streamings (only those played for more than 30s)
    public static List<Map.Entry<SpotifyTrack, Long>> getSortedTracks(List<SpotifyTrackStreaming> streamings){
        return getTracks(streamings).stream()
            .sorted(Map.Entry.<SpotifyTrack, Long>comparingByValue().reversed()).toList();
    }

    public static List<Map.Entry<SpotifyAlbum, Long>> getSortedAlbums(List<SpotifyTrackStreaming> streamings) {
        Map<SpotifyTrack, Long> trackCounts = getTracks(streamings).stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<String, List<SpotifyTrack>> albumToTracks = trackCounts.keySet().stream()
            .collect(Collectors.groupingBy(SpotifyTrack::getAlbum));

        Map<SpotifyAlbum, Long> albumPlayCounts = albumToTracks.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> new SpotifyAlbum(entry.getKey(), entry.getValue().get(0).getArtist(), entry.getValue()),
                entry -> entry.getValue().stream()
                    .mapToLong(track -> trackCounts.getOrDefault(track, 0L))
                    .sum()
            ));

        return albumPlayCounts.entrySet().stream()
            .sorted(Map.Entry.<SpotifyAlbum, Long>comparingByValue().reversed())
            .toList();
    }

    public static String getTrackImage(String trackId) {
        String urlString = SPOTIFY_API_BASE_URL + "/tracks/" + trackId;
        String response = getJSONFromURL(urlString);

        try {
            JSONObject jsonResponse = new JSONObject(response.toString());
            String imageUrl = jsonResponse.getJSONObject("album").getJSONArray("images")
                .getJSONObject(0).getString("url");

            return imageUrl;
        } catch (Exception e) {
            e.printStackTrace();
            return "https://i.scdn.co/image/ab67616d0000b2739194a814e095d1347c02fd32";
        }
    }

    public static String getAlbumImage(String trackId) {
        String urlString = SPOTIFY_API_BASE_URL + "/tracks/" + trackId;
        String response = getJSONFromURL(urlString);

        try {
            JSONObject jsonResponse = new JSONObject(response.toString());
            String imageUrl = jsonResponse.getJSONObject("album").getJSONArray("images")
                .getJSONObject(0).getString("url");

            return imageUrl;
        } catch (Exception e) {
            e.printStackTrace();
            return "https://i.scdn.co/image/ab67616d0000b2739194a814e095d1347c02fd32";
        }
    }

    public static String getJSONFromURL(String urlString) {
        StringBuilder result = new StringBuilder();
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + TokenManager.getAccessToken());

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result.toString();
    }


    public static List<SpotifyTrackStreaming> readStreamsInfoFromZip(String zipFilePath) throws IOException {
        File zipFile = new File(spotifyPath);

        List<SpotifyTrackStreaming> trackList = new ArrayList<>();
        
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

                                SpotifyTrackStreaming track = new SpotifyTrackStreaming(
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
        try {
            System.out.println("Saving " + trackList.size() + " tracks to the database...");
            SpotifyDBHandler.insertBatch(trackList,"291624587278417920");
        } catch (NoSuchAlgorithmException | SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return trackList;
    }

    public static List<SpotifyTrackStreaming> readStreamsInfoFromZip(InputStream zipInputStream, String userId) throws IOException {
        List<SpotifyTrackStreaming> trackList = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);

        JsonFactory factory = new JsonFactory();
        factory.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);

        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
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
                                SpotifyTrackStreaming track = new SpotifyTrackStreaming(
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
        try {
            SpotifyDBHandler.insertBatch(trackList, userId);
        } catch (NoSuchAlgorithmException | SQLException e) {
            e.printStackTrace();
        }
        return trackList;
    }

}
