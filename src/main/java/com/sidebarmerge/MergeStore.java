package com.sidebarmerge;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the merged tab remembers between sessions, keyed by {@link SidebarTab#key()}. The layout
 * methods read and write the active preset.
 */
interface MergeStore
{
	/** Panels to merge, top to bottom. */
	List<String> selection();

	void saveSelection(List<String> keys);

	Set<String> collapsed();

	void saveCollapsed(Set<String> keys);

	/** Heights the user dragged cards to, in pixels. Cards not listed share the space equally. */
	Map<String, Integer> heights();

	void saveHeights(Map<String, Integer> heights);

	List<String> presetNames();

	String activePreset();

	void switchPreset(String name);

	/** Why {@code name} can't be used, or null if it can. */
	String presetNameProblem(String name, boolean renamingActive);

	/** Add a preset, copied from the active one or empty, and make it active. */
	void createPreset(String name, boolean copyActive);

	void renameActivePreset(String name);

	/** Delete the active preset and show its neighbour; the last one stays. */
	void deleteActivePreset();
}
