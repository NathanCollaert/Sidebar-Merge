package com.sidebarmerge;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Sits in a sidebar tab while that tab's unwrapped panel is shown in the merged tab.
 *
 * ClientUI removes a plugin's tab with {@code sidebar.remove(panel.getWrappedPanel())}, and
 * JTabbedPane finds that tab by asking each tab component whether it {@code equals()} the
 * argument. Answering yes for the borrowed panel means a plugin that stops (or hides its icon)
 * while we hold its panel still has its own tab removed, instead of leaving a dead tab behind
 * that would knock ClientUI's tab-index bookkeeping out of line.
 */
final class TabPlaceholder extends JPanel
{
	final PluginPanel owner;

	TabPlaceholder(PluginPanel owner)
	{
		super(new BorderLayout());
		this.owner = owner;
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(16, 16, 16, 16));
		JLabel label = new JLabel("Shown in the Sidebar Merge tab", SwingConstants.CENTER);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(label, BorderLayout.NORTH);
	}

	@Override
	public boolean equals(Object o)
	{
		return o == this || o == owner;
	}

	// Identity hash on purpose: Swing keeps components in identity-keyed maps, and nothing should
	// ever look this up in place of the panel. Only JTabbedPane.indexOfComponent needs the match.
	@Override
	public int hashCode()
	{
		return super.hashCode();
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH, 0);
	}
}
