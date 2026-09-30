package com.safjnest.lol.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.safjnest.lol.service.StaticDataService;

import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

public class ItemUtils {

  public static final int BASE_BOOTS = 1001;
  private static volatile Map<Integer, Item> items;
  private static final Set<Integer> boots = new HashSet<>();

  private ItemUtils() {}

  public static Map<Integer, Item> getItems() {
    if (items == null) synchronized (ItemUtils.class) {
      if (items == null) {
        Map<Integer, Item> loadedItems = StaticDataService.getItems();
        items = loadedItems == null ? new HashMap<>() : loadedItems;
      }
    }
    return items;
  }

  public static Item getItem(int id) {
    return getItems().get(id);
  }

  public static boolean isBoots(Item item) {
    if (item == null) return false;
    boolean isBoots = item.getId() == BASE_BOOTS;
    boolean fromBoots = item.getFrom() != null && item.getFrom().contains("1001");
    boolean containsBoots = item.getName() != null && item.getName().toLowerCase().contains("boots")
        || item.getTags() != null && item.getTags().contains("Boots");
    return isBoots || fromBoots || containsBoots;
  }

  public static synchronized Set<Integer> getBoots() {
    if (!boots.isEmpty()) return boots;

    for (Item item : getItems().values()) {
      if (isBoots(item)) {
        boots.add(item.getId());
      }
    }
    return boots;
  }

  public static boolean isPrismatic(Item item) {
    return item != null && isPrismatic(item.getId());
  }

  public static boolean isPrismatic(int id) {
    return id > 440000 && String.valueOf(id).startsWith("44");
  }

  public static boolean isPrismatic(String id) {
      return isPrismatic(Integer.parseInt(id));
  }
  
}
