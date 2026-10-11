package com.sidebarmerge;

import com.sidebarmerge.ui.Icons;
import com.sidebarmerge.ui.Theme;
import com.sidebarmerge.ui.Widgets;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;

/**
 * The merged tab. While it is on screen it borrows the picked plugins' panels and stacks them
 * as cards; the moment it is not, every panel goes back to its own tab.
 */
@Slf4j
class MergedPanel extends PluginPanel implements SectionCard.Listener
{
	private static final String VIEW_STACK = "stack";
	private static final String VIEW_EMPTY = "empty";
	private static final String VIEW_PICKER = "picker";
	private static final String ACTIONS_VIEW = "view";
	private static final String ACTIONS_EDIT = "edit";

	private final MergeStore store;

	private final CardLayout views = new CardLayout();
	private final JPanel body = new JPanel(views);
	private final PartitionLayout stackLayout = new PartitionLayout(Theme.GAP);
	private final Stack stack = new Stack();
	private final PickerView picker = new PickerView();
	private final JLabel emptyTitle = new JLabel("", SwingConstants.CENTER);
	private final JLabel emptyBody = new JLabel("", SwingConstants.CENTER);

	private static final String LEFT_SWITCHER = "switcher";
	private static final String LEFT_TITLE = "title";
	private static final String MODE_NORMAL = "normal";
	private static final String MODE_NAMING = "naming";
	private static final String MODE_CONFIRM = "confirm";

	private enum Naming
	{
		COPY, EMPTY, RENAME
	}

	private final JLabel title = new JLabel("Choose panels", Icons.logo(1), SwingConstants.LEFT);
	private final JButton switcher = Widgets.dropdownButton(Icons.logo(1), 150, this::showPresetMenu);
	private final JLabel count = Widgets.countPill();
	private final JButton foldButton = Widgets.iconButton(Icons.collapseAll(), "Collapse all", this::toggleAll);
	private final CardLayout actions = new CardLayout();
	private final JPanel actionsPanel = Widgets.cardPanel(actions);
	private final CardLayout leftCards = new CardLayout();
	private final JPanel leftPanel = new JPanel(leftCards);
	private final ToolbarModes toolbarModes = new ToolbarModes();
	private final JTextField nameField = new JTextField();
	private final JButton saveName = Widgets.primaryButton("Save", this::commitNaming);
	private final JLabel confirmLabel = new JLabel();
	private Naming naming;
	private JPopupMenu presetMenu;
	/** Which card each CardLayout shows, so updateChrome only switches when it changes. */
	private String shownView;
	private String shownActions;
	private String shownLeft;

	private final List<SectionCard> cards = new ArrayList<>();
	private JTabbedPane sidebar;
	private boolean active;
	private boolean editing;
	/** Set while we rearrange tab contents ourselves, so our own tab listener ignores it. */
	private boolean mutating;

	private final ChangeListener selectionListener = e -> refresh();
	private final ContainerListener tabListener = new ContainerAdapter()
	{
		@Override
		public void componentRemoved(ContainerEvent e)
		{
			onTabRemoved(e.getChild());
		}

		@Override
		public void componentAdded(ContainerEvent e)
		{
			// A plugin (re)started; ClientUI is mid-insert, so look once it is done.
			if (!mutating && active)
			{
				SwingUtilities.invokeLater(MergedPanel.this::sync);
			}
		}
	};

