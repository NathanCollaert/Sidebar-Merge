package com.sidebarmerge;

import com.google.gson.Gson;
import com.sidebarmerge.ui.Theme;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;

/**
 * Dev only. Drives Sidebar Merge in a live client the way a user would (select tabs, edit,
 * stop/start plugins whose panels are merged) and checks the sidebar after each step. Writes
 * sidebar snapshots to build/selftest/ and logs "[SM-TEST] PASS/FAIL ..." lines.
 *
 * Reads ClientUI's private tab bookkeeping by reflection to prove we never knock it out of line.
 * That is fine here because it is test code; the plugin itself uses none.
 */
@Slf4j
@PluginDescriptor(name = "Sidebar Merge Self-Test", description = "Dev only: scripted checks for Sidebar Merge")
public class SidebarMergeSelfTestPlugin extends Plugin
{
	private static final int STEP_DELAY = 1500;
	private static final int PLUGIN_DELAY = 3000;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ClientUI clientUI;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	private final Deque<Step> queue = new ArrayDeque<>();
	private final List<String> failures = new ArrayList<>();
	private int passed;
	private Timer timer;
	private File outDir;

	private MergedPanel merged;
	private JTabbedPane sidebar;
	private Component originalSelection;
	private static final String TEST_PRESET = "SM self-test";
	/** Where content starts on the left, everywhere in the merged tab (see Theme). */
	private static final int ALIGN_X = Theme.ALIGN;
	private static final Gson GSON = new Gson();

	private SidebarMergePlugin sm;
	private String originalPresets;
	private final List<SidebarTab> picks = new ArrayList<>();
	/** A wrapped panel outside the picks, added and removed to test the equal split. */
	private SidebarTab fifth;

	@Override
	protected void startUp()
	{
		outDir = new File("build/selftest");
		outDir.mkdirs();
		File[] old = outDir.listFiles((d, n) -> n.endsWith(".png"));
		if (old != null)
		{
			for (File f : old)
			{
				f.delete();
			}
		}
		waitForUi(0);
	}

	@Override
	protected void shutDown()
	{
		if (timer != null)
		{
			timer.stop();
		}
	}

	private void waitForUi(int attempt)
	{
		SidebarMergePlugin sm = (SidebarMergePlugin) pluginManager.getPlugins().stream()
			.filter(p -> p instanceof SidebarMergePlugin).findFirst().orElse(null);
		MergedPanel panel = sm == null ? null : sm.getPanel();
		JTabbedPane found = panel == null ? null : SidebarTab.findSidebar(panel);
		if (found != null && found.isShowing())
		{
			merged = panel;
			this.sm = sm;
			sidebar = found;
			log.info("[SM-TEST] client UI up; letting external plugins settle for 12s");
			later(12000, this::buildScript);
			return;
		}
		if (attempt > 180)
		{
			log.error("[SM-TEST] gave up waiting for the sidebar");
			return;
		}
		later(1000, () -> waitForUi(attempt + 1));
	}

	private void buildScript()
	{
		step("inventory", STEP_DELAY, this::inventory);
		step("clear saved state, open the merged tab", STEP_DELAY, () ->
		{
			// Leave the merged tab first: it only re-reads the saved state when it is shown.
			if (sidebar.getSelectedComponent() == merged)
			{
				sidebar.setSelectedIndex(tabIndexOf(picks.get(0)));
			}
			// Work in a fresh, empty preset of our own; the restore step puts the user's presets back.
			removeTestPresets();
			sm.createPreset(TEST_PRESET, false);
			merged.presetChanged();
			sidebar.setSelectedComponent(merged);
		});
		step("verify empty state", STEP_DELAY, () ->
		{
			check(merged.cards().isEmpty(), "empty state: no cards");
			check(findButton(merged, "Choose panels").isShowing(), "empty state: Choose panels button showing");
			snapshot("00-empty");
		});
		step("merge four panels while the tab is open", STEP_DELAY, () -> merged.applySelection(keys()));
		step("verify merged", STEP_DELAY, () ->
		{
			verifyMerged("01-merged", picks.size());
			// One alignment column: toolbar logo, card chevrons (and picker rows, checked later).
			JButton switcherButton = findButton(merged, TEST_PRESET);
			check(switcherButton.getWidth() >= switcherButton.getPreferredSize().width,
				"preset name shown in full when there's room (" + switcherButton.getWidth() + " >= " + switcherButton.getPreferredSize().width + ")");
			int logoX = SwingUtilities.convertPoint(switcherButton, Theme.SPACE_XS, 0, merged).x;
			check(logoX == ALIGN_X, "toolbar logo on the alignment column (x=" + logoX + ")");
			for (SectionCard c : merged.cards())
			{
				Component chevron = ((Container) ((Container) c.header()).getComponent(0)).getComponent(0);
				int x = SwingUtilities.convertPoint(chevron, 0, 0, merged).x;
				check(x == ALIGN_X, c.getName() + ": chevron on the alignment column (x=" + x + ")");
			}
			check(merged.cards().get(0).header().getHeight() == Theme.HEADER_HEIGHT, "card header is " + Theme.HEADER_HEIGHT + "px");
		});

		addCollapseSteps();
		addResizeSteps();
		addPresetSteps();

		step("reorder: move the first card to third", STEP_DELAY, () -> merged.moveCard(0, 2));
		step("verify reorder", STEP_DELAY, () ->
		{
			List<String> want = new ArrayList<>(keys());
			want.add(2, want.remove(0));
			check(want.equals(savedList(SidebarMergeConfig.MERGED_PANELS_KEY)), "saved order follows the move");
			check(want.equals(merged.cards().stream().map(c -> c.borrowed.tab.key()).collect(Collectors.toList())), "cards follow the move");
			verifyMerged("04-reordered", picks.size());
			merged.moveCard(2, 0);
		});

		step("open a card on its own", STEP_DELAY, () -> merged.cards().get(0).openButton().doClick());
		step("verify it opened in its own tab", STEP_DELAY, () ->
		{
			SidebarTab first = pickFor(keys().get(0));
			verifyAllReturned();
			check(first.panel.isShowing(), first.tooltip + " is showing in its own tab");
			NavigationButton sel = clientSelectedTab();
			check(sel != null && sel.getPanel() == first.panel, "ClientUI thinks " + first.tooltip + " is selected");
			snapshot("05-opened-on-its-own");
		});
		step("reopen merged tab", STEP_DELAY, () -> sidebar.setSelectedComponent(merged));
		step("verify merged again", STEP_DELAY, () -> verifyMerged("06-merged-again", picks.size()));

		step("open picker", STEP_DELAY, () -> findButton(merged, "Choose panels").doClick());
		step("snapshot picker, filter it", STEP_DELAY, () ->
		{
			check(findButton(merged, "Done").isShowing(), "picker: Done showing");
			JLabel rowLabel = findLabel(findPicker(), picks.get(0).tooltip);
			int rowX = SwingUtilities.convertPoint(rowLabel, 0, 0, merged).x;
			check(rowX == ALIGN_X, "picker row icon on the alignment column (x=" + rowX + ")");
			snapshot("07-picker");
			findPicker().setFilter("tr");
		});
		step("snapshot filtered picker, press Done", STEP_DELAY, () ->
		{
			snapshot("07b-picker-filtered");
			findButton(merged, "Done").doClick();
		});
		step("verify after Done", STEP_DELAY, () -> verifyMerged("08-after-done", picks.size()));

		addTabAboveSteps();
		addRestartSteps(false, "10");
		addRestartSteps(true, "11");

		step("restore", STEP_DELAY, () ->
		{
			if (originalSelection != null && sidebar.indexOfComponent(originalSelection) >= 0 && originalSelection != merged)
			{
				sidebar.setSelectedComponent(originalSelection);
			}
			else
			{
				sidebar.setSelectedIndex(0);
			}
			// Goes through the plugin's ConfigChanged listener, the path an outside change takes.
			setConfig(SidebarMergeConfig.PRESETS_KEY, restoredPresets());
			// RuneLite writes config to disk lazily; flush now so a killed client can't leave
			// the test presets on disk (that is how earlier leftovers survived).
			configManager.sendConfig();
		});
		step("verify restored", STEP_DELAY, () ->
		{
			verifyAllReturned();
			check(getConfig(SidebarMergeConfig.PRESETS_KEY).equals(restoredPresets()), "the user's presets are back in config");
			check(sm.presetNames().stream().noneMatch(n -> n.startsWith(TEST_PRESET)), "no test presets left behind " + sm.presetNames());
			check(sm.presetNames().equals(PresetBook.fromJson(GSON, restoredPresets()).names()),
				"the plugin reloaded them from the config change (" + sm.presetNames() + ")");
			snapshot("12-restored");
		});
		step("summary", 0, () ->
		{
			log.info("[SM-TEST] RESULT: {} passed, {} failed", passed, failures.size());
			for (String f : failures)
			{
				log.info("[SM-TEST]   failed: {}", f);
			}
		});
		next();
	}

