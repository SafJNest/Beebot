package com.safjnest.lol.utils;

import java.util.HashMap;
import java.util.Map;

import com.safjnest.lol.LeagueHandler;

import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

public class ItemUtils {

  private static Map<Integer, Item> items = new HashMap<>();

  static {
    items = LeagueHandler.getRiotApi().getDDragonAPI().getItems();
  }

  public static Map<Integer, Item> getItems() {
    return items;
  }

  public static Item getItem(int id) {
    return items.get(id);
  }

  public static boolean isBoots(Item item) {
    boolean fromBoots = item.getFrom() != null && item.getFrom().contains("1001");
    boolean containsBoots = item.getName().toLowerCase().contains("boots") || item.getTags().contains("Boots");
    return fromBoots || containsBoots;
  }

  public static boolean isPrismatic(Item item) {
    return isPrismatic(item.getId());
  }

  public static boolean isPrismatic(int id) {
      return String.valueOf(id).startsWith("44") && id > 440000;
  }

  public static boolean isPrismatic(String id) {
      return isPrismatic(Integer.parseInt(id));
  }
  
}
