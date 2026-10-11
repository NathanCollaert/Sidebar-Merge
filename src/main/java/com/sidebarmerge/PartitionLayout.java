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
 * Resized cards are squeezed to leave each auto card {@link #MIN_AUTO}, or stretched to fill the
 * tab when no card is auto. That is for display only: their saved heights don't change.
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
	 * @param fixed     each card's saved height, or -1 for an auto card
	 * @param available height to fill, gaps already taken off
	 */
	static int[] allocate(int[] fixed, boolean[] collapsed, int headerHeight, int available)
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
				h[i] = headerHeight;
				space -= headerHeight;
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

		int fixedSpace = spaceForResizedCards(space, autos, fixeds, fixedSum);
		int usedByResized = allocateResizedCards(h, fixed, collapsed, fixedSpace, fixedSum);
		allocateAutoCards(h, fixed, collapsed, space - usedByResized, autos);
		return h;
	}

	private static int spaceForResizedCards(int space, int autos, int fixeds, long fixedSum)
	{
		if (autos == 0)
		{
			return fixeds == 0 ? 0 : space;
		}
		int equalSplit = space / (autos + fixeds);
		int autoFloorThatFits = Math.min(MIN_AUTO, equalSplit);
		return (int) Math.min(fixedSum, Math.max(0, space - (long) autos * autoFloorThatFits));
	}

	/** Rounds cumulatively, so the heights add up to exactly {@code fixedSpace}. Returns their sum. */
	private static int allocateResizedCards(int[] h, int[] fixed, boolean[] collapsed, int fixedSpace, long fixedSum)
	{
		long acc = 0;
		int prevEnd = 0;
		int used = 0;
		for (int i = 0; i < h.length; i++)
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
		return used;
	}

	private static void allocateAutoCards(int[] h, int[] fixed, boolean[] collapsed, int autoSpace, int autos)
	{
		if (autos == 0)
		{
			return;
		}
		int base = autoSpace / autos;
		int extra = autoSpace % autos;
		for (int i = 0; i < h.length; i++)
		{
			if (!collapsed[i] && fixed[i] <= 0)
			{
				h[i] = base + (extra-- > 0 ? 1 : 0);
			}
		}
	}

	/** Call before changing a card's state: the layouts that follow glide from the current heights. */
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
			double t = easeOutCubic(p);
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

	private static double easeOutCubic(double progress)
	{
		return 1 - Math.pow(1 - progress, 3);
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