	private void inventory()
	{
		check(sidebar.isVisible(), "sidebar is expanded");
		if (!sidebar.isVisible())
		{
			queue.clear();
			step("summary", 0, () -> log.info("[SM-TEST] RESULT: aborted, expand the sidebar and rerun"));
			return;
		}
		originalSelection = sidebar.getSelectedComponent();
		originalPresets = getConfig(SidebarMergeConfig.PRESETS_KEY);
		check(PresetBook.fromJson(GSON, originalPresets) != null, "the user's presets are saved and readable (" + sm.presetNames() + ")");
		checkBookkeeping("baseline");

		List<SidebarTab> tabs = SidebarTab.list(sidebar, merged, new IdentityHashMap<>());
		for (SidebarTab t : tabs)
		{
			log.info("[SM-TEST] tab '{}' {} wrapped={} prefHeight={}", t.tooltip, t.key(), t.wrapped, contentHeight(t));
		}

		List<SidebarTab> wrapped = tabs.stream().filter(t -> t.wrapped)
			.sorted(Comparator.comparingInt(this::contentHeight).reversed()).collect(Collectors.toList());
		List<SidebarTab> unwrapped = tabs.stream().filter(t -> !t.wrapped && !isConfig(t))
			.sorted(Comparator.comparingInt(this::contentHeight).reversed()).collect(Collectors.toList());
		if (unwrapped.isEmpty())
		{
			// Merging the wrench panel is fine; the restart steps never stop its plugin.
			tabs.stream().filter(t -> !t.wrapped).findFirst().ifPresent(unwrapped::add);
		}

		// Interleave so both shapes sit next to each other: W, U, W, U.
		for (int i = 0; i < 2; i++)
		{
			if (i < wrapped.size())
			{
				picks.add(wrapped.get(i));
			}
			if (i < unwrapped.size())
			{
				picks.add(unwrapped.get(i));
			}
		}
		log.info("[SM-TEST] picks: {}", picks.stream().map(t -> t.tooltip + (t.wrapped ? " (W)" : " (U)")).collect(Collectors.toList()));
		check(picks.size() >= 3, "at least three panels to merge (got " + picks.size() + ")");
		fifth = wrapped.stream().filter(t -> !picks.contains(t)).findFirst().orElse(null);
		check(fifth != null, "a fifth panel to add (" + (fifth == null ? "none" : fifth.tooltip) + ")");
	}

