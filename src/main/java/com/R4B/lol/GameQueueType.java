// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.Stream;

import com.R4B.lol.*;

public enum GameQueueType{
    
    CUSTOM(0),
    
    NORMAL_3X3_BLIND_PICK_OLD(8),
    
    NORMAL_5X5_BLIND_PICK_OLD(1, 2),
    
    NORMAL_5X5_DRAFT(14),
    
    RANKED_SOLO_5X5(new Integer[]{4}, "RANKED_SOLO_5x5"),
    
    RANKED_PREMADE_5X5(6, 710),
    
    RANKED_PREMADE_3X3(9),
    
    RANKED_TEAM_3X3(41),
    
    RANKED_TEAM_5X5(42),
    
    ODIN_5X5_BLIND(16),
    
    ODIN_5X5_DRAFT(17),
    
    BOT_5X5(7),
    
    BOT_ODIN_5X5(25),
    
    BOT_5X5_INTRO_OLD(31, 34),
    
    BOT_5X5_BEGINNER_OLD(32, 33, 35),
    
    BOT_5X5_INTERMEDIATE_OLD(36),
    
    BOT_3X3_BEGINNER_OLD(52),
    
    GROUP_FINDER_5X5(61),
    
    ARAM_5X5_OLD(62, 63, 64, 65, 67),
    
    ONEFORALL_5X5(70, 1020, 1021, 1022, 1023, 1024),
    
    FIRSTBLOOD_1X1(72),
    
    FIRSTBLOOD_2X2(73),
    
    HEXAKILL_6X6_SR(75, 971),
    
    URF_5X5(76),
    
    ONE_FOR_ALL_MIRROR(78),
    
    BOT_URF_5X5(83),
    
    NIGHTMARE_BOT_5X5_RANK1(90, 91),
    
    NIGHTMARE_BOT_5X5_RANK2(92),
    
    NIGHTMARE_BOT_5X5_RANK5(93),
    
    ASCENSION_5X5(910, 911, 96, 97),
    
    HEXAKILL(970, 98),
    
    BILGEWATER_ARAM_5X5(100),
    
    KING_PORO_5X5(920, 921, 300, 301),
    
    COUNTER_PICK(310),
    
    BILGEWATER_5X5(313),
    
    NEXUS_SIEGE_OLD(315),
    
    DEFINITELY_NOT_DOMINION_5X5(316, 317),
    
    ALL_RANDOM_URF(900, 901, 318, 319, 902, 903, 904),
    
    SNOW_BATTLE_ARURF(1010, 1011, 1012),
    
    OVERCHARGE(1000, 1001),
    
    ARAM_5X5(325, 324),
    
    TEAM_BUILDER_DRAFT_UNRANKED_5X5(400, 401, 402, 403),
    
    TEAM_BUILDER_DRAFT_RANKED_5X5(410, 411, 412, 413),
    
    TEAM_BUILDER_RANKED_SOLO(420, 421, 422),
    
    NORMAL_5V5_BLIND_PICK(430, 431, 432, 433),
    
    RANKED_FLEX_SR(440, 441, 442),
    
    ARAM(450, 451, 452, 930, 931),
    
    NORMAL_3X3_BLIND_PICK(460, 461),
    
    RANKED_FLEX_TT(470),
    
    ASSASSINATE_5X5(320, 321, 600, 601),
    
    DARKSTAR_3X3(610, 611),
    
    CLASH(700, 701, 721),
    
    ARURF_CLASH(740, 741),
    
    BOT_3X3_INTERMEDIATE(800, 801),
    
    BOT_3X3_INTRO(810),
    
    BOT_3X3_BEGINNER(820),
    
    BOT_5X5_INTRO(830, 831, 832, 870),
    
    BOT_5X5_BEGINNER(840, 841, 842, 880),
    
    BOT_5X5_INTERMEDIATE(850, 851, 852, 890),
    
    NEXUS_SIEGE(940, 941, 314, 315),
    
    NIGHTMARE_BOT_5X5_VOTE(950, 951),
    
    NIGHTMARE_BOT_5X5(960, 961),
    
    INVASION_NORMAL(980, 981, 982),
    
    INVASION_ONSLAUGHT(990),
    
