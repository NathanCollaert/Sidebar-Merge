package com.sidebarmerge;

import com.google.gson.Gson;
import com.sidebarmerge.ui.Icons;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "Sidebar Merge",
	description = "Combine several plugins' sidebar panels into one tab, stacked top to bottom, with presets",
	tags = {"sidebar", "panel", "merge", "combine", "stack", "split", "tabs", "collapse", "preset", "layout"}
)
public class SidebarMergePlugin extends Plugin implements MergeStore
{
	/*
	 * Everything is set up from the merged tab itself, so there is no Config interface: providing
	 * one makes RuneLite show a settings gear even when every item is hidden. The presets are
	 * stored straight through ConfigManager instead.
	 */
	static final String CONFIG_GROUP = "sidebarmerge";
	static final String PRESETS_KEY = "presets";

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Getter(AccessLevel.PACKAGE)
	private MergedPanel panel;
	@Getter(AccessLevel.PACKAGE)
	private NavigationButton navButton;

	private PresetBook book;
	/** Last JSON we wrote, so our own saves don't come back to us as outside changes. */
	private String savedJson;

	@Override
	protected void startUp()
	{
		book = loadBook();
		panel = new MergedPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Sidebar Merge")
			.icon(Icons.logoImage())
			.priority(1)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		// PluginManager stops plugins on the EDT, so this runs before our tab is removed.
		panel.release();
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
	}

	/** The presets were changed from outside (a config sync, another client): show the new state. */
	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!CONFIG_GROUP.equals(e.getGroup()) || !PRESETS_KEY.equals(e.getKey()) || Objects.equals(e.getNewValue(), savedJson))
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				book = loadBook();
				log.debug("Presets changed outside the plugin; reloaded ({} active)", book.active().name);
				panel.presetChanged();
			}
		});
	}

	// ---- layout of the active preset ----

	@Override
	public List<String> selection()
	{
		return new ArrayList<>(book.active().panels);
	}

	@Override
	public void saveSelection(List<String> keys)
	{
		book.active().panels = new ArrayList<>(new LinkedHashSet<>(keys));
		save();
	}

	@Override
	public Set<String> collapsed()
	{
		return new LinkedHashSet<>(book.active().collapsed);
	}

	@Override
	public void saveCollapsed(Set<String> keys)
	{
		book.active().collapsed = new LinkedHashSet<>(keys);
		save();
	}

	@Override
	public Map<String, Integer> heights()
	{
		return new LinkedHashMap<>(book.active().heights);
	}

	@Override
	public void saveHeights(Map<String, Integer> heights)
	{
		book.active().heights = new LinkedHashMap<>(heights);
		save();
	}

	// ---- presets ----

	@Override
	public List<String> presetNames()
	{
		return book.names();
	}

	@Override
	public String activePreset()
	{
		return book.active().name;
	}

	@Override
	public void switchPreset(String name)
	{
		if (book.switchTo(name))
		{
			save();
		}
	}

	@Override
	public String presetNameProblem(String name, boolean renamingActive)
	{
		return book.nameProblem(name, renamingActive);
	}

	@Override
	public void createPreset(String name, boolean copyActive)
	{
		if (book.create(name, copyActive))
		{
			save();
		}
	}

	@Override
	public void renameActivePreset(String name)
	{
		if (book.renameActive(name))
		{
			save();
		}
	}

	@Override
	public void deleteActivePreset()
	{
		if (book.deleteActive())
		{
			save();
		}
	}

	private void save()
	{
		savedJson = book.toJson(gson);
		configManager.setConfiguration(CONFIG_GROUP, PRESETS_KEY, savedJson);
	}

	/** The saved presets, or a single empty "Default" preset the first time. */
	private PresetBook loadBook()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, PRESETS_KEY);
		PresetBook loaded = PresetBook.fromJson(gson, json);
		if (loaded != null)
		{
			savedJson = json;
			return loaded;
		}
		if (json != null && !json.trim().isEmpty())
		{
			log.warn("Saved Sidebar Merge presets are unreadable; starting again with an empty Default preset");
		}
		return PresetBook.fresh();
	}
}
