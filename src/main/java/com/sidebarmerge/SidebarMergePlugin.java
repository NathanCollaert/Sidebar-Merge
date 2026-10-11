package com.sidebarmerge;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.sidebarmerge.ui.Icons;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SidebarMergeConfig config;

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

	@Provides
	SidebarMergeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SidebarMergeConfig.class);
	}

	/** The presets were changed from outside (a config sync, another client): show the new state. */
	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!SidebarMergeConfig.GROUP.equals(e.getGroup()) || !SidebarMergeConfig.PRESETS_KEY.equals(e.getKey())
			|| Objects.equals(e.getNewValue(), savedJson))
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				book = loadBook();
				log.info("[SM] presets changed outside the plugin; reloaded ({} active)", book.active().name);
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
		configManager.setConfiguration(SidebarMergeConfig.GROUP, SidebarMergeConfig.PRESETS_KEY, savedJson);
	}

	/** The saved presets; or, the first time, the single layout saved before presets existed. */
	private PresetBook loadBook()
	{
		String json = configManager.getConfiguration(SidebarMergeConfig.GROUP, SidebarMergeConfig.PRESETS_KEY);
		PresetBook loaded = PresetBook.fromJson(gson, json);
		if (loaded != null)
		{
			savedJson = json;
			return loaded;
		}
		if (json != null && !json.trim().isEmpty())
		{
			log.warn("[SM] saved presets are unreadable; starting again from the pre-preset layout");
		}
		book = PresetBook.migrate(legacyPanels(), legacyCollapsed(), legacyHeights());
		save();
		return book;
	}

	private List<String> legacyPanels()
	{
		return split(config.mergedPanels()).collect(Collectors.toList());
	}

	private Set<String> legacyCollapsed()
	{
		return split(config.collapsedPanels()).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private Map<String, Integer> legacyHeights()
	{
		Map<String, Integer> out = new LinkedHashMap<>();
		split(config.panelHeights()).forEach(entry ->
		{
			int eq = entry.lastIndexOf('=');
			if (eq > 0)
			{
				try
				{
					out.put(entry.substring(0, eq), Integer.parseInt(entry.substring(eq + 1)));
				}
				catch (NumberFormatException ignored)
				{
					// Hand-edited or corrupt entry: treat that card as auto.
				}
			}
		});
		return out;
	}

	private static Stream<String> split(String csv)
	{
		return Arrays.stream(csv.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.distinct();
	}
}