    NEXUS_BLITZ(1200, 1201, 1300, 1301, 1302, 1303, 1304),
    
    ODYSSEY_INTRO(1030, 1031, 1032),
    
    ODYSSEY_CADET(1040, 1041),
    
    ODYSSEY_CREWMEMBER(1050, 1051),
    
    ODYSSEY_CAPTAIN(1060, 1061),
    
    ODYSSEY_ONSLAUGHT(1070, 1071),
    
    STRAWBERRY(1810, 1820, 1830, 1840),
    
    TUTORIAL_MODULE_1(2000),
    TUTORIAL_MODULE_2(2010),
    TUTORIAL_MODULE_3(2020),

    TEAMFIGHT_TACTICS(1090, 1091, 1092, 1093, 1094, 2200),

    TEAMFIGHT_TACTICS_RANKED(new Integer[]{1100, 1101, 1102}, "RANKED_TFT"),
    
    TEAMFIGHT_TACTICS_TUTORIAL(1110),
    
    TEAMFIGHT_TACTICS_SIMULATION(1111),
    
    TEAMFIGHT_TACTICS_HYPER_ROLL(new Integer[]{1120, 1130}, "RANKED_TFT_TURBO"),
    
    TEAMFIGHT_TACTICS_HYPER_ROLL_1V0(1121, 1131, 1122, 1132),

    TEAMFIGHT_TACTICS_DOUBLE_UP_2V0(1142, 1151, 1161),

    TEAMFIGHT_TACTICS_DOUBLE_UP_4V0(1143, 1152, 1162),

    TEAMFIGHT_TACTICS_DOUBLE_UP(new Integer[]{1140, 1150}, "RANKED_TFT_PAIRS"),

    TEAMFIGHT_TACTICS_DOUBLE_UP_1V7_BOTS(1141),
    
    TEAMFIGHT_TACTICS_DOUBLE_UP_WORKSHOP(new Integer[]{1160}, "RANKED_TFT_DOUBLE_UP"),
    
    TEAMFIGHT_TACTICS_FORTUNE_FAVOR(1170, 1171, 1172, 1175),
    
    TEAMFIGHT_TACTICS_SOUL_BRAWL(1180, 1181, 1182, 1185),
    
    TEAMFIGHT_TACTICS_CHONCC_TREASURE(1190, 1191, 1192, 1195, 1210),
    
    TEAMFIGHT_TACTICS_TOCKER_S_TRIALS(1220),
    
    TEAMFIGHT_TACTICS_PENGU_S_PARTY(6120, 6121, 6122, 6125),
    
    TEAMFIGHT_TACTICS_AO_SHINS_ASCENT(6130),
    
    TEAMFIGHT_TACTICS_SET3_5_REVIVAL_GALAXIES(6000, 6001, 6002, 6005),
    
    TEAMFIGHT_TACTICS_REVIVAL_DAWN_OF_HEROES(6110, 6111, 6112, 6115),
    
    TEAMFIGHT_TACTICS_REVIVAL_FESTIVAL_OF_BEASTS(6100, 6101, 6102, 6105),
    
    TEAMFIGHT_TACTICS_SET_QUEUE_1(6010, 6040, 6070),
    TEAMFIGHT_TACTICS_SET_QUEUE_2(6020, 6050, 6080),
    TEAMFIGHT_TACTICS_SET_QUEUE_3(6030, 6060, 6090),

    CHERRY(1700, 1701, 1704, 1710, 1740, 1750),

    BRAWL(2300, 2301, 2302, 2303, 2304, 2305),

    URF_1V1(1901),
    
    URF_2V2(1902),
    
    URF_3V3(1903),
    
    URF_4V4(1904),
    
    URF(1900),
    
    ULTBOOK(1400),
    
    KIWI(2400),
    
    ONE_VS_ONE(1401),
    TWO_VS_TWO(1402),
    THREE_VS_THREE(1403),
    FOUR_VS_FOUR(1404),
    
    TFT_CUSTOM(3000),
    TFT_HYPER_ROLL_CUSTOM(3010),
    
    ARAM_CLASH(720),
    ARAM_BOTS(860),
    
    QUICKPLAY_NORMAL(490),
    
    SWIFTPLAY(480),

