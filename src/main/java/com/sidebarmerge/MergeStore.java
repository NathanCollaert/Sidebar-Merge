package com.sidebarmerge;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keyed by {@link SidebarTab#key()}. The layout methods read and write the active preset. */
interface MergeStore
{
	/** Top to bottom. */
	List<String> selection();

	void saveSelection(List<String> keys);

	Set<String> collapsed();

	void saveCollapsed(Set<String> keys);

	/** In pixels. Cards not listed share the space equally. */
	Map<String, Integer> heights();

	void saveHeights(Map<String, Integer> heights);

	List<String> presetNames();

	String activePreset();

	void switchPreset(String name);

	/** Why {@code name} can't be used, or null if it can. */
	String presetNameProblem(String name, boolean renamingActive);

	/** Also makes the new preset active. */
	void createPreset(String name, boolean copyActive);

	void renameActivePreset(String name);

	/** Its neighbour becomes active. The last preset can't be deleted. */
	void deleteActivePreset();
}