	MergedPanel(MergeStore store)
	{
		super(false);
		this.store = store;

		setLayout(new BorderLayout());
		setBorder(null);
		setBackground(Theme.CANVAS);

		body.setBackground(Theme.CANVAS);
		body.add(stack, VIEW_STACK);
		body.add(emptyState(), VIEW_EMPTY);
		body.add(picker, VIEW_PICKER);

		add(toolbar(), BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
		updateChrome();
	}

	@Override
	public void onActivate()
	{
		refresh();
	}

	@Override
	public void onDeactivate()
	{
		if (active && isOnScreen())
		{
			// RuneLite quirk: removing a tab above ours shifts the selected index, and ClientUI
			// maps it before dropping the removed button, so it deactivates the selected tab.
			log.info("[SM] ignoring deactivate: merged tab is still selected (a tab above it was removed)");
		}
		refresh();
	}

	/** Called from the plugin's shutDown (on the EDT): hand everything back and let go. */
	void release()
	{
		active = false;
		giveBackAll();
		if (sidebar != null)
		{
			sidebar.removeChangeListener(selectionListener);
			sidebar.removeContainerListener(tabListener);
			sidebar = null;
		}
	}

	/** Save a new selection and re-sync. The picker, reordering and the self-test use this. */
	void applySelection(List<String> keys)
	{
		log.info("[SM] selection: {}", keys);
		store.saveSelection(keys);
		// A panel taken out and later re-added starts fresh: expanded and auto-sized.
		Set<String> collapsed = new LinkedHashSet<>(store.collapsed());
		if (collapsed.retainAll(keys))
		{
			store.saveCollapsed(collapsed);
		}
		Map<String, Integer> heights = new LinkedHashMap<>(store.heights());
		if (heights.keySet().retainAll(keys))
		{
			store.saveHeights(heights);
		}
		if (active)
		{
			sync();
		}
		else
		{
			updateChrome();
		}
	}

	List<SectionCard> cards()
	{
		return Collections.unmodifiableList(new ArrayList<>(cards));
	}

	List<BorrowedPanel> borrowedPanels()
	{
		return cards.stream().map(c -> c.borrowed).collect(Collectors.toList());
	}

	JTabbedPane sidebar()
	{
		return sidebar;
	}

	boolean isTransitioning()
	{
		return stackLayout.isTransitioning();
	}

	// ---- SectionCard.Listener ----

	@Override
	public void toggle(SectionCard card)
	{
		setCollapsed(Collections.singletonList(card), !card.isCollapsed());
	}

	@Override
	public void openOwnTab(SectionCard card)
	{
		int index = sidebar == null ? -1 : sidebar.indexOfComponent(card.borrowed.tabComponent());
		if (index >= 0)
		{
			log.info("[SM] opening {} on its own", card.borrowed.tab.tooltip);
			// Our selection listener hands every panel back before ClientUI activates that tab.
			sidebar.setSelectedIndex(index);
		}
	}

	@Override
	public void dragMoved(SectionCard card, int stackY)
	{
		stack.dropY = dropLineY(card, dropIndex(card, stackY));
		stack.repaint();
	}

	@Override
	public void dragEnded(SectionCard card, int stackY)
	{
		stack.dropY = -1;
		stack.repaint();
		moveCard(cards.indexOf(card), dropIndex(card, stackY));
	}

	/** Move a card; {@code to} is its index once removed from its old spot. Unloaded panels keep their place. */
	void moveCard(int from, int to)
	{
		if (from < 0 || from >= cards.size() || to < 0 || to >= cards.size() || from == to)
		{
			return;
		}
		List<String> order = cards.stream().map(c -> c.borrowed.tab.key()).collect(Collectors.toList());
		String moved = order.remove(from);
		order.add(to, moved);

		List<String> selection = new ArrayList<>(store.selection());
		Set<String> loaded = new HashSet<>(order);
		Iterator<String> next = order.iterator();
		for (int i = 0; i < selection.size(); i++)
		{
			if (loaded.contains(selection.get(i)))
			{
				selection.set(i, next.next());
			}
		}
		log.info("[SM] moved {} to position {}", cards.get(from).borrowed.tab.tooltip, to + 1);
		applySelection(selection);
	}

	// ---- on screen / off screen ----

	/**
	 * Whether we are on screen comes from the sidebar itself rather than from ClientUI's
	 * activate/deactivate calls alone (see onDeactivate). Our ChangeListener was added after
	 * ClientUI's, so Swing calls it first: a clicked tab gets its panel back before ClientUI
	 * activates it.
	 */
	private void refresh()
	{
		attach();
		boolean onScreen = isOnScreen();
		if (onScreen && !active)
		{
			active = true;
			log.info("[SM] merged tab shown");
			sync();
		}
		else if (!onScreen && active)
		{
			active = false;
			log.info("[SM] merged tab hidden");
			giveBackAll();
		}
	}

	private boolean isOnScreen()
	{
		return sidebar != null && sidebar.isVisible() && sidebar.getSelectedComponent() == this;
	}

	private void attach()
	{
		if (sidebar != null)
		{
			return;
		}
		sidebar = SidebarTab.findSidebar(this);
		if (sidebar != null)
		{
			sidebar.addChangeListener(selectionListener);
			sidebar.addContainerListener(tabListener);
		}
	}

	// ---- borrowing ----

	/** Make the cards match the saved selection and the tabs that exist right now. */
	private void sync()
	{
		if (!active || sidebar == null)
		{
			updateChrome();
			return;
		}

		List<String> wanted = store.selection();
		Set<String> collapsed = store.collapsed();
		Map<String, Integer> heights = store.heights();
		Map<String, SidebarTab> tabs = new HashMap<>();
		for (SidebarTab t : SidebarTab.list(sidebar, this, borrowedByTabComponent()))
		{
			tabs.putIfAbsent(t.key(), t);
		}

		List<SectionCard> kept = new ArrayList<>();
		List<SectionCard> fresh = new ArrayList<>();
		mutating = true;
		try
		{
			for (SectionCard c : cards)
			{
				SidebarTab now = tabs.get(c.borrowed.tab.key());
				if (wanted.contains(c.borrowed.tab.key()) && now != null && now.panel == c.borrowed.tab.panel)
				{
					kept.add(c);
				}
				else
				{
					long t = System.nanoTime();
					c.borrowed.deactivate();
					slow(c.borrowed.tab.tooltip + " onDeactivate", t);
					t = System.nanoTime();
					c.borrowed.giveBack(sidebar);
					slow("giving back " + c.borrowed.tab.tooltip, t);
					log.info("[SM] gave back {}", c.borrowed.tab.tooltip);
				}
			}

			cards.clear();
			for (String key : wanted)
			{
				SectionCard card = null;
				for (SectionCard k : kept)
				{
					if (k.borrowed.tab.key().equals(key))
					{
						card = k;
						break;
					}
				}
				if (card == null)
				{
					SidebarTab t = tabs.get(key);
					long start = System.nanoTime();
					BorrowedPanel b = t == null ? null : BorrowedPanel.borrow(sidebar, t);
					if (b == null)
					{
						continue;
					}
					card = new SectionCard(b, collapsed.contains(key), heights.getOrDefault(key, -1), this);
					slow("borrowing " + t.tooltip, start);
					fresh.add(card);
					log.info("[SM] borrowed {} ({}{}{})", t.tooltip, t.wrapped ? "wrapped" : "unwrapped",
						card.isCollapsed() ? ", collapsed" : "", card.fixedHeight() > 0 ? ", resized " + card.fixedHeight() : "");
				}
				cards.add(card);
			}
		}
		finally
		{
			mutating = false;
		}

		long t = System.nanoTime();
		rebuildStack();
		slow("rebuilding the stack", t);
		for (SectionCard c : fresh)
		{
			if (!c.isCollapsed())
			{
				t = System.nanoTime();
				c.borrowed.activate();
				slow(c.borrowed.tab.tooltip + " onActivate", t);
			}
		}
	}

	private void giveBackAll()
	{
		if (!cards.isEmpty())
		{
			mutating = true;
			try
			{
				for (SectionCard c : cards)
				{
					long t = System.nanoTime();
					c.borrowed.deactivate();
					slow(c.borrowed.tab.tooltip + " onDeactivate", t);
					t = System.nanoTime();
					c.borrowed.giveBack(sidebar);
					slow("giving back " + c.borrowed.tab.tooltip, t);
				}
				log.info("[SM] gave back {} panel(s)", cards.size());
				cards.clear();
			}
			finally
			{
				mutating = false;
			}
		}
		rebuildStack();
	}

	private void onTabRemoved(Component child)
	{
		if (mutating)
		{
			return;
		}
		if (child == this)
		{
			release();
			return;
		}
		boolean changed = false;
		for (Iterator<SectionCard> it = cards.iterator(); it.hasNext(); )
		{
			SectionCard c = it.next();
			if (c.borrowed.tabComponent() == child)
			{
				c.borrowed.tabRemoved();
				it.remove();
				changed = true;
				log.info("[SM] {} left the sidebar while merged; dropped it", c.borrowed.tab.tooltip);
			}
		}
		if (changed)
		{
			rebuildStack();
		}
	}

	private Map<Component, SidebarTab> borrowedByTabComponent()
	{
		Map<Component, SidebarTab> map = new IdentityHashMap<>();
		for (SectionCard c : cards)
		{
			map.put(c.borrowed.tabComponent(), c.borrowed.tab);
		}
		return map;
	}

	// ---- collapse ----

	private void toggleAll()
	{
		boolean collapse = cards.stream().anyMatch(c -> !c.isCollapsed());
		setCollapsed(cards.stream().filter(c -> c.isCollapsed() != collapse).collect(Collectors.toList()), collapse);
	}

	private void setCollapsed(List<SectionCard> targets, boolean collapse)
	{
		if (targets.isEmpty())
		{
			return;
		}
		stackLayout.beginTransition(stack);
		for (SectionCard c : targets)
		{
			c.setCollapsed(collapse);
			// A collapsed panel is off screen, just like an unselected tab.
			if (collapse)
			{
				c.borrowed.deactivate();
			}
			else if (active)
			{
				c.borrowed.activate();
			}
			log.info("[SM] {} {}", collapse ? "collapsed" : "expanded", c.borrowed.tab.tooltip);
		}

		Set<String> keys = new LinkedHashSet<>(store.collapsed());
		for (SectionCard c : cards)
		{
			if (c.isCollapsed())
			{
				keys.add(c.borrowed.tab.key());
			}
			else
			{
				keys.remove(c.borrowed.tab.key());
			}
		}
		store.saveCollapsed(keys);

		updateChrome();
		stack.revalidate();
		stack.repaint();
	}

	// ---- reordering ----

	/** Where a dragged card would land: its index in the list without it. */
	private int dropIndex(SectionCard dragged, int y)
	{
		int index = 0;
		for (SectionCard c : cards)
		{
			if (c != dragged && y > c.getY() + c.getHeight() / 2)
			{
				index++;
			}
		}
		return index;
	}

	private int dropLineY(SectionCard dragged, int to)
	{
		List<SectionCard> others = new ArrayList<>(cards);
		others.remove(dragged);
		if (others.isEmpty())
		{
			return -1;
		}
		if (to == 0)
		{
			return others.get(0).getY() - Theme.GAP / 2;
		}
		SectionCard above = others.get(to - 1);
		return above.getY() + above.getHeight() + Theme.GAP / 2;
	}

	// ---- chrome ----

	private void startEditing()
	{
		attach();
		if (sidebar == null)
		{
			return;
		}
		picker.load(SidebarTab.list(sidebar, this, borrowedByTabComponent()), store.selection());
		editing = true;
		updateChrome();
		picker.focusSearch();
	}

	private void finishEditing()
	{
		editing = false;
		applySelection(picker.selection());
	}

	/** Log a step that took long enough to feel (40ms+), naming it, so freezes have a culprit. */
	private static void slow(String what, long startNanos)
	{
		long ms = (System.nanoTime() - startNanos) / 1_000_000;
		if (ms >= 40)
		{
			log.info("[SM] slow: {} took {} ms", what, ms);
		}
	}

	private void rebuildStack()
	{
		// Cards that left (their content has already gone home) come out; the rest are reordered
		// in place. removeAll() + add() would rebuild every card's native state, over a second
		// with a heavy panel in the stack.
		for (Component c : stack.getComponents())
		{
			if (c instanceof SectionCard && !cards.contains(c))
			{
				stack.remove(c);
			}
		}
		int grips = stack.rebuildGrips();
		for (int i = 0; i < cards.size(); i++)
		{
			SectionCard c = cards.get(i);
			if (c.getParent() == stack)
			{
				stack.setComponentZOrder(c, grips + i);
			}
			else
			{
				stack.add(c, grips + i);
			}
			c.attachContent();
		}
		updateChrome();
		stack.revalidate();
		stack.repaint();
	}

	private void updateChrome()
	{
		String view;
		if (editing)
		{
			view = VIEW_PICKER;
		}
		else if (!cards.isEmpty())
		{
			view = VIEW_STACK;
		}
		else
		{
			view = VIEW_EMPTY;
			boolean nothingPicked = store.selection().isEmpty();
			boolean presets = store.presetNames().size() > 1;
			emptyTitle.setText(nothingPicked ? "Merge sidebar panels" : "Nothing to show");
			emptyBody.setText("<html><div style='text-align:center;width:160px'>" + (!nothingPicked
				? "None of the panels in this preset are loaded. Turn their plugins on, or choose others."
				: presets
				? "“" + escape(store.activePreset()) + "” has no panels yet. Pick some to show here together."
				: "Show several plugins' panels here together, stacked top to bottom.") + "</div></html>");
		}
		if (!view.equals(shownView))
		{
			shownView = view;
			views.show(body, view);
		}
		String actionsCard = editing ? ACTIONS_EDIT : ACTIONS_VIEW;
		if (!actionsCard.equals(shownActions))
		{
			shownActions = actionsCard;
			actions.show(actionsPanel, actionsCard);
		}
		actionsPanel.revalidate();
		String leftCard = editing ? LEFT_TITLE : LEFT_SWITCHER;
		if (!leftCard.equals(shownLeft))
		{
			shownLeft = leftCard;
			leftCards.show(leftPanel, leftCard);
		}

		switcher.setText(store.activePreset());
		switcher.setToolTipText("Preset: " + store.activePreset() + ". Click to switch, save or rename");
		switcher.revalidate();
		count.setText(String.valueOf(cards.size()));
		count.setVisible(!cards.isEmpty());

		boolean anyExpanded = cards.stream().anyMatch(c -> !c.isCollapsed());
		foldButton.setIcon(anyExpanded || cards.isEmpty() ? Icons.collapseAll() : Icons.expandAll());
		foldButton.setToolTipText(anyExpanded || cards.isEmpty() ? "Collapse all" : "Expand all");
		foldButton.setEnabled(!cards.isEmpty());
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private JComponent toolbar()
	{
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(Theme.CANVAS);
		bar.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Theme.OUTLINE), new EmptyBorder(Theme.SPACE_S, Theme.SPACE_S, Theme.SPACE_S, Theme.SPACE_S)));

