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

	public static List<String> getRecentPatches(int count) {
		if (count <= 0 || patches.isEmpty()) return List.of();
		List<String> result = new ArrayList<>();
		for (String patch : patches) {
			if (patch == null || patch.isBlank() || result.contains(patch)) continue;
			result.add(patch);
		}
		result.sort(PatchUtils::comparePatches);
		if (result.size() <= count) return result;
		return new ArrayList<>(result.subList(result.size() - count, result.size()));
	}

	public static OptionData getAsOptions() {
		List<Choice> choices = new ArrayList<>();
		for (String version : patches.subList(0, Math.min(3, patches.size()))) {
			String patch = patchMajor(version);
			if (patch != null) choices.add(new Choice(patch, patch));
		}
		return new OptionData(OptionType.STRING, "patch", "Patch you want to get the data from", false).addChoices(choices);
	}

	public static String patchMajor(String patch) {
		String value = patch == null ? null : patch.trim();
		if (value == null || value.isBlank()) return null;
		int firstSeparator = value.indexOf('.');
		if (firstSeparator < 0) return value;
		int secondSeparator = value.indexOf('.', firstSeparator + 1);
		return secondSeparator < 0 ? value : value.substring(0, secondSeparator);
	}

	private static int comparePatches(String left, String right) {
		String[] leftParts = left.split("\\.");
		String[] rightParts = right.split("\\.");
		int length = Math.max(leftParts.length, rightParts.length);
		for (int index = 0; index < length; index++) {
			int comparison = Integer.compare(patchPart(leftParts, index), patchPart(rightParts, index));
			if (comparison != 0) return comparison;
		}
		return left.compareTo(right);
	}

	private static int patchPart(String[] parts, int index) {
		if (index >= parts.length) return 0;
		try {
			return Integer.parseInt(parts[index]);
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static List<String> fetchPatches() {
		try {
			URI uri = new URI("https://ddragon.leagueoflegends.com/api/versions.json");
			String json = IOUtils.toString(uri.toURL(), StandardCharsets.UTF_8);
			JSONArray file = (JSONArray) new JSONParser().parse(json);
			List<String> result = new ArrayList<>();
			for (Object v : file) {
				String patch = patchMajor((String) v);
				if (patch != null) result.add(patch);
			}
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return new ArrayList<>();
		}
	}
}
