package com.safjnest.lol.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.safjnest.lol.LeagueHandler;

import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

public class ItemUtils {

  private static Map<Integer, Item> items = new HashMap<>();
  private static Set<Integer> boots = new HashSet<>();
  public static final int BASE_BOOTS = 1001;

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
    boolean isBoots = item.getId() == BASE_BOOTS;
    boolean fromBoots = item.getFrom() != null && item.getFrom().contains("1001");
    boolean containsBoots = item.getName().toLowerCase().contains("boots") || item.getTags().contains("Boots");
    return isBoots || fromBoots || containsBoots;
  }

  public static Set<Integer> getBoots() {
    if (!boots.isEmpty()) return boots;

    for (Item item : items.values()) {
      if (isBoots(item)) {
        boots.add(item.getId());
      }
    }
    return boots;
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