		title.setFont(Theme.title());
		title.setForeground(Theme.TEXT);
		title.setIconTextGap(Theme.SPACE_S);
		title.setBorder(new EmptyBorder(0, Theme.SPACE_XS, 0, 0));

		JPanel switcherRow = new JPanel();
		switcherRow.setOpaque(false);
		switcherRow.setLayout(new BoxLayout(switcherRow, BoxLayout.X_AXIS));
		switcherRow.add(switcher);
		switcherRow.add(Box.createHorizontalStrut(Theme.SPACE_XS));
		switcherRow.add(count);

		leftPanel.setOpaque(false);
		leftPanel.add(switcherRow, LEFT_SWITCHER);
		leftPanel.add(title, LEFT_TITLE);

		JPanel viewActions = Widgets.hbox(Theme.SPACE_XS,
			foldButton, Widgets.iconButton(Icons.tune(), "Choose panels", this::startEditing));
		JPanel editActions = Widgets.hbox(Theme.SPACE_XS, Widgets.primaryButton("Done", this::finishEditing));

		actionsPanel.setOpaque(false);
		actionsPanel.add(viewActions, ACTIONS_VIEW);
		actionsPanel.add(editActions, ACTIONS_EDIT);

		JPanel normal = new JPanel(new BorderLayout());
		normal.setOpaque(false);
		// The name takes whatever the actions leave and shortens with an ellipsis; it never overlaps them.
		normal.add(leftPanel, BorderLayout.CENTER);
		normal.add(actionsPanel, BorderLayout.EAST);