	/**
	 * Presets, through the real menu, naming row and delete confirmation: save the layout as a
	 * new preset, change it, switch back (open and hidden), rename, delete.
	 */
	private void addPresetSteps()
	{
		final String b = TEST_PRESET + " B";
		final String c = TEST_PRESET + " C";
		final List<String> layoutKeys = new ArrayList<>();
		final Map<String, Integer> layoutHeights = new LinkedHashMap<>();
		final List<String> layoutCollapsed = new ArrayList<>();

		step("give the test preset a distinct layout", STEP_DELAY, () ->
		{
			dragBorder(0, 100);
			merged.toggle(merged.cards().get(3));
		});
		step("open the preset menu", STEP_DELAY, () ->
		{
			layoutKeys.addAll(savedList(SidebarMergeConfig.MERGED_PANELS_KEY));
			layoutHeights.putAll(savedHeights());
			layoutCollapsed.addAll(savedList(SidebarMergeConfig.COLLAPSED_PANELS_KEY));
			check(layoutHeights.size() == 2 && layoutCollapsed.size() == 1, "test layout: 2 resized, 1 collapsed");
			findButton(merged, TEST_PRESET).doClick();
		});
		step("menu open: Save as new preset", STEP_DELAY, () ->
		{
			check(merged.presetMenu() != null && merged.presetMenu().isShowing(), "preset menu showing");
			snapshotWithPopups("13a-preset-menu");
			menuItem("Save as new preset…").doClick();
		});
		step("name it: duplicate first, then a free name", STEP_DELAY, () ->
		{
			JTextField field = merged.presetNameField();
			check(field.isShowing(), "naming row showing");
			field.setText(TEST_PRESET.toUpperCase());
			check(!findButton(merged, "Save").isEnabled(), "a duplicate name can't be saved");
			field.setText(b);
			check(findButton(merged, "Save").isEnabled(), "a free name can be saved");
			snapshot("13b-naming");
			findButton(merged, "Save").doClick();
		});
		step("verify the copy", STEP_DELAY, () ->
		{
			check(sm.activePreset().equals(b) && sm.presetNames().contains(TEST_PRESET), "now on " + b + ", " + TEST_PRESET + " kept");
			check(keysOfCards().equals(layoutKeys), "the copy shows the same panels");
			check(savedHeights().equals(layoutHeights) && savedList(SidebarMergeConfig.COLLAPSED_PANELS_KEY).equals(layoutCollapsed),
				"the copy has the same sizes and collapsed cards");
			verifyMerged("13c-copied", picks.size());
			merged.applySelection(Arrays.asList(fifth.key(), picks.get(0).key()));
		});
		step("verify the copy changed on its own", STEP_DELAY, () ->
		{
			check(keysOfCards().equals(Arrays.asList(fifth.key(), picks.get(0).key())), b + " now shows 2 panels");
			verifyMerged("13d-changed", 2);
			// Like the reported freeze: the preset we leave has a collapsed card the next one lacks.
			merged.toggle(merged.cards().get(0));
		});
		step("open the menu", STEP_DELAY, () -> findButton(merged, b).doClick());
		step("switch back from the menu", 60, () -> menuItem(TEST_PRESET).doClick());
		step("mid-switch", STEP_DELAY, () ->
		{
			check(merged.isTransitioning(), "the switch animates");
			snapshot("13e0-mid-switch");
		});
		step("verify the switch back", STEP_DELAY, () ->
		{
			check(sm.activePreset().equals(TEST_PRESET), "back on " + TEST_PRESET);
			check(keysOfCards().equals(layoutKeys), "its 4 panels are back");
			SectionCard first = merged.cards().get(0);
			check(first.fixedHeight() == layoutHeights.get(first.borrowed.tab.key()), "resized heights restored");
			check(merged.cards().get(3).isCollapsed(), "collapsed card restored");
			check(!SwingUtilities.isDescendingFrom(fifth.panel, merged) && fifth.panel.getWrappedPanel().getComponentCount() == 1,
				"the collapsed card we left went back to its own tab, intact");
			verifyMerged("13e-switched-back", picks.size());

			// Still responsive after the switch: a real click on a header, and a panel that scrolls.
			clickHeader(merged.cards().get(1));
			check(merged.cards().get(1).isCollapsed(), "a header click right after the switch collapses the card");
			JScrollPane ws = scrollPaneOf(merged.cards().get(0));
			ws.getVerticalScrollBar().setValue(40);
			check(ws.getViewport().getViewPosition().y == 40, merged.cards().get(0).getName() + " still scrolls");
			ws.getVerticalScrollBar().setValue(0);
		});
		step("click it open again", STEP_DELAY, () ->
		{
			clickHeader(merged.cards().get(1));
			check(!merged.cards().get(1).isCollapsed(), "and a second click expands it");
		});
		final int[] grabBefore = new int[1];
		step("grab a gap 3px inside a card", STEP_DELAY, () ->
		{
			grabBefore[0] = merged.cards().get(0).getHeight();
			dragBorder(0, 20, -(Theme.GAP / 2 + 3));
			check(merged.cards().get(0).fixedHeight() == grabBefore[0] + 20, "grabbing just inside the card edge resizes too");
		});
		step("undo that drag", STEP_DELAY, () ->
		{
			check(merged.cards().get(0).getHeight() == grabBefore[0] + 20, "the layout followed the drag");
			dragBorder(0, -20);
		});
		step("switch while the merged tab is hidden", STEP_DELAY, () ->
		{
			sidebar.setSelectedIndex(tabIndexOf(picks.get(1)));
			merged.switchPreset(b);
			check(merged.borrowedPanels().isEmpty(), "nothing borrowed while hidden");
		});
		step("reopen", STEP_DELAY, () -> sidebar.setSelectedComponent(merged));
		step("verify the hidden switch", STEP_DELAY, () ->
		{
			check(keysOfCards().equals(Arrays.asList(fifth.key(), picks.get(0).key())), b + " shows when the tab reopens");
			verifyMerged("13f-hidden-switch", 2);
			findButton(merged, b).doClick();
		});
		step("rename from the menu", STEP_DELAY, () -> menuItem("Rename preset…").doClick());
		step("type the new name", STEP_DELAY, () ->
		{
			check(merged.presetNameField().getText().equals(b), "rename starts from the current name");
			check(findButton(merged, "Save").isEnabled(), "keeping the same name is allowed");
			merged.presetNameField().setText(c);
			findButton(merged, "Save").doClick();
		});
		step("verify the rename", STEP_DELAY, () ->
		{
			check(sm.presetNames().contains(c) && !sm.presetNames().contains(b), "renamed to " + c);
			check(findButton(merged, c).isShowing(), "the switcher shows " + c);
			findButton(merged, c).doClick();
		});
		step("delete from the menu", STEP_DELAY, () -> menuItem("Delete preset…").doClick());
		step("confirm the delete", STEP_DELAY, () ->
		{
			snapshot("13g-confirm-delete");
			findButton(merged, "Delete").doClick();
		});
		step("verify the delete", STEP_DELAY, () ->
		{
			check(!sm.presetNames().contains(c), c + " is gone");
			check(sm.activePreset().equals(TEST_PRESET), "its neighbour " + TEST_PRESET + " is showing");
			check(keysOfCards().equals(layoutKeys), "with its panels");
			PresetBook saved = PresetBook.fromJson(GSON, getConfig(SidebarMergeConfig.PRESETS_KEY));
			check(saved != null && saved.names().equals(sm.presetNames()), "config holds the same presets");
			verifyMerged("13h-deleted", picks.size());

			// Back to a plain layout for the steps after this.
			merged.toggle(merged.cards().get(3));
		});
		step("reset the test layout", STEP_DELAY, () -> doubleClickBorder(0));
		step("verify reset", STEP_DELAY, () ->
		{
			check(savedHeights().isEmpty() && savedList(SidebarMergeConfig.COLLAPSED_PANELS_KEY).isEmpty(), "test layout plain again");
			verifyMerged("13i-plain", picks.size());
		});
	}

