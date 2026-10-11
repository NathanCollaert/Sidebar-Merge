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

## How it works

RuneLite's sidebar is a Swing `JTabbedPane`, and each tab holds a plugin's
`PluginPanel.getWrappedPanel()`. A component can only have one parent, so the merged tab
*borrows* the picked panels while it's on screen and hands them back the moment it isn't.

- **Tabs are never added or removed.** RuneLite maps tab index to its own sorted list of
  navigation buttons, so only what sits *inside* a tab changes.
- **Wrapped panels** (the default `PluginPanel()`): only the plugin's own `JScrollPane` moves;
  the tab keeps RuneLite's empty wrapper. The move uses `setComponentZOrder`, which re-parents
  without rebuilding the panel's native state. A plain `add()` does that rebuild, and it takes
  over a second for a large panel such as World Switcher.
- **Unwrapped panels** (`PluginPanel(false)`): the tab component is the panel itself, so a
  `TabPlaceholder` is put in the tab first. Its `equals()` matches the panel it replaced, so
  RuneLite still finds and removes the right tab if that plugin is turned off.
- Whether the merged tab is on screen is read from the sidebar's selection and visibility, not
  only from `onActivate`/`onDeactivate`: RuneLite deactivates the selected tab when a tab above
  it is removed, even though it stays selected.

The plugin uses no reflection.

## Development

```
gradlew test             # unit tests: layout, presets, picker rendering
gradlew dev              # dev client with the plugin and a UI-freeze watchdog
gradlew dev -Pselftest   # also runs a scripted in-client self-test (snapshots in build/selftest/)
```

Everything under `src/test` is development tooling and is not part of the plugin. The
self-test reads RuneLite's private sidebar bookkeeping by reflection, to prove the plugin never
puts it out of step.
