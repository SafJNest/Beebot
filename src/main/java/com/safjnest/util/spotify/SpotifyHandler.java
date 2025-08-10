package com.safjnest.util.spotify;

import java.io.IOException;
import java.io.InputStream;
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
import com.safjnest.sql.WebsiteDBHandler;
import com.safjnest.util.HttpUtils;
import com.safjnest.model.spotify.SpotifyAlbum;
import com.safjnest.model.spotify.SpotifyArtist;
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
    public static final String DEFAULT_IMAGE = "https://i.scdn.co/image/ab67616d0000b2739194a814e095d1347c02fd32";
    public static final String SPOTIFY_API_BASE_URL = "https://api.spotify.com/v1";
    
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

    public static List<SpotifyTrackStreaming> readStreamsInfoFromZip(InputStream zipInputStream) throws IOException {
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
        
        return trackList;
    }

    public static void uploadStreamingsToDB(List<SpotifyTrackStreaming> streamings, String userId) {
        try {
            List<SpotifyTrackStreaming> filteredStreamings = streamings.stream()
            .filter(streaming -> streaming.getMsPlayed() >= 30000)
            .collect(Collectors.toList());

            if (filteredStreamings.isEmpty()) {
                System.out.println("No streamings to upload.");
                return;
            }

            System.out.println("Saving " + filteredStreamings.size() + " tracks to the database for user " + userId + "...");
            SpotifyDBHandler.insertBatch(filteredStreamings, userId);
        } catch (Exception e) {
            System.out.println("Error uploading streamings to the database: " + e.getMessage());
        }
    }

    public static List<?> getTopItems(SpotifyMessageType type, String userId, int limit, int offset, SpotifyTimeRange timeRange) {
        switch (timeRange) {
            case SHORT_TERM:
            case MEDIUM_TERM:
            case LONG_TERM:
                return getTopItemsFromSpotifyApi(type, timeRange, userId, limit, offset);
            case FULL_TERM:
                List<?> items = SpotifyDBHandler.getTopItems(type, userId, limit, offset);
                if(items == null || items.isEmpty()) {
                    throw new SpotifyException(SpotifyException.ErrorType.HISTORY_MISSING, 
                        "No items found in the database for user: " + userId);
                }
                return items;
            default:
                throw new SpotifyException(SpotifyException.ErrorType.INVALID_TIME_RANGE, 
                    "Invalid time range: " + timeRange);
        }
    }

    public static List<?> getTopItemsFromSpotifyApi(SpotifyMessageType type, SpotifyTimeRange timeRange, String userId, int limit, int offset) {
        if (type == SpotifyMessageType.ALBUMS) {
            throw new SpotifyException(SpotifyException.ErrorType.NOT_SUPPORTED, 
                "Fetching albums directly from Spotify API is not supported.");
        }

        String token = WebsiteDBHandler.getSpotifyUserToken(userId);

        if (token == null) {
            throw new SpotifyException(SpotifyException.ErrorType.NOT_LINKED,
                "Spotify token not found for user: " + userId);
        }

        String url = SPOTIFY_API_BASE_URL + "/me/top/" + type.getLabel()
                + "?time_range=" + timeRange.getLabel()
                + "&limit=" + limit
                + "&offset=" + offset;

        JSONObject response = HttpUtils.sendGetRequest(url, token);
        if (response == null) {
            throw new SpotifyException(SpotifyException.ErrorType.API_ERROR, 
                "Failed to fetch data from Spotify API");
        }

        try {
            List<Object> items = new ArrayList<>();
            for (Object item : response.getJSONArray("items")) {
                JSONObject jsonItem = (JSONObject) item;
                switch (type) {
                    case TRACKS:
                        items.add(new SpotifyTrack(
                                jsonItem.getString("name"),
                                jsonItem.getJSONArray("artists").getJSONObject(0).getString("name"),
                                jsonItem.getJSONObject("album").getString("name"),
                                jsonItem.getString("id"),
                                0,
                                jsonItem.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url")
                        ));
                        break;
                    case ARTISTS:
                        items.add(new SpotifyArtist(
                                jsonItem.getString("name"),
                                jsonItem.getJSONArray("images").getJSONObject(0).getString("url")
                        ));
                        break;
                    default:
                        break;
                }
            }
            return items;
        } catch (Exception e) {
            e.printStackTrace();
            throw new SpotifyException(SpotifyException.ErrorType.ERROR_PARSING, 
                "Error parsing Spotify response: " + e.getMessage());
        }
    }
}
