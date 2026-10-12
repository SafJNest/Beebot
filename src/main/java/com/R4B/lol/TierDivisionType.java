// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.regex.Pattern;

import com.R4B.lol.*;

public enum TierDivisionType{
    CHALLENGER_I,
    
    GRANDMASTER_I,
    
    MASTER_I,
    
    DIAMOND_I,
    DIAMOND_II,
    DIAMOND_III,
    DIAMOND_IV,
    DIAMOND_V,
    
    EMERALD_I,
    EMERALD_II,
    EMERALD_III,
    EMERALD_IV,
    EMERALD_V,
    
    PLATINUM_I,
    PLATINUM_II,
    PLATINUM_III,
    PLATINUM_IV,
    PLATINUM_V,
    
    GOLD_I,
    GOLD_II,
    GOLD_III,
    GOLD_IV,
    GOLD_V,
    
    SILVER_I,
    SILVER_II,
    SILVER_III,
    SILVER_IV,
    SILVER_V,
    
    BRONZE_I,
    BRONZE_II,
    BRONZE_III,
    BRONZE_IV,
    BRONZE_V,
    
    IRON_I,
    IRON_II,
    IRON_III,
    IRON_IV,
    IRON_V,
    
    UNRANKED;

    public String getCode()
    {
        return this.name();
    }
    
    public String getTier()
    {
        if (!name().contains("_"))
        {
            return null;
        }
        
        return name().split("_")[0];
    }
    
    public String getDivision()
    {
        if (!name().contains("_"))
        {
            return null;
        }
        
        return name().split("_")[1];
    }

    public TierDivisionType getFromCode(String tierAndRank)
    {
        Pattern pattern = Pattern.compile(tierAndRank);
        return Arrays.stream(TierDivisionType.values()).filter(s -> pattern.matcher(s.name()).matches()).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case CHALLENGER_I -> { return "Challenger"; }
            case GRANDMASTER_I -> { return "Grandmaster"; }
            case MASTER_I -> { return "Master"; }
            case DIAMOND_I -> { return "Diamond I"; }
            case DIAMOND_II -> { return "Diamond II"; }
            case DIAMOND_III -> { return "Diamond III"; }
            case DIAMOND_IV -> { return "Diamond IV"; }
            case DIAMOND_V -> { return "Diamond V"; }
            case EMERALD_I -> { return "Emerald I"; }
            case EMERALD_II -> { return "Emerald II"; }
            case EMERALD_III -> { return "Emerald III"; }
            case EMERALD_IV -> { return "Emerald IV"; }
            case EMERALD_V -> { return "Emerald V"; }
            case PLATINUM_I -> { return "Platinum I"; }
            case PLATINUM_II -> { return "Platinum II"; }
            case PLATINUM_III -> { return "Platinum III"; }
            case PLATINUM_IV -> { return "Platinum IV"; }
            case PLATINUM_V -> { return "Platinum V"; }
            case GOLD_I -> { return "Gold I"; }
            case GOLD_II -> { return "Gold II"; }
            case GOLD_III -> { return "Gold III"; }
            case GOLD_IV -> { return "Gold IV"; }
            case GOLD_V -> { return "Gold V"; }
            case SILVER_I -> { return "Silver I"; }
            case SILVER_II -> { return "Silver II"; }
            case SILVER_III -> { return "Silver III"; }
            case SILVER_IV -> { return "Silver IV"; }
            case SILVER_V -> { return "Silver V"; }
            case BRONZE_I -> { return "Bronze I"; }
            case BRONZE_II -> { return "Bronze II"; }
            case BRONZE_III -> { return "Bronze III"; }
            case BRONZE_IV -> { return "Bronze IV"; }
            case BRONZE_V -> { return "Bronze V"; }
            case IRON_I -> { return "Iron I"; }
            case IRON_II -> { return "Iron II"; }
            case IRON_III -> { return "Iron III"; }
            case IRON_IV -> { return "Iron IV"; }
            case IRON_V -> { return "Iron V"; }
            case UNRANKED -> { return "Unranked"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }
    
    public static TierDivisionType getFromCodes(String tier, String rank)
    {
        Pattern pattern = Pattern.compile(String.format("%s_%s", tier, rank));
        return Arrays.stream(TierDivisionType.values()).filter(s -> pattern.matcher(s.name()).matches()).findFirst().orElse(TierDivisionType.UNRANKED);
    }

    public static TierDivisionType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }

