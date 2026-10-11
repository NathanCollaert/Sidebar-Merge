package com.sidebarmerge;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

public class PartitionLayoutTest
{
	private static final int H = 30;
	private static final int A = -1;

	private static int[] alloc(int available, int[] fixed, boolean... collapsed)
	{
		boolean[] c = collapsed.length == 0 ? new boolean[fixed.length] : collapsed;
		return PartitionLayout.allocate(fixed, c, H, available);
	}

	@Test
	public void autoCardsSplitEqually()
	{
		assertArrayEquals(new int[]{300, 300, 300}, alloc(900, new int[]{A, A, A}));
	}

	@Test
	public void addingACardKeepsResizedHeightsAndEvensOutTheAutos()
	{
		// Before: one card resized to 400, two autos share the rest.
		assertArrayEquals(new int[]{400, 250, 250}, alloc(900, new int[]{400, A, A}));
		// A new (auto) card arrives: 400 stays, the three autos split 500.
		assertArrayEquals(new int[]{400, 167, 167, 166}, alloc(900, new int[]{400, A, A, A}));
	}

	@Test
	public void resizedCardsScaleToFillWhenNoneAreAuto()
	{
		assertArrayEquals(new int[]{450, 450}, alloc(900, new int[]{300, 300}));
		assertArrayEquals(new int[]{300, 600}, alloc(900, new int[]{100, 200}));
	}

	@Test
	public void resizedCardsAreSqueezedSoAutosKeepAFloor()
	{
		// 850 would leave the auto 50px; the floor is 80, so the resized card shows at 820.
		assertArrayEquals(new int[]{820, 80}, alloc(900, new int[]{850, A}));
		// 800 leaves 100, above the floor: untouched.
		assertArrayEquals(new int[]{800, 100}, alloc(900, new int[]{800, A}));
	}

	@Test
	public void collapsedCardsTakeTheirHeaderOnly()
	{
		assertArrayEquals(new int[]{30, 450, 450}, alloc(930, new int[]{A, A, A}, true, false, false));
		// A collapsed resized card ignores its saved height until it is expanded again.
		assertArrayEquals(new int[]{30, 450, 450}, alloc(930, new int[]{500, A, A}, true, false, false));
	}

	@Test
	public void allCollapsedLeavesTheRestEmpty()
	{
		assertArrayEquals(new int[]{30, 30}, alloc(900, new int[]{A, A}, true, true));
	}

	@Test
	public void crowdedTabStillSplitsEqually()
	{
		int[] h = alloc(1300, new int[27]);
		for (int i = 0; i < 27; i++)
		{
			h[i] = Math.abs(h[i] - 48);
		}
		// 1300 / 27 = 48 rem 4: every card is 48 or 49.
		for (int d : h)
		{
			org.junit.Assert.assertTrue(d <= 1);
		}
	}

	@Test
	public void leftoverPixelsGoToTheFirstAutos()
	{
		assertArrayEquals(new int[]{34, 33, 33}, alloc(100, new int[]{A, A, A}));
	}

	@Test
	public void emptyStack()
	{
		assertArrayEquals(new int[0], alloc(900, new int[0]));
	}
}