	private List<String> keysOfCards()
	{
		return merged.cards().stream().map(card -> card.borrowed.tab.key()).collect(Collectors.toList());
	}

	private JMenuItem menuItem(String text)
	{
		for (Component c : merged.presetMenu().getComponents())
		{
			if (c instanceof JMenuItem && text.equals(((JMenuItem) c).getText()))
			{
				return (JMenuItem) c;
			}
		}
		throw new IllegalStateException("no '" + text + "' menu item");
	}

	/** Drag a border, add a fifth panel, collapse a resized card, remove the fifth, reset with a double-click. */
	private void addResizeSteps()
	{
		final int[] before = new int[8];
		step("drag the border under the first card down 120px", STEP_DELAY, () ->
		{
			List<SectionCard> cards = merged.cards();
			for (int i = 0; i < cards.size(); i++)
			{
				before[i] = cards.get(i).getHeight();
			}
			dragBorder(0, 120);
		});
		step("verify resize", STEP_DELAY, () ->
		{
			List<SectionCard> cards = merged.cards();
			check(cards.get(0).getHeight() == before[0] + 120, "first card grew by 120 (" + before[0] + " -> " + cards.get(0).getHeight() + ")");
			check(cards.get(1).getHeight() == before[1] - 120, "second card shrank by 120 (" + before[1] + " -> " + cards.get(1).getHeight() + ")");
			for (int i = 2; i < cards.size(); i++)
			{
				check(cards.get(i).getHeight() == before[i], cards.get(i).getName() + " did not move");
			}
			Map<String, Integer> saved = savedHeights();
			check(saved.size() == 2
					&& Integer.valueOf(cards.get(0).getHeight()).equals(saved.get(cards.get(0).borrowed.tab.key()))
					&& Integer.valueOf(cards.get(1).getHeight()).equals(saved.get(cards.get(1).borrowed.tab.key())),
				"both heights saved " + saved.values());
			verifyMerged("04a-resized", picks.size());
		});
		step("add a fifth panel", STEP_DELAY, () ->
		{
			List<SectionCard> cards = merged.cards();
			before[0] = cards.get(0).getHeight();
			before[1] = cards.get(1).getHeight();
			List<String> five = new ArrayList<>(keys());
			five.add(fifth.key());
			merged.applySelection(five);
		});
		step("verify fifth", STEP_DELAY, () ->
		{
			List<SectionCard> cards = merged.cards();
			check(cards.size() == 5 && cards.get(4).fixedHeight() <= 0, "new card " + fifth.tooltip + " arrives auto-sized");
			check(cards.get(0).getHeight() == before[0] && cards.get(1).getHeight() == before[1],
				"resized cards kept their heights (" + before[0] + ", " + before[1] + ")");
			verifyMerged("04b-added-fifth", picks.size() + 1);
		});
		step("collapse a resized card", STEP_DELAY, () -> merged.toggle(merged.cards().get(0)));
		step("verify resized card collapsed", STEP_DELAY, () ->
		{
			SectionCard first = merged.cards().get(0);
			check(first.getHeight() == Theme.HEADER_HEIGHT && first.fixedHeight() == before[0], "collapsed, saved height " + before[0] + " remembered");
			verifyMerged("04c-resized-collapsed", picks.size() + 1);
			merged.toggle(first);
		});
		step("verify resized card expanded", STEP_DELAY, () ->
		{
			check(merged.cards().get(0).getHeight() == before[0], "expands back to its " + before[0] + "px");
			verifyMerged("04d-resized-expanded", picks.size() + 1);
		});
		step("remove the fifth panel", STEP_DELAY, () -> merged.applySelection(keys()));
		step("verify fifth removed", STEP_DELAY, () ->
		{
			List<SectionCard> cards = merged.cards();
			check(cards.get(0).getHeight() == before[0] && cards.get(1).getHeight() == before[1], "resized cards still kept their heights");
			verifyMerged("04e-fifth-removed", picks.size());
		});
		step("double-click the border to reset", STEP_DELAY, () -> doubleClickBorder(0));
		step("verify reset", STEP_DELAY, () ->
		{
			check(merged.cards().stream().allMatch(c -> c.fixedHeight() <= 0), "both back to auto");
			check(savedHeights().isEmpty(), "saved heights cleared");
			verifyMerged("04f-reset", picks.size());

			// A press with a 2px wobble is a click, not a resize.
			dragBorder(0, 2);
			check(merged.cards().stream().allMatch(c -> c.fixedHeight() <= 0) && savedHeights().isEmpty(), "a click on a border resizes nothing");
		});
	}

