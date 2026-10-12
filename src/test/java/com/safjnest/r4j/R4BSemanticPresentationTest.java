package com.safjnest.r4j;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.R4B.lol.CanonicalQueue;
import com.R4B.lol.GameQueueType;
import com.R4B.lol.LaneType;
import com.R4B.lol.TierDivisionType;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.lol.utils.TierDivisionUtils;

public class R4BSemanticPresentationTest {
    private static final String BASELINE = """
        queue|CUSTOM|Custom
        queue|NORMAL_3X3_BLIND_PICK_OLD|Normal 3x3 Blind Pick Old
        queue|NORMAL_5X5_BLIND_PICK_OLD|Normal 5x5 Blind Pick Old
        queue|NORMAL_5X5_DRAFT|Draft Pick
        queue|RANKED_SOLO_5X5|Ranked Solo/Duo
        queue|RANKED_PREMADE_5X5|Ranked Premade 5x5
        queue|RANKED_PREMADE_3X3|Ranked Premade 3x3
        queue|RANKED_TEAM_3X3|Ranked Team 3x3
        queue|RANKED_TEAM_5X5|Ranked Team 5x5
        queue|ODIN_5X5_BLIND|Odin 5x5 Blind
        queue|ODIN_5X5_DRAFT|Odin 5x5 Draft
        queue|BOT_5X5|Bot 5x5
        queue|BOT_ODIN_5X5|Bot Odin 5x5
        queue|BOT_5X5_INTRO_OLD|Bot 5x5 Intro Old
        queue|BOT_5X5_BEGINNER_OLD|Bot 5x5 Beginner Old
        queue|BOT_5X5_INTERMEDIATE_OLD|Bot 5x5 Intermediate Old
        queue|BOT_3X3_BEGINNER_OLD|Bot 3x3 Beginner Old
        queue|GROUP_FINDER_5X5|Group Finder 5x5
        queue|ARAM_5X5_OLD|Aram 5x5 Old
        queue|ONEFORALL_5X5|Oneforall 5x5
        queue|FIRSTBLOOD_1X1|Firstblood 1x1
        queue|FIRSTBLOOD_2X2|Firstblood 2x2
        queue|HEXAKILL_6X6_SR|Hexakill 6x6 Sr
        queue|URF_5X5|Urf 5x5
        queue|ONE_FOR_ALL_MIRROR|One For All Mirror
        queue|BOT_URF_5X5|Bot Urf 5x5
        queue|NIGHTMARE_BOT_5X5_RANK1|Nightmare Bot 5x5 Rank1
        queue|NIGHTMARE_BOT_5X5_RANK2|Nightmare Bot 5x5 Rank2
        queue|NIGHTMARE_BOT_5X5_RANK5|Nightmare Bot 5x5 Rank5
        queue|ASCENSION_5X5|Ascension 5x5
        queue|HEXAKILL|Hexakill
        queue|BILGEWATER_ARAM_5X5|Bilgewater Aram 5x5
        queue|KING_PORO_5X5|King Poro 5x5
        queue|COUNTER_PICK|Counter Pick
        queue|BILGEWATER_5X5|Bilgewater 5x5
        queue|NEXUS_SIEGE_OLD|Nexus Siege Old
        queue|DEFINITELY_NOT_DOMINION_5X5|Definitely Not Dominion 5x5
        queue|ALL_RANDOM_URF|URF
        queue|SNOW_BATTLE_ARURF|Snow Battle Arurf
        queue|OVERCHARGE|Overcharge
        queue|ARAM_5X5|Aram 5x5
        queue|TEAM_BUILDER_DRAFT_UNRANKED_5X5|Draft Pick
        queue|TEAM_BUILDER_DRAFT_RANKED_5X5|Ranked Solo/Duo
        queue|TEAM_BUILDER_RANKED_SOLO|Ranked Solo/Duo
        queue|NORMAL_5V5_BLIND_PICK|Normal 5v5 Blind Pick
        queue|RANKED_FLEX_SR|Ranked Flex
        queue|ARAM|Aram
        queue|NORMAL_3X3_BLIND_PICK|Normal 3x3 Blind Pick
        queue|RANKED_FLEX_TT|Ranked Flex Tt
        queue|ASSASSINATE_5X5|Assassinate 5x5
        queue|DARKSTAR_3X3|Darkstar 3x3
        queue|CLASH|Clash
        queue|ARURF_CLASH|Arurf Clash
        queue|BOT_3X3_INTERMEDIATE|Bot 3x3 Intermediate
        queue|BOT_3X3_INTRO|Bot 3x3 Intro
        queue|BOT_3X3_BEGINNER|Bot 3x3 Beginner
        queue|BOT_5X5_INTRO|Bot 5x5 Intro
        queue|BOT_5X5_BEGINNER|Bot 5x5 Beginner
        queue|BOT_5X5_INTERMEDIATE|Bot 5x5 Intermediate
        queue|NEXUS_SIEGE|Nexus Siege
        queue|NIGHTMARE_BOT_5X5_VOTE|Nightmare Bot 5x5 Vote
        queue|NIGHTMARE_BOT_5X5|Nightmare Bot 5x5
        queue|INVASION_NORMAL|Invasion Normal
        queue|INVASION_ONSLAUGHT|Invasion Onslaught
        queue|NEXUS_BLITZ|Nexus Blitz
        queue|ODYSSEY_INTRO|Odyssey Intro
        queue|ODYSSEY_CADET|Odyssey Cadet
        queue|ODYSSEY_CREWMEMBER|Odyssey Crewmember
        queue|ODYSSEY_CAPTAIN|Odyssey Captain
        queue|ODYSSEY_ONSLAUGHT|Odyssey Onslaught
        queue|STRAWBERRY|Swarm
        queue|TUTORIAL_MODULE_1|Tutorial Module 1
        queue|TUTORIAL_MODULE_2|Tutorial Module 2
        queue|TUTORIAL_MODULE_3|Tutorial Module 3
        queue|TEAMFIGHT_TACTICS|Teamfight Tactics
        queue|TEAMFIGHT_TACTICS_RANKED|Teamfight Tactics Ranked
        queue|TEAMFIGHT_TACTICS_TUTORIAL|Teamfight Tactics Tutorial
        queue|TEAMFIGHT_TACTICS_SIMULATION|Teamfight Tactics Simulation
        queue|TEAMFIGHT_TACTICS_HYPER_ROLL|Teamfight Tactics Hyper Roll
        queue|TEAMFIGHT_TACTICS_HYPER_ROLL_1V0|Teamfight Tactics Hyper Roll 1v0
        queue|TEAMFIGHT_TACTICS_DOUBLE_UP_2V0|Teamfight Tactics Double Up 2v0
        queue|TEAMFIGHT_TACTICS_DOUBLE_UP_4V0|Teamfight Tactics Double Up 4v0
        queue|TEAMFIGHT_TACTICS_DOUBLE_UP|Teamfight Tactics Double Up
        queue|TEAMFIGHT_TACTICS_DOUBLE_UP_1V7_BOTS|Teamfight Tactics Double Up 1v7 Bots
        queue|TEAMFIGHT_TACTICS_DOUBLE_UP_WORKSHOP|Teamfight Tactics Double Up Workshop
        queue|TEAMFIGHT_TACTICS_FORTUNE_FAVOR|Teamfight Tactics Fortune Favor
        queue|TEAMFIGHT_TACTICS_SOUL_BRAWL|Teamfight Tactics Soul Brawl
        queue|TEAMFIGHT_TACTICS_CHONCC_TREASURE|Teamfight Tactics Choncc Treasure
        queue|TEAMFIGHT_TACTICS_TOCKER_S_TRIALS|Teamfight Tactics Tocker S Trials
        queue|TEAMFIGHT_TACTICS_PENGU_S_PARTY|Teamfight Tactics Pengu S Party
        queue|TEAMFIGHT_TACTICS_AO_SHINS_ASCENT|Teamfight Tactics Ao Shins Ascent
        queue|TEAMFIGHT_TACTICS_SET3_5_REVIVAL_GALAXIES|Teamfight Tactics Set3 5 Revival Galaxies
        queue|TEAMFIGHT_TACTICS_REVIVAL_DAWN_OF_HEROES|Teamfight Tactics Revival Dawn Of Heroes
        queue|TEAMFIGHT_TACTICS_REVIVAL_FESTIVAL_OF_BEASTS|Teamfight Tactics Revival Festival Of Beasts
        queue|TEAMFIGHT_TACTICS_SET_QUEUE_1|Teamfight Tactics Set Queue 1
        queue|TEAMFIGHT_TACTICS_SET_QUEUE_2|Teamfight Tactics Set Queue 2
        queue|TEAMFIGHT_TACTICS_SET_QUEUE_3|Teamfight Tactics Set Queue 3
        queue|CHERRY|Arena
        queue|BRAWL|Brawl
        queue|URF_1V1|Urf 1v1
        queue|URF_2V2|Urf 2v2
        queue|URF_3V3|Urf 3v3
        queue|URF_4V4|Urf 4v4
        queue|URF|URF
        queue|ULTBOOK|Ultimate Spellbook
        queue|KIWI|Kiwi
        queue|ONE_VS_ONE|One Vs One
        queue|TWO_VS_TWO|Two Vs Two
        queue|THREE_VS_THREE|Three Vs Three
        queue|FOUR_VS_FOUR|Four Vs Four
        queue|TFT_CUSTOM|Tft Custom
        queue|TFT_HYPER_ROLL_CUSTOM|Tft Hyper Roll Custom
        queue|ARAM_CLASH|Aram Clash
        queue|ARAM_BOTS|Aram Bots
        queue|QUICKPLAY_NORMAL|Quickplay Normal
        queue|SWIFTPLAY|Swiftplay
        queue|DOOMBOTS_V2|DoomBots
        queue|PRACTICE_TOOL|Practice Tool
        queue|CUSTOM_SUMMONERS_RIFT|Custom Summoners Rift
        queue|CUSTOM_ARAM|Custom Aram
        queue|JADE|Jade
        queue|JADE_RANKED_SOLO_5X5|Jade Ranked Solo 5x5
        queue|JADE_BOT|Jade Bot
        queue|CUSTOM_JADE|Custom Jade
        queue|KIWI_JADE|Kiwi Jade
        queue|CUSTOM_KIWI_JADE|Custom Kiwi Jade
        lane|MID|Mid Lane
        lane|TOP|Top Lane
        lane|JUNGLE|Jungle
        lane|BOT|Bot Lane
        lane|UTILITY|Support
        lane|AFK|AFK
        lane|INVALID|INVALID
        lane|NONE|Remake Or NoLane
        rank|CHALLENGER_I|CH
        rank|GRANDMASTER_I|GM
        rank|MASTER_I|MS
        rank|DIAMOND_I|D1
        rank|DIAMOND_II|D2
        rank|DIAMOND_III|D3
        rank|DIAMOND_IV|D4
        rank|DIAMOND_V|D1
        rank|EMERALD_I|E1
        rank|EMERALD_II|E2
        rank|EMERALD_III|E3
        rank|EMERALD_IV|E4
        rank|EMERALD_V|E1
        rank|PLATINUM_I|P1
        rank|PLATINUM_II|P2
        rank|PLATINUM_III|P3
        rank|PLATINUM_IV|P4
        rank|PLATINUM_V|P1
        rank|GOLD_I|G1
        rank|GOLD_II|G2
        rank|GOLD_III|G3
        rank|GOLD_IV|G4
        rank|GOLD_V|G1
        rank|SILVER_I|S1
        rank|SILVER_II|S2
        rank|SILVER_III|S3
        rank|SILVER_IV|S4
        rank|SILVER_V|S1
        rank|BRONZE_I|B1
        rank|BRONZE_II|B2
        rank|BRONZE_III|B3
        rank|BRONZE_IV|B4
        rank|BRONZE_V|B1
        rank|IRON_I|I1
        rank|IRON_II|I2
        rank|IRON_III|I3
        rank|IRON_IV|I4
        rank|IRON_V|I1
        rank|UNRANKED|U
        """;

