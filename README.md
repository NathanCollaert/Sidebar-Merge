# Sidebar Merge

Show several plugins' sidebar panels in one tab, stacked top to bottom: your XP tracker, loot
tracker and farming timers on one screen instead of three tabs.

## Getting started

1. Open the **Sidebar Merge** tab: the wooden frame holding two parchment panels.
2. Click **Choose panels**. Search, switch panels on, put them in order with the arrows, and
   click **Done**.

## Cards

Each merged panel is a card.

- **Collapse:** click its header. A collapsed card shrinks to its header and the others take
  the space. The double-chevron button in the toolbar collapses or expands every card.
- **Resize:** drag the gap between two cards to move height between them. Double-click the gap
  to go back to equal heights.
- **Reorder:** drag a card by its header.
- **Full size:** the arrow button on a card opens that panel in its own tab.

Cards always fill the tab. Cards you've resized keep their height when other cards are added,
removed or collapsed, and the rest share the remaining space equally. A panel taller than its
card scrolls inside it.

The plugins' own sidebar icons stay where they are. Clicking one shows that panel on its own;
the merged tab picks it back up when you return.

## Presets

A preset is a whole layout: which panels, their order, which cards are collapsed, and their
heights. Make one for each activity (bossing, skilling, flipping) and switch with one click.

The preset's name sits at the top of the tab. Click it to:

- **switch** to another preset;
- **Save as new preset…**: copy the current layout under a new name;
- **New empty preset…**: start from nothing;
- **Rename preset…** or **Delete preset…** (the last preset can't be deleted).

Changes save as you make them; there is no Save button.

## Good to know

- A collapsed panel is told it's hidden, just like an unselected tab, so plugins that pause
  while hidden still do.
- Panels are recognised by their plugin's panel class. The rare plugin that opens two tabs
  using the same class can only have one of them merged.
