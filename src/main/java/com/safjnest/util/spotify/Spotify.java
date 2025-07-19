package com.safjnest.util.spotify;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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

public class Spotify {
    private static final String SPOTIFY_API_BASE_URL = "https://api.spotify.com/v1";
    private static final String spotifyPath = "rsc" + File.separator + "my_spotify_data.zip";

    public static void printTracks() {
        try {
            long startTime = System.currentTimeMillis();
            List<SpotifyTrackStreaming> streamings = readStreamsInfoFromZip(spotifyPath);
            long endTime = System.currentTimeMillis();
            System.out.println("Time taken to read tracks: " + (endTime - startTime) + " ms");
            System.out.println("Total tracks: " + streamings.size());
            //tracks.forEach(System.out::println);

            Map<SpotifyTrack, Long> tracks = streamings.stream()
            .filter(streaming -> streaming.getMsPlayed() >= 30000)
            .collect(Collectors.groupingBy(
                streaming -> streaming.getTrack(),
                Collectors.counting()
            ));

            System.out.println("Total unique tracks: " + tracks.size());
            printTopTracks(tracks);
            System.out.println("\nTop Albums:");
            printTopAlbums(tracks);
            System.out.println("\nTop Artists:");
            printTopArtists(tracks);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void printTopTracks(Map<SpotifyTrack, Long> tracks) {
        tracks.entrySet().stream()
            .sorted(Map.Entry.<SpotifyTrack, Long>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> System.out.println(entry.getKey().toString() + " - Played " + entry.getValue() + " times"));
    }

    public static void printTopAlbums(Map<SpotifyTrack, Long> tracks) {
        Map<String, Long> albums = tracks.entrySet().stream()
            .collect(Collectors.groupingBy(
                entry -> entry.getKey().getAlbum(),
                Collectors.summingLong(Map.Entry::getValue)
            ));

        albums.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> System.out.println(entry.getKey() + " - Played " + entry.getValue() + " times"));
    }

    public static void printTopArtists(Map<SpotifyTrack, Long> tracks) {
        Map<String, Long> artists = tracks.entrySet().stream()
            .collect(Collectors.groupingBy(
                entry -> entry.getKey().getArtist(),
                Collectors.summingLong(Map.Entry::getValue)
            ));

        artists.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> System.out.println(entry.getKey() + " - Played " + entry.getValue() + " times"));
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

        return trackList;
    }
}