		toolbarModes.setOpaque(false);
		toolbarModes.add(normal, MODE_NORMAL);
		toolbarModes.add(namingRow(), MODE_NAMING);
		toolbarModes.add(confirmRow(), MODE_CONFIRM);

		bar.add(toolbarModes, BorderLayout.CENTER);
		return bar;
	}

	// ---- presets ----

	/** The active preset changed (switched, created, deleted, reloaded): show its layout. */
	void presetChanged()
	{
		long start = System.nanoTime();
		if (active && sidebar != null)
		{
			// Cards in both layouts glide to their new size and collapse state; sync() then hands
			// back panels the new preset doesn't have and borrows the ones it adds. Cards on their
			// way out are left alone: expanding one just to hand it back would wake its plugin.
			List<String> wanted = store.selection();
			Set<String> collapsed = store.collapsed();
			Map<String, Integer> heights = store.heights();
			stackLayout.beginTransition(stack);
			for (SectionCard c : cards)
			{
				String key = c.borrowed.tab.key();
				if (!wanted.contains(key))
				{
					continue;
				}
				boolean collapse = collapsed.contains(key);
				if (c.isCollapsed() != collapse)
				{
					c.setCollapsed(collapse);
					if (collapse)
					{
						c.borrowed.deactivate();
					}
					else
					{
						c.borrowed.activate();
					}
				}
				c.setFixedHeight(heights.getOrDefault(key, -1));
			}
			sync();
		}
		updateChrome();
		log.info("[SM] preset: {} ({} ms)", store.activePreset(), (System.nanoTime() - start) / 1_000_000);
	}

	void switchPreset(String name)
	{
		if (!name.equals(store.activePreset()))
		{
			store.switchPreset(name);
			presetChanged();
		}
	}

	JPopupMenu presetMenu()
	{
		return presetMenu;
	}

	JTextField presetNameField()
	{
		return nameField;
	}

	private void showPresetMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		for (String name : store.presetNames())
		{
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(name, name.equals(store.activePreset()));
			item.addActionListener(e ->
			{
				log.info("[SM] preset menu: {} chosen{}", name,
					java.awt.EventQueue.getCurrentEvent() instanceof MouseEvent ? " with the mouse" : "");
				switchPreset(name);
			});
			menu.add(item);
		}
		menu.addSeparator();
		menu.add(menuItem("Save as new preset…", Icons.copy(), () -> startNaming(Naming.COPY)));
		menu.add(menuItem("New empty preset…", Icons.plus(), () -> startNaming(Naming.EMPTY)));
		menu.add(menuItem("Rename preset…", Icons.pencil(), () -> startNaming(Naming.RENAME)));
		JMenuItem delete = menuItem("Delete preset…", Icons.trash(), this::confirmDelete);
		delete.setEnabled(store.presetNames().size() > 1);
		menu.add(delete);
		presetMenu = menu;
		menu.show(switcher, 0, switcher.getHeight() + 2);
	}

	private static JMenuItem menuItem(String text, Icon icon, Runnable action)
	{
		JMenuItem item = new JMenuItem(text, icon);
		item.setIconTextGap(Theme.SPACE_S);
		item.addActionListener(e -> action.run());
		return item;
	}

	private JComponent namingRow()
	{
		nameField.setFont(Theme.small());
		nameField.setBackground(Theme.SURFACE);
		nameField.setForeground(Theme.TEXT);
		nameField.setCaretColor(Theme.TEXT);
		nameField.setPreferredSize(new Dimension(60, Theme.CONTROL));
		nameField.putClientProperty("JTextField.placeholderText", "Preset name");
		nameField.addActionListener(e -> commitNaming());
		nameField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				validateName();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				validateName();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				validateName();
			}
		});
		nameField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
		nameField.getActionMap().put("cancel", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				endToolbarMode();
			}
		});

		JPanel buttons = Widgets.hbox(Theme.SPACE_XS, Widgets.textButton("Cancel", this::endToolbarMode), saveName);

		JPanel row = new JPanel(new BorderLayout(Theme.SPACE_S, 0));
		row.setOpaque(false);
		row.add(nameField, BorderLayout.CENTER);
		row.add(buttons, BorderLayout.EAST);
		return row;
	}

	private JComponent confirmRow()
	{
		confirmLabel.setFont(Theme.small());
		confirmLabel.setForeground(Theme.TEXT);

		JPanel buttons = Widgets.hbox(Theme.SPACE_XS,
			Widgets.textButton("Cancel", this::endToolbarMode), Widgets.dangerButton("Delete", this::commitDelete));

		JPanel row = new JPanel(new BorderLayout(Theme.SPACE_S, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(0, Theme.SPACE_XS, 0, 0));
		row.add(confirmLabel, BorderLayout.CENTER);
		row.add(buttons, BorderLayout.EAST);
		return row;
	}

	private void startNaming(Naming mode)
	{
		naming = mode;
		nameField.setText(mode == Naming.RENAME ? store.activePreset() : suggestName());
		validateName();
		toolbarModes.showMode(MODE_NAMING);
		// After the popup has closed, or it takes the focus back.
		SwingUtilities.invokeLater(() ->
		{
			nameField.selectAll();
			nameField.requestFocusInWindow();
		});
	}

	private void validateName()
	{
		if (naming == null)
		{
			return;
		}
		String problem = store.presetNameProblem(nameField.getText(), naming == Naming.RENAME);
		saveName.setEnabled(problem == null);
		boolean blank = nameField.getText().trim().isEmpty();
		nameField.putClientProperty("JComponent.outline", problem != null && !blank ? "error" : null);
		nameField.setToolTipText(problem);
	}

	private void commitNaming()
	{
		if (naming == null || store.presetNameProblem(nameField.getText(), naming == Naming.RENAME) != null)
		{
			return;
		}
		String name = nameField.getText().trim();
		switch (naming)
		{
			case COPY:
				store.createPreset(name, true);
				break;
			case EMPTY:
				store.createPreset(name, false);
				break;
			case RENAME:
				store.renameActivePreset(name);
				break;
		}
		log.info("[SM] {} preset {}", naming == Naming.RENAME ? "renamed to" : "created", name);
		endToolbarMode();
		presetChanged();
	}

	private void confirmDelete()
	{
		confirmLabel.setText("Delete “" + store.activePreset() + "”?");
		toolbarModes.showMode(MODE_CONFIRM);
	}

	private void commitDelete()
	{
		String gone = store.activePreset();
		store.deleteActivePreset();
		log.info("[SM] deleted preset {}", gone);
		endToolbarMode();
		presetChanged();
	}

	private void endToolbarMode()
	{
		naming = null;
		toolbarModes.showMode(MODE_NORMAL);
	}

	/** "Preset 2", "Preset 3", ...: the first one not taken. */
	private String suggestName()
	{
		for (int i = store.presetNames().size() + 1; ; i++)
		{
			String name = "Preset " + i;
			if (store.presetNameProblem(name, false) == null)
			{
				return name;
			}
		}
	}

	/** The toolbar's three faces: normal, naming a preset, confirming a delete. */
	private static final class ToolbarModes extends JPanel
	{
		private final CardLayout cards = new CardLayout();

		ToolbarModes()
		{
			setLayout(cards);
		}

		void showMode(String mode)
		{
			cards.show(this, mode);
		}
	}

	private JComponent emptyState()
	{
		JPanel column = new JPanel();
		column.setOpaque(false);
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

		JLabel logo = new JLabel(Icons.logo(3));
		emptyTitle.setFont(Theme.title());
		emptyTitle.setForeground(Theme.TEXT);
		emptyBody.setFont(Theme.small());
		emptyBody.setForeground(Theme.TEXT_MUTED);

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		buttonRow.setOpaque(false);
		buttonRow.add(Widgets.primaryButton("Choose panels", this::startEditing));

		for (JComponent c : new JComponent[]{logo, emptyTitle, emptyBody, buttonRow})
		{
			c.setAlignmentX(Component.CENTER_ALIGNMENT);
		}
		column.add(logo);
		column.add(Box.createVerticalStrut(Theme.SPACE_L));
		column.add(emptyTitle);
		column.add(Box.createVerticalStrut(Theme.SPACE_S));
		column.add(emptyBody);
		column.add(Box.createVerticalStrut(Theme.SPACE_XL));
		column.add(buttonRow);

		JPanel empty = new JPanel(new GridBagLayout());
		empty.setBackground(Theme.CANVAS);
		GridBagConstraints gc = new GridBagConstraints();
		gc.anchor = GridBagConstraints.NORTH;
		gc.weighty = 1;
		gc.insets = new Insets(64, Theme.SPACE_L, 0, Theme.SPACE_L);
		empty.add(column, gc);
		return empty;
	}

	private void saveHeights()
	{
		Map<String, Integer> heights = new LinkedHashMap<>(store.heights());
		for (SectionCard c : cards)
		{
			if (c.fixedHeight() > 0)
			{
				heights.put(c.borrowed.tab.key(), c.fixedHeight());
			}
			else
			{
				heights.remove(c.borrowed.tab.key());
			}
		}
		store.saveHeights(heights);
	}

	/**
	 * The card stack. The gap between two cards is a border: drag it to move height between the
	 * expanded cards on either side (both then keep their size), double-click it to hand those
	 * two back to the equal split. Also draws where a dragged card will land.
	 */
	private final class Stack extends JPanel
	{
		private static final int MIN_CARD = Theme.HEADER_HEIGHT + 40;
		private static final int DRAG_THRESHOLD = 3;

		private int dropY = -1;
		private int hoverGap = -1;
		private int pressGap = -1;
		private int pressY;
		private boolean resizing;
		private SectionCard above;
		private SectionCard below;
		private int aboveStart;
		private int belowStart;

		Stack()
		{
			super(stackLayout);
			setBackground(Theme.CANVAS);
			setBorder(new EmptyBorder(Theme.GAP, Theme.SIDE, Theme.GAP, Theme.SIDE));

			MouseAdapter border = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					setHoverGap(gapAt(e.getY()));
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					if (pressGap < 0)
					{
						setHoverGap(-1);
					}
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					int gap = gapAt(e.getY());
					if (gap < 0 || !SwingUtilities.isLeftMouseButton(e) || stackLayout.isTransitioning())
					{
						return;
					}
					SectionCard[] pair = neighbours(gap);
					pressGap = gap;
					pressY = e.getY();
					above = pair[0];
					below = pair[1];
					aboveStart = above.getHeight();
					belowStart = below.getHeight();
					resizing = false;
				}

				@Override
				public void mouseDragged(MouseEvent e)
				{
					if (pressGap < 0)
					{
						return;
					}
					int dy = e.getY() - pressY;
					if (!resizing)
					{
						// A click that doesn't move must not turn auto cards into resized ones.
						if (Math.abs(dy) < DRAG_THRESHOLD)
						{
							return;
						}
						resizing = true;
						bakeResizedHeights();
					}
					dy = Math.max(Math.min(0, MIN_CARD - aboveStart), Math.min(Math.max(0, belowStart - MIN_CARD), dy));
					above.setFixedHeight(aboveStart + dy);
					below.setFixedHeight(belowStart - dy);
					revalidate();
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (resizing)
					{
						saveHeights();
						log.info("[SM] resized {} to {}px and {} to {}px",
							above.getName(), above.fixedHeight(), below.getName(), below.fixedHeight());
					}
					pressGap = -1;
					resizing = false;
					above = null;
					below = null;
					setHoverGap(gapAt(e.getY()));
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					int gap = gapAt(e.getY());
					if (e.getClickCount() == 2 && gap >= 0 && SwingUtilities.isLeftMouseButton(e))
					{
						resetGap(gap);
					}
				}
			};
			addMouseListener(border);
			addMouseMotionListener(border);
		}

		/** One grab strip per gap, on top of the cards so it can reach into their edges. Returns how many. */
		int rebuildGrips()
		{
			for (Component c : getComponents())
			{
				if (c instanceof Grip)
				{
					remove(c);
				}
			}
			int count = Math.max(0, cards.size() - 1);
			for (int i = 0; i < count; i++)
			{
				add(new Grip(), 0);
			}
			return count;
		}

		@Override
		public void doLayout()
		{
			super.doLayout();
			int gap = 0;
			for (Component c : getComponents())
			{
				if (c instanceof Grip && gap + 1 < cards.size())
				{
					SectionCard a = cards.get(gap);
					SectionCard b = cards.get(gap + 1);
					int top = a.getY() + a.getHeight() - Theme.GRAB;
					c.setBounds(0, top, getWidth(), b.getY() + Theme.GRAB - top);
					gap++;
				}
			}
		}

		/** Index i of the gap between cards i and i+1 under y, if it can resize anything; else -1. */
		private int gapAt(int y)
		{
			for (int i = 0; i + 1 < cards.size(); i++)
			{
				SectionCard a = cards.get(i);
				SectionCard b = cards.get(i + 1);
				if (y >= a.getY() + a.getHeight() - 1 - Theme.GRAB && y <= b.getY() + 1 + Theme.GRAB)
				{
					return neighbours(i) != null ? i : -1;
				}
			}
			return -1;
		}

		/** The nearest expanded card above the gap and below it; collapsed cards in between stay put. */
		private SectionCard[] neighbours(int gap)
		{
			SectionCard up = null;
			SectionCard down = null;
			for (int i = gap; i >= 0 && up == null; i--)
			{
				up = cards.get(i).isCollapsed() ? null : cards.get(i);
			}
			for (int i = gap + 1; i < cards.size() && down == null; i++)
			{
				down = cards.get(i).isCollapsed() ? null : cards.get(i);
			}
			return up == null || down == null ? null : new SectionCard[]{up, down};
		}

		/**
		 * Resized cards may be showing squeezed or stretched (see PartitionLayout). Save what is
		 * on screen as their height, so the border doesn't jump when the drag starts.
		 */
		private void bakeResizedHeights()
		{
			for (SectionCard c : cards)
			{
				if (!c.isCollapsed() && c.fixedHeight() > 0)
				{
					c.setFixedHeight(c.getHeight());
				}
			}
		}

		private void resetGap(int gap)
		{
			SectionCard[] pair = neighbours(gap);
			stackLayout.beginTransition(this);
			pair[0].setFixedHeight(-1);
			pair[1].setFixedHeight(-1);
			saveHeights();
			log.info("[SM] reset {} and {} to auto height", pair[0].getName(), pair[1].getName());
			revalidate();
			repaint();
		}

		private void setHoverGap(int gap)
		{
			if (hoverGap != gap)
			{
				hoverGap = gap;
				setCursor(Cursor.getPredefinedCursor(gap >= 0 ? Cursor.N_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
				setToolTipText(gap >= 0 ? "Drag to resize, double-click to reset" : null);
				repaint();
			}
		}

		@Override
		public void paint(Graphics g)
		{
			super.paint(g);
			Graphics2D g2 = Theme.smooth(g);
			int gap = resizing ? pressGap : hoverGap;
			if (gap >= 0 && gap + 1 < cards.size())
			{
				SectionCard a = cards.get(gap);
				SectionCard b = cards.get(gap + 1);
				double cy = (a.getY() + a.getHeight() + b.getY()) / 2.0;
				g2.setColor(resizing ? Theme.ACCENT : Theme.TEXT_MUTED);
				g2.fill(new RoundRectangle2D.Double(getWidth() / 2.0 - 16, cy - 1.5, 32, 3, 3, 3));
			}
			if (dropY >= 0)
			{
				g2.setColor(Theme.ACCENT);
				g2.fill(new RoundRectangle2D.Double(Theme.SIDE, dropY - 1.5, getWidth() - 2 * Theme.SIDE, 3, 3, 3));
			}
			g2.dispose();
		}

		@Override
		public Dimension getMinimumSize()
		{
			return new Dimension(0, 0);
		}
	}

	/**
	 * Invisible strip over a gap between cards, reaching {@link Theme#GRAB} into each card so a
	 * 4px gap is still easy to grab. It hands its mouse events to the stack, which does the resizing.
	 */
	private final class Grip extends JComponent
	{
		Grip()
		{
			setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
			MouseAdapter forward = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					pass(e);
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					pass(e);
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					pass(e);
				}

				@Override
				public void mouseMoved(MouseEvent e)
				{
					pass(e);
				}

				@Override
				public void mouseDragged(MouseEvent e)
				{
					pass(e);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					pass(e);
				}
			};
			addMouseListener(forward);
			addMouseMotionListener(forward);
		}

		private void pass(MouseEvent e)
		{
			stack.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, stack));
		}
	}
}
