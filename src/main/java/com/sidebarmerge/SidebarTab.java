package com.sidebarmerge;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/**
 * One plugin tab in RuneLite's sidebar, read through plain Swing. ClientUI keeps the sidebar as
 * a JTabbedPane whose tab components are each plugin's {@link PluginPanel#getWrappedPanel()}.
 */
final class SidebarTab
{
	final String tooltip;
	final Icon icon;
	final PluginPanel panel;
	/**
	 * True for the default PluginPanel(true): the tab holds RuneLite's wrapper JPanel around the
	 * plugin's own JScrollPane. False for PluginPanel(false): the tab holds the panel itself.
	 */
	final boolean wrapped;

	private SidebarTab(String tooltip, Icon icon, PluginPanel panel, boolean wrapped)
	{
		this.tooltip = tooltip;
		this.icon = icon;
		this.panel = panel;
		this.wrapped = wrapped;
	}

	/** Stable across restarts; tooltips are for display only. */
	String key()
	{
		return panel.getClass().getName();
	}

	static JTabbedPane findSidebar(Component inside)
	{
		return (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, inside);
	}

	/**
	 * Every plugin tab except {@code self}. Tabs we currently hold content from look empty (or hold
	 * a placeholder), so {@code borrowed} maps their tab component back to what they were.
	 */
	static List<SidebarTab> list(JTabbedPane sidebar, Component self, Map<Component, SidebarTab> borrowed)
	{
		List<SidebarTab> out = new ArrayList<>();
		for (int i = 0; i < sidebar.getTabCount(); i++)
		{
			Component c = sidebar.getComponentAt(i);
			if (c == self)
			{
				continue;
			}
			SidebarTab tab = borrowed.get(c);
			if (tab == null)
			{
				tab = resolve(c, sidebar.getToolTipTextAt(i), sidebar.getIconAt(i));
			}
			if (tab != null)
			{
				out.add(tab);
			}
		}
		return out;
	}

	private static SidebarTab resolve(Component c, String tooltip, Icon icon)
	{
		if (c instanceof PluginPanel && ((PluginPanel) c).getWrappedPanel() == c)
		{
			return new SidebarTab(tooltip, icon, (PluginPanel) c, false);
		}
		PluginPanel owner = findWrappedOwner(c, c, 0);
		return owner == null ? null : new SidebarTab(tooltip, icon, owner, true);
	}

	// wrapper -> JScrollPane -> JViewport -> north panel -> PluginPanel: four levels down.
	private static PluginPanel findWrappedOwner(Component tabComponent, Component c, int depth)
	{
		if (c instanceof PluginPanel && ((PluginPanel) c).getWrappedPanel() == tabComponent)
		{
			return (PluginPanel) c;
		}
		if (depth >= 6 || !(c instanceof Container))
		{
			return null;
		}
		for (Component child : ((Container) c).getComponents())
		{
			PluginPanel p = findWrappedOwner(tabComponent, child, depth + 1);
			if (p != null)
			{
				return p;
			}
		}
		return null;
	}
}