    private static final String STATISTICS_QUEUES = """
        CUSTOM|OTHER
        NORMAL_3X3_BLIND_PICK_OLD|OTHER
        NORMAL_5X5_BLIND_PICK_OLD|NORMAL_BLIND
        NORMAL_5X5_DRAFT|NORMAL_DRAFT
        RANKED_SOLO_5X5|RANKED_SOLO
        RANKED_PREMADE_5X5|OTHER
        RANKED_PREMADE_3X3|OTHER
        RANKED_TEAM_3X3|OTHER
        RANKED_TEAM_5X5|OTHER
        ODIN_5X5_BLIND|OTHER
        ODIN_5X5_DRAFT|OTHER
        BOT_5X5|OTHER
        BOT_ODIN_5X5|OTHER
        BOT_5X5_INTRO_OLD|OTHER
        BOT_5X5_BEGINNER_OLD|OTHER
        BOT_5X5_INTERMEDIATE_OLD|OTHER
        BOT_3X3_BEGINNER_OLD|OTHER
        GROUP_FINDER_5X5|OTHER
        ARAM_5X5_OLD|ARAM
        ONEFORALL_5X5|SPECIAL
        FIRSTBLOOD_1X1|OTHER
        FIRSTBLOOD_2X2|OTHER
        HEXAKILL_6X6_SR|OTHER
        URF_5X5|URF
        ONE_FOR_ALL_MIRROR|OTHER
        BOT_URF_5X5|OTHER
        NIGHTMARE_BOT_5X5_RANK1|OTHER
        NIGHTMARE_BOT_5X5_RANK2|OTHER
        NIGHTMARE_BOT_5X5_RANK5|OTHER
        ASCENSION_5X5|OTHER
        HEXAKILL|OTHER
        BILGEWATER_ARAM_5X5|OTHER
        KING_PORO_5X5|OTHER
        COUNTER_PICK|OTHER
        BILGEWATER_5X5|OTHER
        NEXUS_SIEGE_OLD|OTHER
        DEFINITELY_NOT_DOMINION_5X5|OTHER
        ALL_RANDOM_URF|URF
        SNOW_BATTLE_ARURF|URF
        OVERCHARGE|OTHER
        ARAM_5X5|ARAM
        TEAM_BUILDER_DRAFT_UNRANKED_5X5|NORMAL_DRAFT
        TEAM_BUILDER_DRAFT_RANKED_5X5|RANKED_FLEX
        TEAM_BUILDER_RANKED_SOLO|RANKED_SOLO
        NORMAL_5V5_BLIND_PICK|NORMAL_BLIND
        RANKED_FLEX_SR|RANKED_FLEX
        ARAM|ARAM
        NORMAL_3X3_BLIND_PICK|OTHER
        RANKED_FLEX_TT|OTHER
        ASSASSINATE_5X5|OTHER
        DARKSTAR_3X3|OTHER
        CLASH|OTHER
        ARURF_CLASH|OTHER
        BOT_3X3_INTERMEDIATE|OTHER
        BOT_3X3_INTRO|OTHER
        BOT_3X3_BEGINNER|OTHER
        BOT_5X5_INTRO|OTHER
        BOT_5X5_BEGINNER|OTHER
        BOT_5X5_INTERMEDIATE|OTHER
        NEXUS_SIEGE|OTHER
        NIGHTMARE_BOT_5X5_VOTE|OTHER
        NIGHTMARE_BOT_5X5|OTHER
        INVASION_NORMAL|OTHER
        INVASION_ONSLAUGHT|OTHER
        NEXUS_BLITZ|NEXUS_BLITZ
        ODYSSEY_INTRO|OTHER
        ODYSSEY_CADET|OTHER
        ODYSSEY_CREWMEMBER|OTHER
        ODYSSEY_CAPTAIN|OTHER
        ODYSSEY_ONSLAUGHT|OTHER
        STRAWBERRY|SWARM
        TUTORIAL_MODULE_1|OTHER
        TUTORIAL_MODULE_2|OTHER
        TUTORIAL_MODULE_3|OTHER
        TEAMFIGHT_TACTICS|OTHER
        TEAMFIGHT_TACTICS_RANKED|OTHER
        TEAMFIGHT_TACTICS_TUTORIAL|OTHER
        TEAMFIGHT_TACTICS_SIMULATION|OTHER
        TEAMFIGHT_TACTICS_HYPER_ROLL|OTHER
        TEAMFIGHT_TACTICS_HYPER_ROLL_1V0|OTHER
        TEAMFIGHT_TACTICS_DOUBLE_UP_2V0|OTHER
        TEAMFIGHT_TACTICS_DOUBLE_UP_4V0|OTHER
        TEAMFIGHT_TACTICS_DOUBLE_UP|OTHER
        TEAMFIGHT_TACTICS_DOUBLE_UP_1V7_BOTS|OTHER
        TEAMFIGHT_TACTICS_DOUBLE_UP_WORKSHOP|OTHER
        TEAMFIGHT_TACTICS_FORTUNE_FAVOR|OTHER
        TEAMFIGHT_TACTICS_SOUL_BRAWL|OTHER
        TEAMFIGHT_TACTICS_CHONCC_TREASURE|OTHER
        TEAMFIGHT_TACTICS_TOCKER_S_TRIALS|OTHER
        TEAMFIGHT_TACTICS_PENGU_S_PARTY|OTHER
        TEAMFIGHT_TACTICS_AO_SHINS_ASCENT|OTHER
        TEAMFIGHT_TACTICS_SET3_5_REVIVAL_GALAXIES|OTHER
        TEAMFIGHT_TACTICS_REVIVAL_DAWN_OF_HEROES|OTHER
        TEAMFIGHT_TACTICS_REVIVAL_FESTIVAL_OF_BEASTS|OTHER
        TEAMFIGHT_TACTICS_SET_QUEUE_1|OTHER
        TEAMFIGHT_TACTICS_SET_QUEUE_2|OTHER
        TEAMFIGHT_TACTICS_SET_QUEUE_3|OTHER
        CHERRY|ARENA
        BRAWL|SPECIAL
        URF_1V1|OTHER
        URF_2V2|OTHER
        URF_3V3|OTHER
        URF_4V4|OTHER
        URF|URF
        ULTBOOK|ULTBOOK
        KIWI|SPECIAL
        ONE_VS_ONE|OTHER
        TWO_VS_TWO|OTHER
        THREE_VS_THREE|OTHER
        FOUR_VS_FOUR|OTHER
        TFT_CUSTOM|OTHER
        TFT_HYPER_ROLL_CUSTOM|OTHER
        ARAM_CLASH|ARAM
        ARAM_BOTS|ARAM
        QUICKPLAY_NORMAL|SWIFTPLAY
        SWIFTPLAY|SWIFTPLAY
        DOOMBOTS_V2|SPECIAL
        PRACTICE_TOOL|OTHER
        CUSTOM_SUMMONERS_RIFT|OTHER
        CUSTOM_ARAM|OTHER
        JADE|SPECIAL
        JADE_RANKED_SOLO_5X5|RANKED_SOLO
        JADE_BOT|OTHER
        CUSTOM_JADE|OTHER
        KIWI_JADE|OTHER
        CUSTOM_KIWI_JADE|OTHER
        """;

    @Test
    public void keepsEveryStatisticsQueueMappingIncludingHistoricalValues() {
        for (String line : STATISTICS_QUEUES.strip().split("\\R")) {
            String[] parts = line.split("\\|", -1);
            assertEquals(parts[0], parts[1], CanonicalQueue.from(GameQueueType.valueOf(parts[0])).name());
        }
        assertEquals(CanonicalQueue.OTHER, CanonicalQueue.from(null));
    }

    @Test
    public void keepsEveryBotQueueLaneAndCompactRankLabel() {
        for (String line : BASELINE.strip().split("\\R")) {
            String[] parts = line.split("\\|", -1);
            String actual = switch (parts[0]) {
                case "queue" -> GameQueueTypeUtils.prettyName(GameQueueType.valueOf(parts[1]));
                case "lane" -> LaneTypeUtils.getPrettyName(LaneType.valueOf(parts[1]));
                case "rank" -> TierDivisionUtils.getFormattedRank(TierDivisionType.valueOf(parts[1]), false);
                default -> throw new IllegalArgumentException(parts[0]);
            };
            assertEquals(parts[0] + " " + parts[1], parts[2], actual);
        }
    }
}
