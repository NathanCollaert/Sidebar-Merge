package com.sidebarmerge;

import com.sidebarmerge.ui.Theme;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Lays the cards out top to bottom and fills the tab:
 * <ul>
 * <li>a collapsed card takes its header only;</li>
 * <li>a card the user resized keeps its height;</li>
 * <li>every other (auto) card gets an equal share of what is left.</li>
 * </ul>
 * If resized cards would leave the auto cards less than {@link #MIN_AUTO} each, the resized cards
 * are squeezed in proportion for display; their saved heights don't change. With no auto cards,
 * the resized cards scale to fill the tab.
 *
 * Changes can glide: {@link #beginTransition} before changing a card's state, and the next
 * layouts interpolate from the old heights to the new ones.
 */
@Slf4j
final class PartitionLayout implements LayoutManager
{
	static final int MIN_AUTO = 80;
	private static final long TRANSITION_NANOS = 180_000_000L;

	private final int gap;
	private int[] lastAllocation = new int[0];
	private int lastAvailable = -1;

	private Map<Component, Integer> fromHeights;
	private long transitionStart;
	private boolean frozen;
	private Timer ticker;

	PartitionLayout(int gap)
	{
		this.gap = gap;
	}

	/**
	 * @param fixed     each card's saved height, or -1 for an auto card (ignored when collapsed)
	 * @param collapsed which cards show their header only
	 * @param header    a collapsed card's height
	 * @param available height to fill, gaps already taken off
	 */
	static int[] allocate(int[] fixed, boolean[] collapsed, int header, int available)
	{
		int n = fixed.length;
		int[] h = new int[n];
		int space = available;
		int autos = 0;
		int fixeds = 0;
		long fixedSum = 0;
		for (int i = 0; i < n; i++)
		{
			if (collapsed[i])
			{
				h[i] = header;
				space -= header;
			}
			else if (fixed[i] > 0)
			{
				fixeds++;
				fixedSum += fixed[i];
			}
			else
			{
				autos++;
			}
		}
		space = Math.max(0, space);

		int fixedSpace;
		if (autos == 0)
		{
			fixedSpace = fixeds == 0 ? 0 : space;
		}
		else
		{
			// When the tab is crowded, the floor drops to an equal split so it can always be met.
			int minAuto = Math.min(MIN_AUTO, space / (autos + fixeds));
			fixedSpace = (int) Math.min(fixedSum, Math.max(0, space - (long) autos * minAuto));
		}

		// Resized cards: their saved heights, or scaled in proportion to them. Cumulative rounding
		// makes the parts add up to exactly fixedSpace.
		long acc = 0;
		int prevEnd = 0;
		int used = 0;
		for (int i = 0; i < n; i++)
		{
			if (!collapsed[i] && fixed[i] > 0)
			{
				acc += fixed[i];
				int end = fixedSpace == fixedSum ? (int) acc : (int) Math.round((double) fixedSpace * acc / fixedSum);
				h[i] = end - prevEnd;
				prevEnd = end;
				used += h[i];
			}
		}

		// Auto cards: equal shares; leftover pixels go one each to the first ones.
		if (autos > 0)
		{
			int autoSpace = space - used;
			int base = autoSpace / autos;
			int extra = autoSpace % autos;
			for (int i = 0; i < n; i++)
			{
				if (!collapsed[i] && fixed[i] <= 0)
				{
					h[i] = base + (extra-- > 0 ? 1 : 0);
				}
			}
		}
		return h;
	}

	/** Snapshot the current heights; layouts over the next 180ms glide from them. */
	void beginTransition(Container parent)
	{
		fromHeights = new IdentityHashMap<>();
		for (Component c : parent.getComponents())
		{
			fromHeights.put(c, c.getHeight());
		}
		transitionStart = System.nanoTime();
		frozen = false;
		if (ticker == null)
		{
			ticker = new Timer(15, e ->
			{
				parent.revalidate();
				parent.repaint();
			});
		}
		ticker.start();
	}

	boolean isTransitioning()
	{
		return fromHeights != null;
	}

	@Override
	public void layoutContainer(Container parent)
	{
		List<SectionCard> cards = cards(parent);
		Insets in = parent.getInsets();
		int width = parent.getWidth() - in.left - in.right;
		int available = parent.getHeight() - in.top - in.bottom - gap * Math.max(0, cards.size() - 1);

		int[] fixed = new int[cards.size()];
		boolean[] collapsed = new boolean[cards.size()];
		for (int i = 0; i < fixed.length; i++)
		{
			fixed[i] = cards.get(i).fixedHeight();
			collapsed[i] = cards.get(i).isCollapsed();
		}
		int[] target = allocate(fixed, collapsed, Theme.HEADER_HEIGHT, available);
		int[] heights = target;

		if (fromHeights != null)
		{
			double p = Math.min(1, (System.nanoTime() - transitionStart) / (double) TRANSITION_NANOS);
			double t = 1 - Math.pow(1 - p, 3);
			heights = new int[target.length];
			for (int i = 0; i < target.length; i++)
			{
				SectionCard card = cards.get(i);
				Integer from = fromHeights.get(card);
				heights[i] = from == null ? target[i] : (int) Math.round(from + (target[i] - from) * t);
				if (!frozen)
				{
					card.freezeContent(Math.max(from == null ? 0 : from, target[i]));
				}
				card.transitionProgress(t);
			}
			frozen = true;
			if (p >= 1)
			{
				fromHeights = null;
				ticker.stop();
				cards.forEach(SectionCard::transitionDone);
			}
		}

		int y = in.top;
		for (int i = 0; i < heights.length; i++)
		{
			cards.get(i).setBounds(in.left, y, width, heights[i]);
			y += heights[i] + gap;
		}

		if (fromHeights == null && target.length > 0
			&& (available != lastAvailable || !Arrays.equals(target, lastAllocation)))
		{
			lastAvailable = available;
			lastAllocation = target;
			log.debug("layout: {}px available -> {}", available, describe(cards, target));
		}
	}

	private static String describe(List<SectionCard> cards, int[] heights)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < heights.length; i++)
		{
			SectionCard c = cards.get(i);
			sb.append(i == 0 ? "" : ", ").append(c.getName()).append(' ').append(heights[i]).append(
				c.isCollapsed() ? " (collapsed)" : c.fixedHeight() > 0 ? " (resized " + c.fixedHeight() + ")" : " (auto)");
		}
		return sb.toString();
	}

	@Override
	public Dimension preferredLayoutSize(Container parent)
	{
		List<SectionCard> cards = cards(parent);
		Insets in = parent.getInsets();
		int height = gap * Math.max(0, cards.size() - 1);
		int width = 0;
		for (SectionCard c : cards)
		{
			Dimension d = c.getPreferredSize();
			height += d.height;
			width = Math.max(width, d.width);
		}
		return new Dimension(width + in.left + in.right, height + in.top + in.bottom);
	}

	@Override
	public Dimension minimumLayoutSize(Container parent)
	{
		return new Dimension(0, 0);
	}

	@Override
	public void addLayoutComponent(String name, Component comp)
	{
	}

	@Override
	public void removeLayoutComponent(Component comp)
	{
	}

	private static List<SectionCard> cards(Container parent)
	{
		List<SectionCard> out = new ArrayList<>();
		for (Component c : parent.getComponents())
		{
			if (c instanceof SectionCard && c.isVisible())
			{
				out.add((SectionCard) c);
			}
		}
		return out;
	}
}