	/** Collapse one card (mid-animation snapshot), collapse all, expand all, survive hide/show. */
	private void addCollapseSteps()
	{
		// Filled when the step runs: picks are only known once the inventory step has run.
		final int[] before = new int[8];
		step("collapse the first card", 90, () ->
		{
			List<SectionCard> cards = merged.cards();
			for (int i = 0; i < cards.size(); i++)
			{
				before[i] = cards.get(i).getHeight();
			}
			merged.toggle(cards.get(0));
		});
		step("mid-animation", STEP_DELAY, () ->
		{
			check(merged.isTransitioning(), "collapse animates");
			int h = merged.cards().get(0).getHeight();
			check(h < before[0] && h > Theme.HEADER_HEIGHT, "mid-animation height " + h + " is between " + Theme.HEADER_HEIGHT + " and " + before[0]);
			snapshot("02a-collapsing");
		});
		step("verify one collapsed", STEP_DELAY, () ->
		{
			List<SectionCard> cards = merged.cards();
			SectionCard first = cards.get(0);
			check(first.isCollapsed() && first.getHeight() == Theme.HEADER_HEIGHT, first.getName() + " collapsed to its header (" + first.getHeight() + ")");
			check(!first.borrowed.isActivated(), first.getName() + " told it is hidden (onDeactivate)");
			check(savedList(SidebarMergeConfig.COLLAPSED_PANELS_KEY).contains(first.borrowed.tab.key()), "collapse saved");
			int grown = 0;
			for (int i = 1; i < cards.size(); i++)
			{
				grown += cards.get(i).getHeight() - before[i];
			}
			check(grown > 0, "the others took the freed height (+" + grown + "px)");
			verifyMerged("02-collapsed-one", picks.size());
		});
		step("collapse all", STEP_DELAY, () -> findButton(merged, "Collapse all").doClick());
		step("verify all collapsed", STEP_DELAY, () ->
		{
			for (SectionCard c : merged.cards())
			{
				check(c.isCollapsed() && c.getHeight() == Theme.HEADER_HEIGHT, c.getName() + " collapsed");
			}
			check(savedList(SidebarMergeConfig.COLLAPSED_PANELS_KEY).size() == picks.size(), "all collapses saved");
			verifyMerged("03-all-collapsed", picks.size());
		});
		step("expand all", STEP_DELAY, () -> findButton(merged, "Expand all").doClick());
		step("verify all expanded", STEP_DELAY, () ->
		{
			check(merged.cards().stream().noneMatch(SectionCard::isCollapsed), "none collapsed");
			check(merged.cards().stream().allMatch(c -> c.borrowed.isActivated()), "all told they are shown (onActivate)");
			check(savedList(SidebarMergeConfig.COLLAPSED_PANELS_KEY).isEmpty(), "expand saved");
			verifyMerged("03b-all-expanded", picks.size());
		});
		step("collapse the second card, then leave the merged tab", STEP_DELAY, () ->
		{
			merged.toggle(merged.cards().get(1));
			later(400, () -> sidebar.setSelectedIndex(tabIndexOf(clickTarget())));
		});
		step("come back", STEP_DELAY, () ->
		{
			verifyAllReturned();
			sidebar.setSelectedComponent(merged);
		});
		step("verify collapse survived", STEP_DELAY, () ->
		{
			SectionCard second = merged.cards().get(1);
			check(second.isCollapsed() && second.getHeight() == Theme.HEADER_HEIGHT, second.getName() + " still collapsed after hide/show");
			check(!second.borrowed.isActivated(), "re-borrowed collapsed panel not activated");
			verifyMerged("03c-collapse-survived", picks.size());
			merged.toggle(second);
		});
		step("verify re-expanded", STEP_DELAY, () -> verifyMerged("03d-re-expanded", picks.size()));
	}

	/**
	 * RuneLite quirk: removing a tab above the selected one makes ClientUI deactivate the selected
	 * panel although it stays selected. Only the wrench sits above the merged tab here, so add and
	 * remove a throwaway tab at priority -100 to provoke it.
	 */
	private void addTabAboveSteps()
	{
		final NavigationButton[] dummy = new NavigationButton[1];
		step("add a throwaway tab above every other tab", STEP_DELAY, () ->
		{
			dummy[0] = NavigationButton.builder()
				.tooltip("SM self-test dummy")
				.icon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
				.priority(-100)
				.panel(new PluginPanel()
				{
				})
				.build();
			clientToolbar.addNavigation(dummy[0]);
		});
		step("verify after insert above", STEP_DELAY, () ->
		{
			check(sidebar.getSelectedComponent() == merged, "merged tab still selected after a tab was inserted above it");
			check(merged.borrowedPanels().size() == picks.size(), "all panels still merged after insert above");
			checkBookkeeping("after insert above");
		});
		step("remove the throwaway tab", STEP_DELAY, () -> clientToolbar.removeNavigation(dummy[0]));
		step("verify after removal above", STEP_DELAY, () ->
		{
			check(sidebar.getSelectedComponent() == merged, "merged tab still selected after the tab above it was removed");
			NavigationButton sel = clientSelectedTab();
			log.info("[SM-TEST] info: ClientUI.selectedTab after removal above = {}", sel == null ? null : sel.getTooltip());
			verifyMerged("09a-after-removal-above", picks.size());
		});
		step("click a merged plugin's own icon after the quirk", STEP_DELAY, () -> sidebar.setSelectedIndex(tabIndexOf(clickTarget())));
		step("verify own tab after the quirk", STEP_DELAY, () ->
		{
			verifyAllReturned();
			NavigationButton sel = clientSelectedTab();
			check(sel != null && sel.getPanel() == clickTarget().panel, "ClientUI thinks " + clickTarget().tooltip + " is selected after the quirk");
		});
		step("reopen merged tab after the quirk", STEP_DELAY, () -> sidebar.setSelectedComponent(merged));
		step("verify merged after the quirk", STEP_DELAY, () -> verifyMerged("09b-merged-after-quirk", picks.size()));
	}

	/** Stop the owning plugin of a merged panel while the merged tab is open, then start it again. */
	private void addRestartSteps(boolean wrappedShape, String snap)
	{
		final Plugin[] owner = new Plugin[1];
		final SidebarTab[] target = new SidebarTab[1];
		final int[] tabsBefore = new int[1];

		step("stop the plugin of a merged " + (wrappedShape ? "wrapped" : "unwrapped") + " panel", PLUGIN_DELAY, () ->
		{
			for (SidebarTab t : picks)
			{
				Plugin p = t.wrapped == wrappedShape && !isConfig(t) ? ownerOf(t.panel) : null;
				if (p != null)
				{
					owner[0] = p;
					target[0] = t;
					break;
				}
			}
			if (owner[0] == null)
			{
				log.info("[SM-TEST] skip: no stoppable plugin owns a merged {} panel", wrappedShape ? "wrapped" : "unwrapped");
				return;
			}
			tabsBefore[0] = sidebar.getTabCount();
			log.info("[SM-TEST] stopping {} (tab '{}')", owner[0].getClass().getSimpleName(), target[0].tooltip);
			try
			{
				pluginManager.stopPlugin(owner[0]);
			}
			catch (Exception e)
			{
				fail("stopPlugin threw: " + e);
			}
		});
		step("verify after stop", STEP_DELAY, () ->
		{
			if (owner[0] == null)
			{
				return;
			}
			check(sidebar.getTabCount() == tabsBefore[0] - 1, "its tab is gone (" + tabsBefore[0] + " -> " + sidebar.getTabCount() + ")");
			checkBookkeeping("after stop");
			check(!hasPlaceholderFor(target[0].panel), "no placeholder left for the stopped panel");
			check(orphanPlaceholders() == 0, "every placeholder belongs to a merged panel");
			check(!SwingUtilities.isDescendingFrom(target[0].panel, merged), "its panel left the merged tab");
			check(merged.borrowedPanels().size() == picks.size() - 1, "the other panels stay merged (" + merged.borrowedPanels().size() + ")");
			check(sidebar.getSelectedComponent() == merged, "merged tab still selected");
			NavigationButton sel = clientSelectedTab();
			log.info("[SM-TEST] info: ClientUI.selectedTab after stop = {}", sel == null ? null : sel.getTooltip());
			snapshot(snap + "-after-stop");
		});
		step("start it again", PLUGIN_DELAY, () ->
		{
			if (owner[0] == null)
			{
				return;
			}
			try
			{
				pluginManager.startPlugin(owner[0]);
			}
			catch (Exception e)
			{
				fail("startPlugin threw: " + e);
			}
		});
		step("verify after restart", STEP_DELAY, () ->
		{
			if (owner[0] == null)
			{
				return;
			}
			check(sidebar.getTabCount() == tabsBefore[0], "its tab is back");
			checkBookkeeping("after restart");
			SidebarTab now = SidebarTab.list(sidebar, merged, borrowedMap()).stream()
				.filter(t -> t.key().equals(target[0].key())).findFirst().orElse(null);
			check(now != null && SwingUtilities.isDescendingFrom(now.panel, merged), "restarted panel was merged again");
			check(merged.borrowedPanels().size() == picks.size(), "all " + picks.size() + " panels merged");
			// Keep later steps pointing at the live panel instance.
			if (now != null)
			{
				picks.set(picks.indexOf(target[0]), now);
			}
			verifyMerged(snap + "-after-restart", picks.size());
		});
	}

