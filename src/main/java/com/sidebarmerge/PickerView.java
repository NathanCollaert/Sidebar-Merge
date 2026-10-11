package com.sidebarmerge;

import com.sidebarmerge.ui.Icons;
import com.sidebarmerge.ui.Theme;
import com.sidebarmerge.ui.Widgets;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.components.IconTextField;

/**
 * Edit mode: search, switch panels on or off, and order the merged ones top to bottom. Two
 * grouped lists, "In this tab" and "Available": one rounded box each, rows split by hairlines.
 */
final class PickerView extends JPanel
{
	private static final int ROW_HEIGHT = 32;
	private static final int SEARCH_HEIGHT = 28;

	private final IconTextField search = new IconTextField();
	private final JPanel column = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
	private final Map<String, SidebarTab> tabs = new LinkedHashMap<>();
	private final List<String> selected = new ArrayList<>();

	PickerView()
	{
		super(new BorderLayout());
		setBackground(Theme.CANVAS);

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(0, SEARCH_HEIGHT));
		search.setBackground(Theme.SURFACE);
		search.setHoverBackgroundColor(Theme.SURFACE_HOVER);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				rebuild();
			}
		});
		search.addClearListener(this::rebuild);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(Theme.CANVAS);
		top.setBorder(new EmptyBorder(Theme.SPACE_S, Theme.SPACE_S, 0, Theme.SPACE_S));
		top.add(search, BorderLayout.CENTER);

		column.setBackground(Theme.CANVAS);
		JPanel north = new WidthTrackingPanel();
		north.setBackground(Theme.CANVAS);
		north.setBorder(new EmptyBorder(0, Theme.SPACE_S, Theme.SPACE_L, Theme.SPACE_S));
		north.add(column, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(north, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(Theme.CANVAS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		add(top, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
	}

	void load(List<SidebarTab> available, List<String> selection)
	{
		tabs.clear();
		for (SidebarTab t : available)
		{
			tabs.putIfAbsent(t.key(), t);
		}
		selected.clear();
		selected.addAll(selection);
		search.setText("");
		rebuild();
	}

	void focusSearch()
	{
		search.requestFocusInWindow();
	}

	void setFilter(String text)
	{
		search.setText(text);
	}

	List<String> selection()
	{
		return new ArrayList<>(selected);
	}

	private void rebuild()
	{
		String filter = search.getText() == null ? "" : search.getText().trim().toLowerCase();
		column.removeAll();

		List<JComponent> merged = new ArrayList<>();
		for (int i = 0; i < selected.size(); i++)
		{
			if (matches(selected.get(i), filter))
			{
				merged.add(row(selected.get(i), i, filter.isEmpty()));
			}
		}
		column.add(overline("IN THIS TAB", selected.size(), Theme.SPACE_M));
		column.add(group(merged, selected.isEmpty() ? "Nothing yet. Switch panels on below." : "No merged panels match."));

		List<JComponent> available = tabs.values().stream()
			.filter(t -> !selected.contains(t.key()) && matches(t.key(), filter))
			.sorted(Comparator.comparing(t -> t.tooltip.toLowerCase()))
			.map(t -> row(t.key(), -1, false))
			.collect(Collectors.toList());
		column.add(overline("AVAILABLE", available.size(), Theme.SPACE_L));
		column.add(group(available, filter.isEmpty() ? "Every panel is already merged." : "No panels match “" + filter + "”."));

		column.revalidate();
		column.repaint();
	}

	private boolean matches(String key, String filter)
	{
		return filter.isEmpty() || title(key).toLowerCase().contains(filter);
	}

	private String title(String key)
	{
		SidebarTab t = tabs.get(key);
		return t != null ? t.tooltip : key.substring(key.lastIndexOf('.') + 1);
	}

	/** {@code position} is the index among merged panels, or -1 for an available one. */
	private JComponent row(String key, int position, boolean showArrows)
	{
		SidebarTab tab = tabs.get(key);
		boolean on = position >= 0;

		Row row = new Row(() -> toggle(key));
		Icon icon = tab != null ? tab.icon : Icons.logoMuted();
		JLabel label = new JLabel(tab != null ? tab.tooltip : title(key) + "  ·  not loaded", icon, SwingConstants.LEFT);
		label.setFont(Theme.small());
		label.setForeground(tab != null ? Theme.TEXT : Theme.TEXT_FAINT);
		label.setIconTextGap(Theme.SPACE_S);

		List<java.awt.Component> controls = new ArrayList<>();
		if (on && showArrows)
		{
			controls.add(enabled(Widgets.iconButton(Icons.arrowUp(), "Move up", () -> reorder(position, -1)), position > 0));
			controls.add(enabled(Widgets.iconButton(Icons.arrowDown(), "Move down", () -> reorder(position, 1)), position < selected.size() - 1));
		}
		controls.add(new Widgets.Switch(on));

		row.add(label, BorderLayout.CENTER);
		row.add(Widgets.hbox(Theme.SPACE_XS, controls.toArray(new java.awt.Component[0])), BorderLayout.EAST);
		return row;
	}

	private void toggle(String key)
	{
		if (!selected.remove(key))
		{
			selected.add(key);
		}
		rebuild();
	}

	private void reorder(int position, int delta)
	{
		String key = selected.remove(position);
		selected.add(position + delta, key);
		rebuild();
	}

	private static JComponent enabled(JComponent c, boolean enabled)
	{
		c.setEnabled(enabled);
		return c;
	}

	/** Section label: close to the list it names, further from the one above. */
	private static JComponent overline(String text, int count, int spaceAbove)
	{
		JLabel l = Widgets.overline(text + "  ·  " + count);
		l.setBorder(new EmptyBorder(spaceAbove, Theme.SPACE_XS, Theme.SPACE_XS, 0));
		return l;
	}

	/** Rows in one rounded box, a hairline between each; an empty box shows {@code emptyText}. */
	private static JComponent group(List<JComponent> rows, String emptyText)
	{
		Group group = new Group();
		if (rows.isEmpty())
		{
			JLabel hint = new JLabel(emptyText);
			hint.setFont(Theme.small());
			hint.setForeground(Theme.TEXT_FAINT);
			// Height only: DynamicGridLayout scales widths from preferred widths, so a 0 width
			// would collapse the row to nothing.
			hint.setBorder(new EmptyBorder((ROW_HEIGHT - hint.getPreferredSize().height) / 2, Theme.SPACE_XS,
				(ROW_HEIGHT - hint.getPreferredSize().height + 1) / 2, Theme.SPACE_XS));
			group.add(hint);
		}
		for (int i = 0; i < rows.size(); i++)
		{
			if (i > 0)
			{
				group.add(new Hairline());
			}
			group.add(rows.get(i));
		}
		return group;
	}

	/**
	 * The scrolling list. A plain panel would be laid out at its preferred width, the widest row,
	 * and a long name pushes every row's switch past the right edge. Tracking the viewport width
	 * keeps rows inside it; long names shorten with an ellipsis instead.
	 */
	private static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable
	{
		WidthTrackingPanel()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction)
		{
			return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private static final class Group extends JPanel
	{
		Group()
		{
			super(new DynamicGridLayout(0, 1, 0, 0));
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, Theme.ARC, Theme.ARC);
			g2.setColor(Theme.SURFACE);
			g2.fill(shape);
			g2.setColor(Theme.OUTLINE);
			g2.draw(shape);
			g2.dispose();
		}
	}

	/** A 1px divider, inset to the rows' content so it doesn't touch the box edges. */
	private static final class Hairline extends JComponent
	{
		Hairline()
		{
			setPreferredSize(new Dimension(0, 1));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(Theme.OUTLINE);
			g.fillRect(Theme.SPACE_XS, 0, getWidth() - 2 * Theme.SPACE_XS, 1);
		}
	}

	/** A hoverable row that toggles when clicked anywhere outside its buttons. */
	private static final class Row extends JPanel
	{
		private boolean hover;

		Row(Runnable onClick)
		{
			super(new BorderLayout(Theme.SPACE_S, 0));
			setOpaque(false);
			setBorder(new EmptyBorder(Theme.SPACE_XS, Theme.SPACE_XS, Theme.SPACE_XS, Theme.SPACE_XS));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					setHover(true);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					setHover(contains(e.getPoint()));
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					onClick.run();
				}
			});
		}

		// Fixed height, natural width: DynamicGridLayout scales widths from preferred widths,
		// so pinning the width to 0 would collapse the row to nothing.
		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(super.getPreferredSize().width, ROW_HEIGHT);
		}

		private void setHover(boolean hover)
		{
			if (this.hover != hover)
			{
				this.hover = hover;
				repaint();
			}
		}

		// Inset 2px with a smaller radius, so the highlight sits inside the box's rounded corners.
		@Override
		protected void paintComponent(Graphics g)
		{
			if (hover)
			{
				Graphics2D g2 = Theme.smooth(g);
				g2.setColor(Theme.SURFACE_HOVER);
				g2.fill(new RoundRectangle2D.Double(2, 2, getWidth() - 4, getHeight() - 4, 6, 6));
				g2.dispose();
			}
		}
	}
}