  public record MmrRange(int minimum, Integer maximum) {}

  public static boolean isHighElo(TierDivisionType division) {
    return Arrays.asList(TierDivisionType.MASTER_I, TierDivisionType.GRANDMASTER_I, TierDivisionType.CHALLENGER_I).contains(division);
  }

  public static List<TierDivisionType> descendingRankedDivisions() {
    return Arrays.stream(TierDivisionType.values())
      .filter(division -> division != TierDivisionType.UNRANKED)
      .sorted(Comparator.comparingInt((TierDivisionType division) -> getMmr(division, 0)).reversed())
      .toList();
  }

  public static TierType getAverageRank(List<TierDivisionType> divisions) {
    if (divisions == null || divisions.size() == 0) return TierType.UNRANKED; 
    
    int average = 0;
    TierDivisionType averageRank = TierDivisionType.UNRANKED;

    int unranked = 0;
    for (TierDivisionType division : divisions) {
        if (TierDivisionType.UNRANKED == division) {
            unranked++;
            continue;
        }

        average += division.ordinal();
    }
    average = (divisions.size() - unranked) > 0 ? Math.round(average / (divisions.size() - unranked)) : TierDivisionType.UNRANKED.ordinal();

    if (average >= TierDivisionType.values().length)
        average = TierDivisionType.UNRANKED.ordinal();

    averageRank = TierDivisionType.values()[average];
    if (averageRank.getDivision() != null && averageRank.getDivision().equalsIgnoreCase("V")) {
        if (average - 1 < TierDivisionType.values().length) {
            averageRank = TierDivisionType.values()[average - 1];
        }
    }
    return averageRank.getTier() != null ? TierType.valueOf(averageRank.getTier().toUpperCase()) : TierType.UNRANKED;
  }

  public static int getMmr(TierDivisionType division, int lp) {
    if (division == null || division == TierDivisionType.UNRANKED) return -1;

    int base = switch (division) {
        case IRON_IV -> 0;
        case IRON_III -> 100;
        case IRON_II -> 200;
        case IRON_I -> 300;
        case BRONZE_IV -> 400;
        case BRONZE_III -> 500;
        case BRONZE_II -> 600;
        case BRONZE_I -> 700;
        case SILVER_IV -> 800;
        case SILVER_III -> 900;
        case SILVER_II -> 1000;
        case SILVER_I -> 1100;
        case GOLD_IV -> 1200;
        case GOLD_III -> 1300;
        case GOLD_II -> 1400;
        case GOLD_I -> 1500;
        case PLATINUM_IV -> 1600;
        case PLATINUM_III -> 1700;
        case PLATINUM_II -> 1800;
        case PLATINUM_I -> 1900;
        case EMERALD_IV -> 2000;
        case EMERALD_III -> 2100;
        case EMERALD_II -> 2200;
        case EMERALD_I -> 2300;
        case DIAMOND_IV -> 2400;
        case DIAMOND_III -> 2500;
        case DIAMOND_II -> 2600;
        case DIAMOND_I -> 2700;
        case MASTER_I -> 10000;
        case GRANDMASTER_I -> 20000;
        case CHALLENGER_I -> 30000;
        default -> -1;
    };

    return base < 0 ? -1 : base + Math.max(lp, 0);
  }

  public static MmrRange getMmrRange(TierDivisionType division) {
    int minimum = getMmr(division, 0);
    if (minimum < 0) return new MmrRange(-1, 0);

    Integer maximum = null;
    for (TierDivisionType candidate : TierDivisionType.values()) {
      int candidateMinimum = getMmr(candidate, 0);
      if (candidateMinimum <= minimum || (maximum != null && candidateMinimum >= maximum)) continue;
      maximum = candidateMinimum;
    }
    return new MmrRange(minimum, maximum);
  }

  public static TierDivisionType getDivisionFromMmr(long mmr) {
    TierDivisionType result = TierDivisionType.UNRANKED;
    int resultMinimum = -1;
    for (TierDivisionType division : TierDivisionType.values()) {
      int minimum = getMmr(division, 0);
      if (minimum <= resultMinimum || mmr < minimum) continue;
      result = division;
      resultMinimum = minimum;
    }
    return result;
  }
    public static MmrRange getMmrRange(TierType tier) { return TierType.getMmrRange(tier); }
}
