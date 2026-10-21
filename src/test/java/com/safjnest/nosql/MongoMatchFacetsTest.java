package com.safjnest.nosql;

import static org.junit.Assert.*;

import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;

import com.mongodb.MongoClientSettings;
import com.safjnest.lol.model.match.MatchFacetQuery;
import com.safjnest.lol.model.match.MatchFacets;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class MongoMatchFacetsTest {

    @Test
    public void usesOnePlayerScopedFacetPipelineWithDisjointSeasonAndRangeBranches() {
        MatchFacetQuery query = new MatchFacetQuery(
            LeagueShard.EUW1,
            List.of(GameQueueType.TEAM_BUILDER_RANKED_SOLO, GameQueueType.RANKED_FLEX_SR),
            List.of(103, 238),
            List.of(LaneType.MID),
            List.of("16.20"),
            1_000,
            2_000,
            null,
            List.of(
                new MatchFacetQuery.Season("current", 2026, 10L, 20L),
                new MatchFacetQuery.Season("previous", 2025, 1L, 9L)
            )
        );

        List<Bson> pipeline = MongoDB.matchFacetsPipeline("player", query);
        BsonDocument scope = pipeline.get(0).toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());
        BsonDocument facets = pipeline.get(1).toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry())
            .getDocument("$facet");
        String total = facets.getArray("total").toString();
        String queue = facets.getArray("queue").toString();
        String seasons = facets.getArray("season").toString();
        String range = facets.getArray("range").toString();

        assertEquals(List.of("puuid", "region"), List.copyOf(scope.getDocument("$match").keySet()));
        assertEquals(7, facets.size());
        assertTrue(total.contains("TEAM_BUILDER_RANKED_SOLO"));
        assertTrue(total.contains("RANKED_FLEX_SR"));
        assertTrue(total.contains("1000"));
        assertFalse(queue.contains("TEAM_BUILDER_RANKED_SOLO"));
        assertTrue(queue.contains("103"));
        assertTrue(queue.contains("MID"));
        assertTrue(seasons.contains("10"));
        assertFalse(seasons.contains("1000"));
        assertFalse(range.contains("1000"));
        assertEquals(BsonArray.class, facets.getArray("champion").getClass());
    }

    @Test
    public void usesOrWithinDimensionsAndAndAcrossDimensions() {
        MatchFacetQuery query = new MatchFacetQuery(
            LeagueShard.EUW1,
            List.of(GameQueueType.TEAM_BUILDER_RANKED_SOLO, GameQueueType.RANKED_FLEX_SR),
            List.of(103, 238),
            List.of(LaneType.MID, LaneType.TOP),
            List.of("16.20", "16.19"),
            1_000,
            2_000,
            null,
            List.of()
        );
        BsonDocument facets = MongoDB.matchFacetsPipeline("player", query).get(1)
            .toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).getDocument("$facet");
        String total = facets.getArray("total").toString();
        String queueOptions = facets.getArray("queue").toString();
        String championOptions = facets.getArray("champion").toString();

        assertTrue(total.contains("TEAM_BUILDER_RANKED_SOLO"));
        assertTrue(total.contains("RANKED_FLEX_SR"));
        assertTrue(total.contains("103"));
        assertTrue(total.contains("238"));
        assertTrue(total.contains("MID"));
        assertTrue(total.contains("TOP"));
        assertTrue(total.contains("16.20"));
        assertTrue(total.contains("16.19"));
        assertFalse(queueOptions.contains("TEAM_BUILDER_RANKED_SOLO"));
        assertTrue(queueOptions.contains("103"));
        assertTrue(queueOptions.contains("MID"));
        assertTrue(championOptions.contains("TEAM_BUILDER_RANKED_SOLO"));
        assertTrue(championOptions.contains("16.19"));
        assertTrue(facets.getArray("queue").toString().contains("$sort"));
    }

    @Test
    public void preservesSelectedValuesAndDeterministicCountsWhenResultsAreEmpty() {
        MatchFacetQuery query = new MatchFacetQuery(
            LeagueShard.EUW1,
            List.of(GameQueueType.TEAM_BUILDER_RANKED_SOLO, GameQueueType.RANKED_FLEX_SR),
            List.of(238, 103),
            List.of(LaneType.MID, LaneType.TOP),
            List.of("16.20", "16.19"),
            1_000,
            2_000,
            null,
            List.of(new MatchFacetQuery.Season("current", 2026, 10L, 20L))
        );

        MatchFacets response = MongoDB.readMatchFacets(new Document(), query);

        assertEquals(0, response.total());
        assertEquals(new MatchFacets.Range(null, null), response.facets().range());
        assertEquals(List.of("RANKED_FLEX_SR", "TEAM_BUILDER_RANKED_SOLO"),
            response.facets().queue().stream().map(MatchFacets.Option::value).toList());
        assertTrue(response.facets().queue().stream().allMatch(option -> option.selected() && option.total() == 0));
        assertEquals(List.of(103, 238), response.facets().champion().stream().map(MatchFacets.Option::value).toList());
        assertTrue(response.facets().champion().stream().allMatch(option -> option.selected() && option.total() == 0));
        assertEquals(List.of("16.19", "16.20"), response.facets().patch().stream().map(MatchFacets.Option::value).toList());
        assertTrue(response.facets().patch().stream().allMatch(option -> option.selected() && option.total() == 0));
        assertEquals(0, response.facets().season().get(0).total());
    }

    @Test
    public void mapsKnownCountsAndMarksEverySelectedValue() {
        MatchFacetQuery query = new MatchFacetQuery(
            LeagueShard.EUW1,
            List.of(GameQueueType.TEAM_BUILDER_RANKED_SOLO, GameQueueType.RANKED_FLEX_SR),
            List.of(103, 238),
            List.of(LaneType.MID, LaneType.TOP),
            List.of("16.20"),
            10,
            20,
            "current",
            List.of(new MatchFacetQuery.Season("current", 2026, 10L, 20L),
                new MatchFacetQuery.Season("previous", 2025, 1L, 9L))
        );
        Document result = new Document("total", List.of(new Document("total", 8)))
            .append("queue", List.of(
                new Document("_id", "TEAM_BUILDER_RANKED_SOLO").append("total", 8),
                new Document("_id", "RANKED_FLEX_SR").append("total", 4)))
            .append("lane", List.of(new Document("_id", "MID").append("total", 8)))
            .append("champion", List.of(new Document("_id", 103).append("total", 8),
                new Document("_id", 238).append("total", 5)))
            .append("patch", List.of(new Document("_id", "16.20").append("total", 8)))
            .append("season", List.of(new Document("current", 8).append("previous", 4).append("all", 12)))
            .append("range", List.of(new Document("min", 10L).append("max", 20L)));

        MatchFacets response = MongoDB.readMatchFacets(result, query);

        assertEquals(8, response.total());
        assertEquals(List.of("TEAM_BUILDER_RANKED_SOLO", "RANKED_FLEX_SR"),
            response.facets().queue().stream().map(MatchFacets.Option::value).toList());
        assertEquals(List.of(8L, 4L), response.facets().queue().stream().map(MatchFacets.Option::total).toList());
        assertTrue(response.facets().queue().stream().allMatch(MatchFacets.Option::selected));
        assertEquals(List.of(103, 238), response.facets().champion().stream().map(MatchFacets.Option::value).toList());
        assertTrue(response.facets().champion().stream().allMatch(MatchFacets.Option::selected));
        assertTrue(response.facets().lane().get(0).selected());
        assertTrue(response.facets().patch().get(0).selected());
        assertTrue(response.facets().season().get(0).selected());
        assertFalse(response.facets().season().get(1).selected());
        assertFalse(response.facets().season().get(2).selected());
        assertEquals(new MatchFacets.Range(10L, 20L), response.facets().range());
    }

    @Test
    public void excludesMissingCategoricalValuesAndSortsCountsByTotalThenValue() {
        MatchFacetQuery query = new MatchFacetQuery(
            LeagueShard.EUW1, List.of(), List.of(), List.of(), List.of(), 0, 0, "all", List.of()
        );
        BsonDocument facets = MongoDB.matchFacetsPipeline("player", query).get(1)
            .toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).getDocument("$facet");
        String lane = facets.getArray("lane").toString();
        String patch = facets.getArray("patch").toString();

        assertTrue(lane.contains("$ne"));
        assertTrue(patch.contains("$ne"));
        assertTrue(lane.contains("$sort"));
        assertTrue(patch.contains("$sort"));
    }
}