	private void verifyMerged(String snap, int expected)
	{
		List<SectionCard> cards = merged.cards();
		check(!merged.isTransitioning(), "no animation still running");
		check(cards.size() == expected, "borrowed " + cards.size() + "/" + expected);
		for (SectionCard card : cards)
		{
			SidebarTab t = card.borrowed.tab;
			if (card.isCollapsed())
			{
				check(!t.panel.isShowing() && SwingUtilities.isDescendingFrom(t.panel, merged), t.tooltip + " is held but hidden while collapsed");
			}
			else
			{
				check(t.panel.isShowing() && SwingUtilities.isDescendingFrom(t.panel, merged), t.tooltip + " is showing inside the merged tab");
			}
			if (t.wrapped)
			{
				check(t.panel.getWrappedPanel().getComponentCount() == 0, t.tooltip + ": its tab keeps the empty wrapper");
			}
			else
			{
				int i = sidebar.indexOfComponent(t.panel);
				check(i >= 0 && sidebar.getComponentAt(i) instanceof TabPlaceholder, t.tooltip + ": its tab holds a placeholder");
			}
		}

		int used = 0;
		int minAuto = Integer.MAX_VALUE;
		int maxAuto = Integer.MIN_VALUE;
		boolean anyExpanded = false;
		for (SectionCard c : cards)
		{
			String name = c.borrowed.tab.tooltip;
			int h = c.getHeight();
			used += h;
			if (c.isCollapsed())
			{
				check(h == Theme.HEADER_HEIGHT, name + ": collapsed card is header only (" + h + ")");
				continue;
			}
			anyExpanded = true;
			if (c.fixedHeight() > 0 && cards.stream().anyMatch(o -> !o.isCollapsed() && o.fixedHeight() <= 0))
			{
				check(h == c.fixedHeight(), name + ": resized card keeps its " + c.fixedHeight() + "px (" + h + ")");
			}
			else
			{
				minAuto = Math.min(minAuto, h);
				maxAuto = Math.max(maxAuto, h);
			}
			// Content taller than the card scrolls inside it.
			JScrollPane sp = scrollPaneOf(c);
			int viewPref = sp.getViewport().getView().getPreferredSize().height;
			int viewport = sp.getViewport().getHeight();
			if (c.borrowed.tab.wrapped)
			{
				int needed = sp.getViewport().getView().getPreferredSize().width;
				int have = sp.getViewport().getWidth();
				int padding = c.borrowed.tab.panel.getInsets().right;
				int lost = needed - padding - have;
				if (lost == 1)
				{
					log.info("[SM-TEST] NOTE {}: loses its rightmost pixel at the {}px side margin ({} of {}px, no padding, wide scrollbar)",
						name, Theme.SIDE, have, needed);
				}
				else
				{
					check(lost <= 0, name + ": no content clipped sideways (" + have + " of " + needed + "px, "
						+ padding + "px of that is the panel's own padding)");
				}
			}
			if (viewPref > viewport + 2)
			{
				check(sp.getVerticalScrollBar().isVisible(), name + ": content " + viewPref + " > " + viewport + " scrolls");
			}
		}
		if (maxAuto >= 0)
		{
			check(maxAuto - minAuto <= 1, "auto cards share equally (" + minAuto + ".." + maxAuto + ")");
		}
		Container stack = cards.isEmpty() ? null : cards.get(0).getParent();
		if (stack != null && anyExpanded)
		{
			int inner = stack.getHeight() - stack.getInsets().top - stack.getInsets().bottom - Theme.GAP * (cards.size() - 1);
			check(used == inner, "cards fill the tab exactly (" + used + " == " + inner + ")");
		}
		checkBookkeeping("merged");
		snapshot(snap);
	}

	/**
	 * Drag the border under card {@code gap} by {@code dy} with real mouse events, sent to
	 * whatever is under the pointer (the grab strip) the way AWT routes a user's drag.
	 * {@code offset} moves the grab point off the gap's centre, e.g. into a card's edge.
	 */
	private void dragBorder(int gap, int dy)
	{
		dragBorder(gap, dy, 0);
	}

	private void dragBorder(int gap, int dy, int offset)
	{
		Container stack = merged.cards().get(0).getParent();
		int x = stack.getWidth() / 2;
		int y = borderY(gap) + offset;
		Component target = SwingUtilities.getDeepestComponentAt(stack, x, y);
		check(target != null && target.getClass().getSimpleName().equals("Grip"),
			"grab strip under the pointer " + (offset == 0 ? "on the gap" : offset + "px off it") + " ("
				+ (target == null ? null : target.getClass().getSimpleName()) + ")");
		mouseVia(stack, target, MouseEvent.MOUSE_MOVED, x, y, 0);
		mouseVia(stack, target, MouseEvent.MOUSE_PRESSED, x, y, 1);
		for (int step = 1; step <= 4; step++)
		{
			mouseVia(stack, target, MouseEvent.MOUSE_DRAGGED, x, y + dy * step / 4, 1);
		}
		mouseVia(stack, target, MouseEvent.MOUSE_RELEASED, x, y + dy, 1);
	}

