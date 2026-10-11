# Sidebar Merge (POC)

Combine several plugins' sidebar panels into one tab, stacked top to bottom.

## Use

1. Open the **Sidebar Merge** tab (a wooden frame holding two parchment panels).
2. Click **Choose panels** (sliders icon). Search, switch panels on, order them with the
   arrows, then click **Done**.
3. Each panel is a card:
   - **Click its header** to collapse or expand it. A collapsed card shrinks to its header,
     and the height it frees goes to the other cards.
   - **Drag its header** to reorder the cards.
   - **The arrow-out-of-box button** opens that panel full size in its own tab.
   - **The double-chevron button** in the toolbar collapses or expands every card.

Order, collapsed state and dragged heights are saved between sessions.

### Presets

A preset is a whole layout: which panels, their order, which cards are collapsed, and any
dragged heights. The name in the toolbar is the active preset; click it to:

- **switch** to another preset (cards both layouts share glide to their new size);
- **Save as new preset…**: copy the current layout under a new name and switch to it;
- **New empty preset…**: start from nothing;
- **Rename preset…** / **Delete preset…**: both inline in the toolbar (delete asks first; the
  last preset can't be deleted).

There is no Save button: every change goes straight into the active preset. Your layout from
before presets existed becomes the "Default" preset. A collapsed panel is told it is hidden
(`onDeactivate`), the same as an unselected tab, so plugins that pause while hidden still do.

Cards always fill the tab:

- **Collapsed** cards take their header only.
- **Resized** cards (you dragged their border) keep their exact height: when cards are added
  or removed, others collapse, or the window resizes.
- **Auto** cards (all the others, including any newly added card) split what is left into
  exactly equal parts. A card's content scrolls inside it when it is taller than the card.

Drag the gap between two cards to move height between them; both then count as resized.
Double-click the gap to hand those two back to the equal split. If resized cards would leave
the auto cards under 80px each, the resized cards are squeezed on screen; their saved heights
don't change. If every card is resized, they scale together to fill the tab.

The original icons stay in the sidebar. Clicking one shows that panel on its own again, and
the merged tab picks it back up when you return to it.

## How it works

RuneLite's sidebar is a `JTabbedPane`. Each tab holds a plugin's `PluginPanel.getWrappedPanel()`.
A Swing component can only have one parent, so the merged tab *borrows* the panels while it is
on screen and returns them the moment it is not.

- **The one rule:** never add or remove sidebar tabs. ClientUI maps tab index to its own sorted
  set of navigation buttons, so only what sits *inside* a tab may change.
- **Wrapped panels** (`PluginPanel()`, the default): only the plugin's own `JScrollPane` moves.
  The tab keeps RuneLite's empty wrapper, and the plugin keeps its own scrolling.
- **Unwrapped panels** (`PluginPanel(false)`): the tab component is the panel itself, so a
  `TabPlaceholder` is swapped in with `setComponentAt` first. The panel then goes into our own
  scroll pane. The placeholder's `equals()` matches the panel it replaced, so RuneLite's
  `sidebar.remove(panel)` still finds and removes the right tab if that plugin stops.
- Whether the merged tab is on screen comes from the sidebar's selection and visibility, not only
  from `onActivate`/`onDeactivate`. RuneLite deactivates the selected tab when a tab above it is
  removed, even though it stays selected.

Spacing lives in `ui/Theme`. Every gap comes from a 4px scale (4, 8, 12, 16, 24), and
content lines up on one column 12px in (toolbar logo, card chevrons, picker icons). Cards sit
4px apart with a 4px margin around them, by choice, so panels get the height. That makes the
gap a small target, so an invisible 12px grab strip sits over each gap, reaching 4px into the
cards. At a 4px side margin, a plugin panel that draws to its very edge and has a 10px
scrollbar (Hunter Rumour Locations) loses its rightmost pixel. The self-test logs that as a
note and fails on anything worse.

No reflection is used in the plugin. The self-test reads ClientUI's private bookkeeping to prove
it stays in line.

## Dev

```
gradlew test               # fair-share allocation unit tests
gradlew dev                # client with the plugin and a UI-freeze watchdog ([SM-WATCHDOG] in the log)
gradlew dev -Pselftest     # + scripted self-test; snapshots in build/selftest/
gradlew dev -Pdemo=login   # records the scripted demo (after you log in) into build/demo/
python tools/compose_demo.py   # -> demo/sidebar-merge-reddit.mp4 (vertical, captions, end card)
```

## Known gaps (POC)

- Original sidebar icons can't be hidden without reflection.
- Panels are identified by class name. Two tabs that use the same panel class can't be told apart.
- Relies on ClientUI's current `JTabbedPane` structure (checked against client 1.13.1).
