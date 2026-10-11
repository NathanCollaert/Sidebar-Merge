package com.sidebarmerge;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.SwingUtil;

/**
 * A plugin panel lent to the merged tab. A Swing component can only have one parent, so the
 * panel really moves; it goes back to its own tab whenever the merged tab is not showing.
 *
 * The one rule: never add or remove sidebar tabs. ClientUI maps tab index to its own sorted
 * set of navigation buttons, so tabs must stay one-to-one with that set. Only what sits
 * inside a tab may change.
 */
@Slf4j
abstract class BorrowedPanel
{
	final SidebarTab tab;
	private boolean activated;

	private BorrowedPanel(SidebarTab tab)
	{
		this.tab = tab;
	}

	/** Returns null if the tab isn't shaped the way we expect; it is then left alone. */
	static BorrowedPanel borrow(JTabbedPane sidebar, SidebarTab tab)
	{
		return tab.wrapped ? Wrapped.borrow(tab) : Unwrapped.borrow(sidebar, tab);
	}

	/** What the merged tab shows. Adding it to a container is what moves it out of the tab. */
	abstract Component content();

	/** The component sitting in the sidebar tab while we hold the content. */
	abstract Component tabComponent();

	/** Put the content back in its tab. */
	abstract void giveBack(JTabbedPane sidebar);

	/** ClientUI removed the plugin's tab while we held the content (plugin stopped, icon hidden). */
	abstract void tabRemoved();

	/** Tell the plugin its panel is on screen, as ClientUI does for a selected tab. Idempotent. */
	void activate()
	{
		if (!activated)
		{
			activated = true;
			SwingUtil.activate(tab.panel);
		}
	}

	void deactivate()
	{
		if (activated)
		{
			activated = false;
			SwingUtil.deactivate(tab.panel);
		}
	}

	boolean isActivated()
	{
		return activated;
	}

	/**
	 * PluginPanel(true): move only the plugin's own JScrollPane out of RuneLite's wrapper. The
	 * tab keeps the (now empty) wrapper, and the plugin keeps its own scrolling, which is what
	 * caps it at its share of the height.
	 */
	private static final class Wrapped extends BorrowedPanel
	{
		private final JPanel wrapper;
		private final Component content;
		private final Object constraints;

		private Wrapped(SidebarTab tab, JPanel wrapper, Component content, Object constraints)
		{
			super(tab);
			this.wrapper = wrapper;
			this.content = content;
			this.constraints = constraints;
		}

		static Wrapped borrow(SidebarTab tab)
		{
			JPanel wrapper = tab.panel.getWrappedPanel();
			if (wrapper.getComponentCount() != 1)
			{
				log.warn("{}: wrapper has {} children, expected 1; skipping", tab.tooltip, wrapper.getComponentCount());
				return null;
			}
			Component content = wrapper.getComponent(0);
			Object constraints = wrapper.getLayout() instanceof BorderLayout
				? ((BorderLayout) wrapper.getLayout()).getConstraints(content)
				: null;
			return new Wrapped(tab, wrapper, content, constraints);
		}

		@Override
		Component content()
		{
			return content;
		}

		@Override
		Component tabComponent()
		{
			return wrapper;
		}

		@Override
		void giveBack(JTabbedPane sidebar)
		{
			restore();
		}

		// Restore even though the tab is gone: a plugin that re-adds the same button later
		// (e.g. a "show sidebar icon" toggle) should get its panel back intact.
		@Override
		void tabRemoved()
		{
			restore();
		}

		private void restore()
		{
			if (content.getParent() != wrapper)
			{
				// Same window: re-parent without tearing down the panel's native state (see
				// SectionCard.attachContent). BorderLayout takes a null constraint as CENTER,
				// which is where PluginPanel puts it. A tab that's gone needs the plain add.
				java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(wrapper);
				if (window != null && javax.swing.SwingUtilities.getWindowAncestor(content) == window
					&& BorderLayout.CENTER.equals(constraints))
				{
					wrapper.setComponentZOrder(content, 0);
				}
				else
				{
					wrapper.add(content, constraints);
				}
			}
			content.setVisible(true);
			wrapper.revalidate();
			wrapper.repaint();
		}
	}

	/**
	 * PluginPanel(false): the tab component is the panel itself. Moving it straight into our
	 * container would make Swing call JTabbedPane.remove(panel), which deletes the tab. So swap a
	 * placeholder into the tab first (setComponentAt keeps the tab), then move the panel into our
	 * own scroll pane. It stretches to fill its share and scrolls if its content is taller.
	 */
	private static final class Unwrapped extends BorrowedPanel
	{
		private final TabPlaceholder placeholder;
		private final JScrollPane scroll;

		private Unwrapped(SidebarTab tab)
		{
			super(tab);
			placeholder = new TabPlaceholder(tab.panel);
			scroll = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			scroll.setBorder(null);
			scroll.getVerticalScrollBar().setUnitIncrement(16);
		}

		static Unwrapped borrow(JTabbedPane sidebar, SidebarTab tab)
		{
			int index = sidebar.indexOfComponent(tab.panel);
			if (index < 0)
			{
				log.warn("{}: panel not found in the sidebar; skipping", tab.tooltip);
				return null;
			}
			Unwrapped b = new Unwrapped(tab);
			sidebar.setComponentAt(index, b.placeholder);
			// JTabbedPane hides the components of unselected tabs.
			tab.panel.setVisible(true);
			b.scroll.setViewportView(new StretchView(tab.panel));
			return b;
		}

		@Override
		Component content()
		{
			return scroll;
		}

		@Override
		Component tabComponent()
		{
			return placeholder;
		}

		@Override
		void giveBack(JTabbedPane sidebar)
		{
			int index = sidebar.indexOfComponent(placeholder);
			if (index >= 0)
			{
				// Re-parents the panel out of our StretchView and sets its visibility for the tab.
				sidebar.setComponentAt(index, tab.panel);
			}
			else
			{
				detach();
			}
		}

		@Override
		void tabRemoved()
		{
			detach();
		}

		private void detach()
		{
			Container parent = tab.panel.getParent();
			if (parent != null)
			{
				parent.remove(tab.panel);
			}
		}
	}

	/** Fills the viewport when there's room, scrolls when the panel is taller than its share. */
	private static final class StretchView extends JPanel implements Scrollable
	{
		StretchView(Component content)
		{
			super(new BorderLayout());
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			add(content, BorderLayout.CENTER);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
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
			return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
		}
	}
}