	private void doubleClickBorder(int gap)
	{
		Container stack = merged.cards().get(0).getParent();
		int x = stack.getWidth() / 2;
		int y = borderY(gap);
		Component target = SwingUtilities.getDeepestComponentAt(stack, x, y);
		for (int click = 1; click <= 2; click++)
		{
			mouseVia(stack, target, MouseEvent.MOUSE_PRESSED, x, y, click);
			mouseVia(stack, target, MouseEvent.MOUSE_RELEASED, x, y, click);
			mouseVia(stack, target, MouseEvent.MOUSE_CLICKED, x, y, click);
		}
	}

	/** A click on a card header with real mouse events, as a user's click arrives. */
	private void clickHeader(SectionCard card)
	{
		Component header = card.header();
		int x = header.getWidth() / 3;
		int y = header.getHeight() / 2;
		mouse(header, MouseEvent.MOUSE_PRESSED, x, y, 1);
		mouse(header, MouseEvent.MOUSE_RELEASED, x, y, 1);
		mouse(header, MouseEvent.MOUSE_CLICKED, x, y, 1);
	}

	/** Mouse event at stack coordinates (x, y), delivered to {@code target} in its own coordinates. */
	private static void mouseVia(Container stack, Component target, int id, int x, int y, int clicks)
	{
		Component to = target == null ? stack : target;
		java.awt.Point p = SwingUtilities.convertPoint(stack, x, y, to);
		mouse(to, id, p.x, p.y, clicks);
	}

	private int borderY(int gap)
	{
		List<SectionCard> cards = merged.cards();
		SectionCard a = cards.get(gap);
		return (a.getY() + a.getHeight() + cards.get(gap + 1).getY()) / 2;
	}

