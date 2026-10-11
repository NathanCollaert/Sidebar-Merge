package com.sidebarmerge;

import static com.sidebarmerge.PartitionLayout.MIN_AUTO;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PartitionLayoutTest
{
	private static final int HEADER = 30;
	private static final int AUTO = -1;

	private static int[] alloc(int available, int[] fixed, boolean... collapsed)
	{
		boolean[] c = collapsed.length == 0 ? new boolean[fixed.length] : collapsed;
		return PartitionLayout.allocate(fixed, c, HEADER, available);
	}

	@Test
	public void autoCardsSplitEqually()
	{
		assertArrayEquals(new int[]{300, 300, 300}, alloc(900, new int[]{AUTO, AUTO, AUTO}));
	}

	@Test
	public void addingACardKeepsResizedHeightsAndEvensOutTheAutos()
	{
		int[] oneResizedTwoAuto = alloc(900, new int[]{400, AUTO, AUTO});
		int[] oneResizedThreeAuto = alloc(900, new int[]{400, AUTO, AUTO, AUTO});
		assertArrayEquals(new int[]{400, 250, 250}, oneResizedTwoAuto);
		assertArrayEquals(new int[]{400, 167, 167, 166}, oneResizedThreeAuto);
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
		assertArrayEquals(new int[]{900 - MIN_AUTO, MIN_AUTO}, alloc(900, new int[]{850, AUTO}));
	}

	@Test
	public void resizedCardsLeavingAutosAboveTheFloorAreUntouched()
	{
		assertArrayEquals(new int[]{800, 100}, alloc(900, new int[]{800, AUTO}));
	}

	@Test
	public void collapsedCardsTakeTheirHeaderOnly()
	{
		assertArrayEquals(new int[]{HEADER, 450, 450}, alloc(930, new int[]{AUTO, AUTO, AUTO}, true, false, false));
	}

	@Test
	public void collapsedResizedCardIgnoresItsSavedHeight()
	{
		assertArrayEquals(new int[]{HEADER, 450, 450}, alloc(930, new int[]{500, AUTO, AUTO}, true, false, false));
	}

	@Test
	public void allCollapsedLeavesTheRestEmpty()
	{
		assertArrayEquals(new int[]{HEADER, HEADER}, alloc(900, new int[]{AUTO, AUTO}, true, true));
	}

	@Test
	public void crowdedTabStillSplitsEqually()
	{
		int cardCount = 27;
		int available = 1300;
		int equalShare = available / cardCount;
		for (int height : alloc(available, new int[cardCount]))
		{
			assertTrue(Math.abs(height - equalShare) <= 1);
		}
	}

	@Test
	public void leftoverPixelsGoToTheFirstAutos()
	{
		assertArrayEquals(new int[]{34, 33, 33}, alloc(100, new int[]{AUTO, AUTO, AUTO}));
	}

	@Test
	public void emptyStack()
	{
		assertArrayEquals(new int[0], alloc(900, new int[0]));
	}
}