    DOOMBOTS_V2(4210, 4220, 4240, 4250, 4260),
    
    PRACTICE_TOOL(3140),
    
    CUSTOM_SUMMONERS_RIFT(3100, 3110, 3120, 3130),
    CUSTOM_ARAM(3200, 3210, 3220, 3230, 3270),

    JADE(4300, 4301, 4302, 4303, 4304, 4305, 4306, 4307, 4308, 4309, 4310, 4311),
    
    JADE_RANKED_SOLO_5X5(new Integer[]{}, "JADE_RANKED_SOLO_5x5"),
    
    JADE_BOT(4320, 4321),
    
    CUSTOM_JADE(3260, 3261, 3262),

    KIWI_JADE(2450),
    
    CUSTOM_KIWI_JADE(3280),

    ;
    
    private final Integer[] codes;
    private final String    apiname;
    
    GameQueueType(final Integer... codes)
    {
        this(codes, "");
    }
    
    GameQueueType(final Integer[] codes, final String apiName)
    {
        this.codes = codes;
        this.apiname = apiName;
    }
    
    public GameQueueType getFromCode(String value)
    {
        try
        {
            
            if (value.contains("."))
            {
                value = value.substring(0, value.indexOf("."));
            }
            
            return getFromId(Integer.parseInt(value));
        } catch (NumberFormatException e)
        {
            return getFromString(value);
        }
    }