	private static void mouse(Component target, int id, int x, int y, int clicks)
	{
		boolean button = id != MouseEvent.MOUSE_MOVED;
		int mods = button && id != MouseEvent.MOUSE_RELEASED && id != MouseEvent.MOUSE_CLICKED ? InputEvent.BUTTON1_DOWN_MASK : 0;
		target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), mods, x, y, clicks, false,
			button ? MouseEvent.BUTTON1 : MouseEvent.NOBUTTON));
	}

	private void verifyAllReturned()
	{
		check(merged.borrowedPanels().isEmpty(), "nothing borrowed");
		check(noPlaceholders(), "no placeholders in the sidebar");
		for (SidebarTab t : picks)
		{
			if (t.wrapped)
			{
				check(t.panel.getWrappedPanel().getComponentCount() == 1, t.tooltip + ": scroll pane back in its wrapper");
			}
			else
			{
				check(sidebar.indexOfComponent(t.panel) >= 0 && sidebar.getComponentAt(sidebar.indexOfComponent(t.panel)) == t.panel,
					t.tooltip + ": panel back in its tab");
			}
		}
		checkBookkeeping("returned");
	}

	/** Tabs must stay one-to-one with ClientUI's sorted button set, or clicks open the wrong panel. */
	private void checkBookkeeping(String when)
	{
		try
		{
			Field f = ClientUI.class.getDeclaredField("sidebarEntries");
			f.setAccessible(true);
			int entries = ((Collection<?>) f.get(clientUI)).size();
			check(entries == sidebar.getTabCount(), "ClientUI bookkeeping in line " + when + " (" + entries + " buttons, " + sidebar.getTabCount() + " tabs)");
		}
		catch (ReflectiveOperationException e)
		{
			fail("could not read ClientUI.sidebarEntries: " + e);
		}
	}

	private NavigationButton clientSelectedTab()
	{
		try
		{
			Field f = ClientUI.class.getDeclaredField("selectedTab");
			f.setAccessible(true);
			return (NavigationButton) f.get(clientUI);
		}
		catch (ReflectiveOperationException e)
		{
			fail("could not read ClientUI.selectedTab: " + e);
			return null;
		}
	}

	private SidebarTab clickTarget()
	{
		return picks.stream().filter(t -> !t.wrapped).findFirst().orElse(picks.get(0));
	}

	private int tabIndexOf(SidebarTab t)
	{
		// For an unwrapped panel this finds its placeholder, via TabPlaceholder.equals.
		return sidebar.indexOfComponent(t.wrapped ? t.panel.getWrappedPanel() : t.panel);
	}

	private boolean noPlaceholders()
	{
		for (int i = 0; i < sidebar.getTabCount(); i++)
		{
			if (sidebar.getComponentAt(i) instanceof TabPlaceholder)
			{
				return false;
			}
		}
		return true;
	}

	private boolean hasPlaceholderFor(PluginPanel panel)
	{
		for (int i = 0; i < sidebar.getTabCount(); i++)
		{
			Component c = sidebar.getComponentAt(i);
			if (c instanceof TabPlaceholder && ((TabPlaceholder) c).owner == panel)
			{
				return true;
			}
		}
		return false;
	}

	private int orphanPlaceholders()
	{
		int orphans = 0;
		for (int i = 0; i < sidebar.getTabCount(); i++)
		{
			Component c = sidebar.getComponentAt(i);
			if (c instanceof TabPlaceholder && merged.borrowedPanels().stream().noneMatch(b -> b.tabComponent() == c))
			{
				orphans++;
			}
		}
		return orphans;
	}

	private IdentityHashMap<Component, SidebarTab> borrowedMap()
	{
		IdentityHashMap<Component, SidebarTab> map = new IdentityHashMap<>();
		for (BorrowedPanel b : merged.borrowedPanels())
		{
			map.put(b.tabComponent(), b.tab);
		}
		return map;
	}

	private Plugin ownerOf(PluginPanel panel)
	{
		String pkg = panel.getClass().getPackage().getName();
		Plugin best = null;
		int bestLen = -1;
		for (Plugin p : pluginManager.getPlugins())
		{
			if (p instanceof SidebarMergePlugin || p instanceof SidebarMergeSelfTestPlugin || !pluginManager.isPluginActive(p))
			{
				continue;
			}
			String ppkg = p.getClass().getPackage().getName();
			if ((pkg.equals(ppkg) || pkg.startsWith(ppkg + ".")) && ppkg.length() > bestLen)
			{
				best = p;
				bestLen = ppkg.length();
			}
		}
		return best;
	}

	private boolean isConfig(SidebarTab t)
	{
		return "Configuration".equals(t.tooltip) || t.key().startsWith("net.runelite.client.plugins.config.");
	}

	private int contentHeight(SidebarTab t)
	{
		return t.wrapped && t.panel.getWrappedPanel().getComponentCount() == 1
			? t.panel.getWrappedPanel().getComponent(0).getPreferredSize().height
			: t.panel.getPreferredSize().height;
	}

	private List<String> keys()
	{
		return picks.stream().map(SidebarTab::key).collect(Collectors.toList());
	}

	private String getConfig(String key)
	{
		String v = configManager.getConfiguration(SidebarMergeConfig.GROUP, key);
		return v == null ? "" : v;
	}

	private void setConfig(String key, String value)
	{
		configManager.setConfiguration(SidebarMergeConfig.GROUP, key, value);
	}

	/** The active preset as written to config, so checks also prove it was saved. */
	private PresetBook.Preset savedActive()
	{
		return PresetBook.fromJson(GSON, getConfig(SidebarMergeConfig.PRESETS_KEY)).active();
	}

	private List<String> savedList(String key)
	{
		PresetBook.Preset p = savedActive();
		return key.equals(SidebarMergeConfig.MERGED_PANELS_KEY) ? new ArrayList<>(p.panels) : new ArrayList<>(p.collapsed);
	}

	private Map<String, Integer> savedHeights()
	{
		return new LinkedHashMap<>(savedActive().heights);
	}

	/** The user's presets as found, minus test presets an earlier run left behind. */
	private String restoredPresets()
	{
		PresetBook book = PresetBook.fromJson(GSON, originalPresets);
		for (String name : book.names())
		{
			if (name.startsWith(TEST_PRESET) && book.names().size() > 1)
			{
				book.switchTo(name);
				book.deleteActive();
			}
		}
		return book.toJson(GSON);
	}

	private void removeTestPresets()
	{
		for (String name : sm.presetNames())
		{
			if (name.startsWith(TEST_PRESET) && sm.presetNames().size() > 1)
			{
				sm.switchPreset(name);
				sm.deleteActivePreset();
			}
		}
	}

	private SidebarTab pickFor(String key)
	{
		return picks.stream().filter(t -> t.key().equals(key)).findFirst().orElseThrow(IllegalStateException::new);
	}

	private PickerView findPicker()
	{
		PickerView p = findComponent(merged, PickerView.class);
		if (p == null)
		{
			throw new IllegalStateException("no picker");
		}
		return p;
	}

	private static JLabel findLabel(Container root, String text)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JLabel && text.equals(((JLabel) c).getText()))
			{
				return (JLabel) c;
			}
			if (c instanceof Container)
			{
				JLabel found = findLabel((Container) c, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static <T> T findComponent(Container root, Class<T> type)
	{
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				return type.cast(c);
			}
			if (c instanceof Container)
			{
				T found = findComponent((Container) c, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static JScrollPane scrollPaneOf(SectionCard c)
	{
		Component content = c.borrowed.content();
		return content instanceof JScrollPane ? (JScrollPane) content : null;
	}

	/** First showing button whose text or tooltip is {@code label}. */
	private static JButton findButton(Container root, String label)
	{
		JButton b = searchButton(root, label);
		if (b == null)
		{
			throw new IllegalStateException("no showing '" + label + "' button");
		}
		return b;
	}

	private static JButton searchButton(Container root, String label)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof JButton && c.isShowing()
				&& (label.equals(((JButton) c).getText()) || label.equals(((JButton) c).getToolTipText())))
			{
				return (JButton) c;
			}
			if (c instanceof Container)
			{
				JButton b = searchButton((Container) c, label);
				if (b != null)
				{
					return b;
				}
			}
		}
		return null;
	}

	/** Like {@link #snapshot}, but painted from the root pane so open popup menus are included. */
	private void snapshotWithPopups(String name)
	{
		try
		{
			JRootPane root = SwingUtilities.getRootPane(sidebar);
			BufferedImage full = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = full.createGraphics();
			root.paint(g);
			g.dispose();
			java.awt.Point at = SwingUtilities.convertPoint(sidebar, 0, 0, root);
			BufferedImage img = full.getSubimage(at.x, at.y, sidebar.getWidth(), sidebar.getHeight());
			// RuneLite makes popups heavyweight (separate windows, so they sit above the game
			// canvas); they aren't in the root pane, so paint the open menu on at its position.
			javax.swing.JPopupMenu menu = merged.presetMenu();
			if (menu != null && menu.isShowing())
			{
				java.awt.Point m = menu.getLocationOnScreen();
				java.awt.Point s = sidebar.getLocationOnScreen();
				Graphics2D mg = img.createGraphics();
				mg.translate(m.x - s.x, m.y - s.y);
				menu.paint(mg);
				mg.dispose();
			}
			File file = new File(outDir, name + ".png");
			ImageIO.write(img, "png", file);
			log.info("[SM-TEST] snapshot {}", file.getAbsolutePath());
		}
		catch (Exception e)
		{
			log.warn("[SM-TEST] snapshot {} failed", name, e);
		}
	}

	private void snapshot(String name)
	{
		try
		{
			BufferedImage img = new BufferedImage(sidebar.getWidth(), sidebar.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = img.createGraphics();
			sidebar.paint(g);
			g.dispose();
			File file = new File(outDir, name + ".png");
			ImageIO.write(img, "png", file);
			log.info("[SM-TEST] snapshot {}", file.getAbsolutePath());
		}
		catch (Exception e)
		{
			log.warn("[SM-TEST] snapshot {} failed", name, e);
		}
	}

	private void check(boolean ok, String what)
	{
		if (ok)
		{
			passed++;
			log.info("[SM-TEST] PASS {}", what);
		}
		else
		{
			fail(what);
		}
	}

	private void fail(String what)
	{
		failures.add(what);
		log.info("[SM-TEST] FAIL {}", what);
	}

	private void step(String name, int delayAfter, Runnable action)
	{
		queue.add(new Step(name, delayAfter, action));
	}

	private void next()
	{
		Step s = queue.poll();
		if (s == null)
		{
			return;
		}
		log.info("[SM-TEST] step: {}", s.name);
		try
		{
			s.action.run();
		}
		catch (Throwable t)
		{
			fail("step '" + s.name + "' threw " + t);
			log.warn("[SM-TEST] step threw", t);
		}
		later(Math.max(1, s.delayAfter), this::next);
	}

	private void later(int ms, Runnable r)
	{
		timer = new Timer(ms, e -> r.run());
		timer.setRepeats(false);
		timer.start();
	}

	private static final class Step
	{
		final String name;
		final int delayAfter;
		final Runnable action;

		Step(String name, int delayAfter, Runnable action)
		{
			this.name = name;
			this.delayAfter = delayAfter;
			this.action = action;
		}
	}
}
