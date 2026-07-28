package com.safjnest.lol.utils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;

public class PatchUtils {

	private static final List<String> patches = fetchPatches();

	public static String getPatch() {
		return patches.isEmpty() ? null : patches.get(0);
	}

	public static String getPreviousPatch() {
		return patches.size() > 1 ? patches.get(1) : null;
	}

	public static List<String> getPatches() {
		return patches;
	}

	public static OptionData getAsOptions() {
		List<Choice> choices = new ArrayList<>();
		for (String version : patches.subList(0, Math.min(3, patches.size()))) {
			String patch = version.split("\\.")[0] + "." + version.split("\\.")[1];
			choices.add(new Choice(patch, patch));
		}
		return new OptionData(OptionType.STRING, "patch", "Patch you want to get the data from", false).addChoices(choices);
	}

	private static List<String> fetchPatches() {
		try {
			URI uri = new URI("https://ddragon.leagueoflegends.com/api/versions.json");
			String json = IOUtils.toString(uri.toURL(), StandardCharsets.UTF_8);
			JSONArray file = (JSONArray) new JSONParser().parse(json);
			List<String> result = new ArrayList<>();
			for (Object v : file) {
        String patch = (String) v;
        patch = patch.split("\\.")[0] + "." + patch.split("\\.")[1];
				result.add(patch);
			}
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return new ArrayList<>();
		}
	}
}
