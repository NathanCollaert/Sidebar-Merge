package com.sidebarmerge;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

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

	/**
	 * ClientUI removes a plugin's tab with {@code sidebar.remove(panel.getWrappedPanel())}, and
	 * JTabbedPane finds that tab through {@code equals()}. Matching the borrowed panel means a plugin
	 * that stops (or hides its icon) while we hold its panel still loses its tab, instead of leaving
	 * a dead tab that would knock ClientUI's tab-index bookkeeping out of line.
	 */
	@Override
	public boolean equals(Object o)
	{
		return o == this || o == owner;
	}

	// Identity hash on purpose: only JTabbedPane.indexOfComponent should match this to the panel,
	// never a hash lookup.
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
