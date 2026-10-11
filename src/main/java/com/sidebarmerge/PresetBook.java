package com.sidebarmerge;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every saved layout of the merged tab, and which one is showing. Stored as one JSON config
 * value. Changes always go to the active preset; there is no separate "save" step.
 */
final class PresetBook
{
	static final String DEFAULT_NAME = "Default";
	static final int MAX_NAME = 24;

	/** One layout: which panels, top to bottom, and how each card is collapsed or resized. */
	static final class Preset
	{
		String name;
		List<String> panels = new ArrayList<>();
		Set<String> collapsed = new LinkedHashSet<>();
		Map<String, Integer> heights = new LinkedHashMap<>();

		Preset(String name)
		{
			this.name = name;
		}

		Preset copy(String newName)
		{
			Preset p = new Preset(newName);
			p.panels.addAll(panels);
			p.collapsed.addAll(collapsed);
			p.heights.putAll(heights);
			return p;
		}
	}

	private List<Preset> presets = new ArrayList<>();
	private String active;

	/** A first book holding the layout saved before presets existed. */
	static PresetBook migrate(List<String> panels, Set<String> collapsed, Map<String, Integer> heights)
	{
		PresetBook book = new PresetBook();
		Preset p = new Preset(DEFAULT_NAME);
		p.panels.addAll(panels);
		p.collapsed.addAll(collapsed);
		p.heights.putAll(heights);
		book.presets.add(p);
		book.active = p.name;
		return book;
	}

	/** Null when the JSON is missing or unreadable. */
	static PresetBook fromJson(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			PresetBook book = gson.fromJson(json, PresetBook.class);
			if (book != null)
			{
				book.normalize();
			}
			return book;
		}
		catch (JsonParseException e)
		{
			return null;
		}
	}

	String toJson(Gson gson)
	{
		return gson.toJson(this);
	}

	Preset active()
	{
		for (Preset p : presets)
		{
			if (p.name.equals(active))
			{
				return p;
			}
		}
		active = presets.get(0).name;
		return presets.get(0);
	}

	List<String> names()
	{
		return presets.stream().map(p -> p.name).collect(Collectors.toList());
	}

	/** Why {@code name} can't be used, or null if it can. Renaming the active preset to itself is fine. */
	String nameProblem(String name, boolean renamingActive)
	{
		String n = name == null ? "" : name.trim();
		if (n.isEmpty())
		{
			return "Give the preset a name";
		}
		if (n.length() > MAX_NAME)
		{
			return "Keep it to " + MAX_NAME + " characters";
		}
		for (Preset p : presets)
		{
			if (p.name.toLowerCase(Locale.ROOT).equals(n.toLowerCase(Locale.ROOT)) && !(renamingActive && p == active()))
			{
				return "There's already a preset called " + p.name;
			}
		}
		return null;
	}

	/** Add a preset (a copy of the active one, or empty) and make it active. */
	boolean create(String name, boolean copyActive)
	{
		if (nameProblem(name, false) != null)
		{
			return false;
		}
		Preset p = copyActive ? active().copy(name.trim()) : new Preset(name.trim());
		presets.add(presets.indexOf(active()) + 1, p);
		active = p.name;
		return true;
	}

	boolean renameActive(String name)
	{
		if (nameProblem(name, true) != null)
		{
			return false;
		}
		Preset p = active();
		p.name = name.trim();
		active = p.name;
		return true;
	}

	/** Delete the active preset and show its neighbour. The last preset can't be deleted. */
	boolean deleteActive()
	{
		if (presets.size() < 2)
		{
			return false;
		}
		int i = presets.indexOf(active());
		presets.remove(i);
		active = presets.get(Math.max(0, i - 1)).name;
		return true;
	}

	boolean switchTo(String name)
	{
		for (Preset p : presets)
		{
			if (p.name.equals(name))
			{
				active = name;
				return true;
			}
		}
		return false;
	}

	/** Repair whatever Gson left null or a hand edit broke, so the rest never has to check. */
	private void normalize()
	{
		if (presets == null)
		{
			presets = new ArrayList<>();
		}
		Set<String> seen = new LinkedHashSet<>();
		presets.removeIf(p -> p == null || p.name == null || p.name.trim().isEmpty()
			|| !seen.add(p.name.trim().toLowerCase(Locale.ROOT)));
		for (Preset p : presets)
		{
			p.name = p.name.trim();
			p.panels = p.panels == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(p.panels));
			p.collapsed = p.collapsed == null ? new LinkedHashSet<>() : new LinkedHashSet<>(p.collapsed);
			p.heights = p.heights == null ? new LinkedHashMap<>() : new LinkedHashMap<>(p.heights);
		}
		if (presets.isEmpty())
		{
			presets.add(new Preset(DEFAULT_NAME));
		}
		active();
	}
}
