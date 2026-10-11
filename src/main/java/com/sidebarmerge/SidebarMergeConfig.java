package com.sidebarmerge;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/** Everything is edited from the merged tab itself, so every item here is hidden. */
@ConfigGroup(SidebarMergeConfig.GROUP)
public interface SidebarMergeConfig extends Config
{
	String GROUP = "sidebarmerge";
	String PRESETS_KEY = "presets";
	String MERGED_PANELS_KEY = "mergedPanels";
	String COLLAPSED_PANELS_KEY = "collapsedPanels";
	String PANEL_HEIGHTS_KEY = "panelHeights";

	@ConfigItem(
		keyName = PRESETS_KEY,
		name = "Presets",
		description = "Every saved merged-tab layout and which one is active, as JSON.",
		hidden = true
	)
	default String presets()
	{
		return "";
	}

	// The three below are the layout saved before presets existed. They are only read once, to
	// seed the "Default" preset, and are left in place so an older build still finds them.

	@ConfigItem(
		keyName = MERGED_PANELS_KEY,
		name = "Merged panels (pre-presets)",
		description = "Panel class names to show in the merged tab, comma separated, top to bottom.",
		hidden = true
	)
	default String mergedPanels()
	{
		return "";
	}

	@ConfigItem(
		keyName = COLLAPSED_PANELS_KEY,
		name = "Collapsed panels (pre-presets)",
		description = "Panel class names whose card is collapsed, comma separated.",
		hidden = true
	)
	default String collapsedPanels()
	{
		return "";
	}

	@ConfigItem(
		keyName = PANEL_HEIGHTS_KEY,
		name = "Panel heights (pre-presets)",
		description = "Heights cards were dragged to, as class=pixels, comma separated.",
		hidden = true
	)
	default String panelHeights()
	{
		return "";
	}
}