    public String prettyName()
    {
        switch (this)
        {
            
            case CUSTOM -> { return "Custom"; }
            case NORMAL_3X3_BLIND_PICK_OLD -> { return "3v3 Blind Pick"; }
            case NORMAL_5X5_BLIND_PICK_OLD, NORMAL_5V5_BLIND_PICK -> { return "5v5 Blind Pick"; }
            case NORMAL_5X5_DRAFT -> { return "5v5 Draft Pick"; }
            case RANKED_SOLO_5X5 -> { return "5v5 Ranked Solo"; }
            case RANKED_PREMADE_5X5 -> { return "5v5 Ranked Duo"; }
            case RANKED_PREMADE_3X3 -> { return "3v3 Ranked Duo"; }
            case RANKED_TEAM_3X3 -> { return "3v3 Ranked Team"; }
            case RANKED_TEAM_5X5 -> { return "5v5 Ranked Team"; }
            case ODIN_5X5_BLIND -> { return "Dominion Blind Pick"; }
            case ODIN_5X5_DRAFT -> { return "Dominion Draft Pick"; }
            case BOT_5X5 -> { return "Summoners Rift Bots"; }
            case BOT_ODIN_5X5 -> { return "Dominion Bots"; }
            case BOT_5X5_INTRO_OLD -> { return "Summoners Rift Bots Intro Difficulty"; }
            case BOT_5X5_BEGINNER_OLD -> { return "Summoners Rift Bots Beginner Difficulty"; }
            case BOT_5X5_INTERMEDIATE_OLD -> { return "Summoners Rift Bots Intermediate Difficulty"; }
            case BOT_3X3_BEGINNER_OLD -> { return "Twisted Treeline Bots Beginner Difficulty"; }
            case GROUP_FINDER_5X5 -> { return "Teambuilder"; }
            case ARAM_5X5_OLD -> { return "ARAM"; }
            case ONEFORALL_5X5 -> { return "One for All"; }
            case FIRSTBLOOD_1X1 -> { return "1v1 Snowdown Showdown"; }
            case FIRSTBLOOD_2X2 -> { return "2v2 Snowdown Showdown"; }
            case HEXAKILL_6X6_SR -> { return "Summoners Rift Hexakill"; }
            case URF_1V1, URF_2V2, URF_3V3, URF_4V4, URF_5X5, URF -> { return "URF"; }
            case ONE_FOR_ALL_MIRROR -> { return "One for All Mirror Mode"; }
            case BOT_URF_5X5 -> { return "URF Bots"; }
            case NIGHTMARE_BOT_5X5_RANK1 -> { return "Doombots Rank 1"; }
            case NIGHTMARE_BOT_5X5_RANK2 -> { return "Doombots Rank 2"; }
            case NIGHTMARE_BOT_5X5_RANK5 -> { return "Doombots Rank 5"; }
            case ASCENSION_5X5 -> { return "Ascension"; }
            case HEXAKILL -> { return "Hexakill"; }
            case BILGEWATER_ARAM_5X5 -> { return "ARAM Butchers Bridge Version"; }
            case KING_PORO_5X5 -> { return "King Poro"; }
            case COUNTER_PICK -> { return "Nemesis"; }
            case BILGEWATER_5X5 -> { return "Blackmarket Brawlers"; }
            case NEXUS_SIEGE_OLD -> { return "Nexus Siege"; }
            case DEFINITELY_NOT_DOMINION_5X5 -> { return "Definitely Not Dominion"; }
            case ALL_RANDOM_URF -> { return "ARURF"; }
            case SNOW_BATTLE_ARURF -> { return "ARURF Snow Skin"; }
            case OVERCHARGE -> { return "Overcharge"; }
            case ARAM_5X5 -> { return "ARAM"; }
            case TEAM_BUILDER_DRAFT_UNRANKED_5X5 -> { return "5v5 Dynamic Queue"; }
            case TEAM_BUILDER_DRAFT_RANKED_5X5 -> { return "5v5 Dynamic Ranked Queue"; }
            case TEAM_BUILDER_RANKED_SOLO -> { return "5v5 Dynamic Ranked Solo Queue"; }
            case RANKED_FLEX_SR -> { return "5v5 Ranked Flex Queue"; }
            case ARAM -> { return "ARAM"; }
            case NORMAL_3X3_BLIND_PICK -> { return "3v3 Normal Flex Queue"; }
            case RANKED_FLEX_TT -> { return "3v3 Ranked Flex Queue"; }
            case ASSASSINATE_5X5 -> { return "Blood Hunt Assassin"; }
            case DARKSTAR_3X3 -> { return "Darkstar"; }
            case CLASH -> { return "Clash"; }
              case ARURF_CLASH -> { return "ARURF Clash"; }
            case BOT_3X3_INTERMEDIATE -> { return "3v3 Bots Intermediate Difficulty"; }
            case BOT_3X3_INTRO -> { return "3v3 Bots Intro Difficulty"; }
            case BOT_3X3_BEGINNER -> { return "3v3 Bots Beginner Difficulty"; }
            case BOT_5X5_INTRO -> { return "5v5 Bots Intro Difficulty"; }
            case BOT_5X5_BEGINNER -> { return "5v5 Bots Beginner Difficulty"; }
            case BOT_5X5_INTERMEDIATE -> { return "5v5 Bots Intermediate Difficulty"; }
            case NEXUS_SIEGE -> { return "Nexus Siege"; }
            case NIGHTMARE_BOT_5X5_VOTE -> { return "Doombots Vote Difficulty"; }
            case NIGHTMARE_BOT_5X5 -> { return "Doombots"; }
            case INVASION_NORMAL -> { return "Invasion Normal Difficulty"; }
            case INVASION_ONSLAUGHT -> { return "Invasion Onslaught Difficulty"; }
            case NEXUS_BLITZ -> { return "Nexus Blitz"; }
            case ODYSSEY_INTRO -> { return "Odyssey Intro Difficulty"; }
            case ODYSSEY_CADET -> { return "Odyssey Cadet Difficulty"; }
            case ODYSSEY_CREWMEMBER -> { return "Odyssey Crewmember Difficulty"; }
            case ODYSSEY_CAPTAIN -> { return "Odyssey Captain Difficulty"; }
            case ODYSSEY_ONSLAUGHT -> { return "Odyssey Onslaught Difficulty"; }
            case TUTORIAL_MODULE_1 -> { return "Tutorial Part 1"; }
            case TUTORIAL_MODULE_2 -> { return "Tutorial Part 2"; }
            case TUTORIAL_MODULE_3 -> { return "Tutorial Part 3"; }
            case TEAMFIGHT_TACTICS -> { return "Teamfight Tactics"; }
            case TEAMFIGHT_TACTICS_RANKED -> { return "Teamfight Tactics Ranked"; }
            case TEAMFIGHT_TACTICS_TUTORIAL -> { return "Teamfight Tactics Tutorial"; }
            case TEAMFIGHT_TACTICS_HYPER_ROLL -> { return "Teamfight Tactics Hyper Roll"; }
            case ARAM_CLASH -> { return "ARAM Clash"; }
            case QUICKPLAY_NORMAL -> { return "Quickplay"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public String commonName()
    {
        switch (this)
        {
            
            case CUSTOM -> { return "Custom"; }
            case NORMAL_3X3_BLIND_PICK_OLD, RANKED_FLEX_TT -> { return "3v3 Blind Pick"; }
            case NORMAL_5X5_BLIND_PICK_OLD, NORMAL_5V5_BLIND_PICK -> { return "5v5 Blind Pick"; }
            case NORMAL_5X5_DRAFT, TEAM_BUILDER_DRAFT_UNRANKED_5X5 -> { return "5v5 Draft Pick"; }
            case RANKED_SOLO_5X5, TEAM_BUILDER_RANKED_SOLO, TEAM_BUILDER_DRAFT_RANKED_5X5 -> { return "5v5 Ranked Solo"; }
            case RANKED_PREMADE_5X5 -> { return "5v5 Ranked Duo"; }
            case RANKED_PREMADE_3X3 -> { return "3v3 Ranked Duo"; }
            case RANKED_TEAM_3X3 -> { return "3v3 Ranked Team"; }
            case RANKED_TEAM_5X5 -> { return "5v5 Ranked Team"; }
            case ODIN_5X5_BLIND -> { return "Dominion Blind Pick"; }
            case ODIN_5X5_DRAFT -> { return "Dominion"; }
            case BOT_5X5, BOT_5X5_INTRO_OLD, BOT_5X5_BEGINNER_OLD, BOT_5X5_INTERMEDIATE_OLD, BOT_5X5_INTRO, BOT_5X5_BEGINNER, BOT_5X5_INTERMEDIATE -> { return "VS AI"; }
            case BOT_ODIN_5X5 -> { return "Dominion VS AI"; }
            case BOT_3X3_BEGINNER_OLD, BOT_3X3_INTERMEDIATE, BOT_3X3_INTRO, BOT_3X3_BEGINNER -> { return "Twisted Treeline VS AI"; }
            case GROUP_FINDER_5X5 -> { return "Teambuilder"; }
            case ARAM_5X5, ARAM_5X5_OLD, BILGEWATER_ARAM_5X5 -> { return "ARAM"; }
            case ONEFORALL_5X5 -> { return "One for All"; }
            case FIRSTBLOOD_1X1 -> { return "1v1 Snowdown Showdown"; }
            case FIRSTBLOOD_2X2 -> { return "2v2 Snowdown Showdown"; }
            case HEXAKILL, HEXAKILL_6X6_SR -> { return "Hexakill"; }
            case URF_1V1, URF_2V2, URF_3V3, URF_4V4, URF_5X5, URF -> { return "URF"; }
            case ONE_FOR_ALL_MIRROR -> { return "One for All Mirror Mode"; }
            case BOT_URF_5X5 -> { return "URF VS AI"; }
            case NIGHTMARE_BOT_5X5_RANK1, NIGHTMARE_BOT_5X5_RANK2, NIGHTMARE_BOT_5X5_RANK5 -> { return "Doombots"; }
            case ASCENSION_5X5 -> { return "Ascension"; }
            case KING_PORO_5X5 -> { return "King Poro"; }
            case COUNTER_PICK -> { return "Nemesis"; }
            case BILGEWATER_5X5 -> { return "Blackmarket Brawlers"; }
            case NEXUS_SIEGE_OLD -> { return "Nexus Siege"; }
            case DEFINITELY_NOT_DOMINION_5X5 -> { return "Definitely Not Dominion"; }
            case ALL_RANDOM_URF, SNOW_BATTLE_ARURF -> { return "ARURF"; }
            case OVERCHARGE -> { return "Overcharge"; }
            case RANKED_FLEX_SR -> { return "5v5 Ranked Flex Queue"; }
            case ARAM -> { return "ARAM"; }
            case NORMAL_3X3_BLIND_PICK -> { return "3v3 Normal Flex Queue"; }
            case ASSASSINATE_5X5 -> { return "Blood Hunt Assassin"; }
            case DARKSTAR_3X3 -> { return "Darkstar"; }
            case CLASH -> { return "Clash"; }
            case NEXUS_SIEGE -> { return "Nexus Siege"; }
            case NIGHTMARE_BOT_5X5_VOTE, NIGHTMARE_BOT_5X5 -> { return "Doombots"; }
            case INVASION_NORMAL, INVASION_ONSLAUGHT -> { return "Invasion"; }
            case NEXUS_BLITZ -> { return "Nexus Blitz"; }
            case ODYSSEY_INTRO, ODYSSEY_CADET, ODYSSEY_CREWMEMBER, ODYSSEY_CAPTAIN, ODYSSEY_ONSLAUGHT -> { return "Odyssey"; }
            case TUTORIAL_MODULE_1, TUTORIAL_MODULE_2, TUTORIAL_MODULE_3 -> { return "Tutorial"; }
            case TEAMFIGHT_TACTICS, TEAMFIGHT_TACTICS_RANKED, TEAMFIGHT_TACTICS_TUTORIAL, TEAMFIGHT_TACTICS_HYPER_ROLL -> { return "Teamfight Tactics"; }
            case ARAM_CLASH -> { return "ARAM Clash"; }
            case QUICKPLAY_NORMAL -> { return "Quickplay"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }
    
    public static GameQueueType getFromString(final String value)
    {
        return Stream.of(GameQueueType.values()).filter(t -> t.name().equalsIgnoreCase(value) || t.apiname.equalsIgnoreCase(value)).findFirst().orElse(null);
    }
    
    public static GameQueueType getFromId(final int value)
    {
        return Stream.of(GameQueueType.values()).filter(t -> Arrays.asList(t.codes).contains(value)).findFirst().orElse(null);
    }
    
    public String getApiName()
    {
        return "".equals(apiname) ? this.name() : apiname;
    }
    
    public Integer[] getValues()
    {
        return this.codes;
    }

    public static GameQueueType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }

  private static final List<GameQueueType> QUEUES_WITHOUT_LANE = Arrays.asList(
    GameQueueType.CHERRY,
    GameQueueType.ULTBOOK,
    GameQueueType.URF,
    GameQueueType.ALL_RANDOM_URF,
    GameQueueType.DOOMBOTS_V2,
    GameQueueType.ONEFORALL_5X5,
    GameQueueType.ARAM,
    GameQueueType.ARAM_CLASH,
    GameQueueType.NEXUS_BLITZ,
    GameQueueType.STRAWBERRY
  );
  private static final List<GameQueueType> LEADERBOARD_QUEUES = List.of(
    GameQueueType.RANKED_SOLO_5X5,
    GameQueueType.RANKED_FLEX_SR
  );

  private static final List<GameQueueType> RANKED_QUEUES = List.of(
    GameQueueType.RANKED_SOLO_5X5,
    GameQueueType.RANKED_FLEX_SR,
    GameQueueType.RANKED_PREMADE_5X5
  );

  public static boolean hasLane(GameQueueType queue) {
    return !QUEUES_WITHOUT_LANE.contains(queue);
  }

  public static boolean isCherry(GameQueueType queue) {
    return queue == GameQueueType.CHERRY;
  }

  public static GameQueueType canonicalQueue(GameQueueType queue) {
    return queue == GameQueueType.TEAM_BUILDER_RANKED_SOLO
        ? GameQueueType.RANKED_SOLO_5X5
        : queue;
  }

  public static GameQueueType rankedCanonicalQueue(GameQueueType queue) {
    GameQueueType canonical = canonicalQueue(queue);
    if (canonical == null) return null;
    if (!RANKED_QUEUES.contains(canonical) || canonical == GameQueueType.RANKED_SOLO_5X5) return GameQueueType.RANKED_SOLO_5X5;
    return canonical;
  }

  public static GameQueueType defaultQueue(GameQueueType queue) {
    return queue == null ? GameQueueType.RANKED_SOLO_5X5 : queue;
  }

  public static boolean isRankedSolo(GameQueueType queue) {
    return canonicalQueue(queue) == GameQueueType.RANKED_SOLO_5X5;
  }

  public static List<GameQueueType> leaderboardQueues() {
    return LEADERBOARD_QUEUES;
  }}
