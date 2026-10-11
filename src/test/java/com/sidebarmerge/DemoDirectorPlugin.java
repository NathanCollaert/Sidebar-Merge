package com.sidebarmerge;

import com.sidebarmerge.ui.Icons;
import com.sidebarmerge.ui.Widgets;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.inject.Inject;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

/**
 * Dev only. Plays a scripted tour of Sidebar Merge in the live client and records it: a drawn
 * cursor moves and real mouse events go to whatever is under it (so hover, drag lines and the
 * glide all happen for real), and every frame the sidebar is painted at 2x into build/demo/.
 * tools/compose_demo.py turns the frames into the vertical video.
 *
 * Records the whole sidebar (nothing else), works in throwaway presets, and puts back the
 * user's presets and selected tab afterwards.
 */
@Slf4j
@PluginDescriptor(name = "Sidebar Merge Demo Director", description = "Dev only: records the demo video")
public class DemoDirectorPlugin extends Plugin
{
	private static final int SCALE = 2;
	private static final int FPS = 30;
	private static final String DEMO_BOOK = "{\"presets\":[{\"name\":\"Skilling\",\"panels\":[],\"collapsed\":[],\"heights\":{}}],\"active\":\"Skilling\"}";

	@Inject
	private PluginManager pluginManager;

	@Inject
	private net.runelite.api.Client client;

	@Inject
	private net.runelite.client.ui.ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	private SidebarMergePlugin sm;
	private MergedPanel merged;
	private JTabbedPane sidebar;

	// What we put back afterwards.
	private String originalPresets;
	private Component originalTab;
	private int recordWidth;
	private int recordHeight;
	private int skipped;

	// Recording.
	private final File outDir = new File("build/demo");
	private ExecutorService writers = Executors.newFixedThreadPool(3);
	private PrintWriter frameLog;
	private PrintWriter captionLog;
	private Timer captureTimer;
	private long t0;
	private int frameNo;

	// The drawn cursor, in sidebar coordinates.
	private double cx = 150;
	private double cy = 260;
	private double fromX;
	private double fromY;
	private double toX;
	private double toY;
	private long moveStart;
	private int moveMs;
	private boolean moving;
	private boolean buttonDown;
	private Component hover;
	private Component pressTarget;
	private long rippleAt = -1;
	private double rippleX;
	private double rippleY;
	private Timer cursorTimer;

	private final Deque<Step> script = new ArrayDeque<>();
	private Timer stepTimer;

	@Override
	protected void startUp()
	{
		later(1000, () -> waitForUi(0));
	}

	@Override
	protected void shutDown()
	{
		stopTimers();
		writers.shutdownNow();
	}

	private void waitForUi(int attempt)
	{
		sm = (SidebarMergePlugin) pluginManager.getPlugins().stream()
			.filter(p -> p instanceof SidebarMergePlugin).findFirst().orElse(null);
		merged = sm == null ? null : sm.getPanel();
		sidebar = merged == null ? null : SidebarTab.findSidebar(merged);
		if (sidebar == null || !sidebar.isShowing())
		{
			if (attempt < 180)
			{
				later(1000, () -> waitForUi(attempt + 1));
			}
			return;
		}
		if (Boolean.getBoolean("sidebarmerge.demo.waitForLogin"))
		{
			log.info("[DEMO] client up; waiting for you to log in (panels look richer logged in).");
			waitForLogin(0);
		}
		else
		{
			log.info("[DEMO] client up; letting plugins settle for 12s. Hands off the window, please.");
			later(12000, this::setUp);
		}
	}

	/** Logged in, then 10s for the plugins to load the account's data, then record. */
	private void waitForLogin(int seconds)
	{
		if (client.getGameState() == net.runelite.api.GameState.LOGGED_IN)
		{
			log.info("[DEMO] logged in; recording starts in 10s. Hands off the window, please.");
			later(10000, this::setUp);
			return;
		}
		if (seconds % 30 == 0)
		{
			log.info("[DEMO] still waiting for login ({}s)", seconds);
		}
		later(1000, () -> waitForLogin(seconds + 1));
	}

	// ---- setup and teardown ----

	private void setUp()
	{
		originalPresets = configManager.getConfiguration(SidebarMergeConfig.GROUP, SidebarMergeConfig.PRESETS_KEY);
		originalTab = sidebar.getSelectedComponent();

		// Recorded at the client's own size: the full height keeps the icon strip in one column and
		// the cards at their real size. The camera handles the vertical video.
		// A collapsed sidebar is just the 31px icon strip: open it (ClientToolbar.openPanel expands
		// it), then go to the first tab of the intro.
		clientToolbar.openPanel(sm.getNavButton());
		configManager.setConfiguration(SidebarMergeConfig.GROUP, SidebarMergeConfig.PRESETS_KEY, DEMO_BOOK);
		sidebar.setSelectedIndex(tab("Skill Calculator"));
		later(2500, this::startRecording);
	}

	private void tearDown()
	{
		stopTimers();
		frameLog.close();
		captionLog.close();
		writers.shutdown();
		try
		{
			writers.awaitTermination(60, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		if (originalTab != null && sidebar.indexOfComponent(originalTab) >= 0)
		{
			sidebar.setSelectedComponent(originalTab);
		}
		configManager.setConfiguration(SidebarMergeConfig.GROUP, SidebarMergeConfig.PRESETS_KEY, originalPresets);
		configManager.sendConfig();
		log.info("[DEMO] DONE: {} frames in {} ({} dropped at the wrong size). Create build/demo/again for another take.",
			frameNo, outDir.getAbsolutePath(), skipped);
		waitForRetake();
	}

	/** Another take without restarting (and logging in) again: create build/demo/again. */
	private void waitForRetake()
	{
		File trigger = new File(outDir, "again");
		if (trigger.delete())
		{
			log.info("[DEMO] retake requested");
			script.clear();
			moving = false;
			buttonDown = false;
			hover = null;
			pressTarget = null;
			rippleAt = -1;
			setUp();
			return;
		}
		stepTimer = later(1000, this::waitForRetake);
	}

	// ---- the script ----

	private void startRecording()
	{
		if (writers.isShutdown())
		{
			writers = Executors.newFixedThreadPool(3);
		}
		outDir.mkdirs();
		File frames = new File(outDir, "frames");
		frames.mkdirs();
		File[] old = frames.listFiles();
		if (old != null)
		{
			for (File f : old)
			{
				f.delete();
			}
		}
		try
		{
			frameLog = new PrintWriter(new FileOutputStream(new File(outDir, "frames.csv")), true, StandardCharsets.UTF_8);
			captionLog = new PrintWriter(new FileOutputStream(new File(outDir, "captions.csv")), true, StandardCharsets.UTF_8);
			try (PrintWriter meta = new PrintWriter(new File(outDir, "meta.json"), StandardCharsets.UTF_8))
			{
				meta.printf("{\"scale\": %d, \"width\": %d, \"height\": %d, \"fps\": %d}%n",
					SCALE, sidebar.getWidth(), sidebar.getHeight(), FPS);
			}
			ImageIO.write(Icons.logoImage(), "png", new File(outDir, "logo.png"));
		}
		catch (Exception e)
		{
			log.error("[DEMO] can't write to {}", outDir, e);
			return;
		}
		log.info("[DEMO] recording: sidebar {}x{} at {}x", sidebar.getWidth(), sidebar.getHeight(), SCALE);
		recordWidth = sidebar.getWidth();
		recordHeight = sidebar.getHeight();

		skipped = 0;
		frameNo = 0;

		cx = sidebar.getWidth() * 0.55;
		cy = sidebar.getHeight() * 0.55;
		t0 = System.nanoTime();
		captureTimer = new Timer(1000 / FPS, e -> capture());
		captureTimer.start();
		cursorTimer = new Timer(10, e -> tickCursor());
		cursorTimer.start();

		buildScript();
		nextStep();
	}

	private void buildScript()
	{
		caption("Juggling sidebar tabs?");
		pause(500);
		for (String name : new String[]{"Time Tracking", "World Switcher", "Skill Calculator"})
		{
			move(() -> tabCentre(tab(name)), 380);
			click();
			pause(420);
		}

		caption("Sidebar Merge stacks them in one tab");
		move(() -> tabCentre(sidebar.indexOfComponent(merged)), 520);
		click();
		pause(650);
		move(() -> centre(button("Choose panels", true)), 520);
		click();
		pause(450);
		pickPanels(new String[][]{{"skill", "Skill Calculator"}, {"time", "Time Tracking"}, {"world", "World Switcher"}}, 70);
		move(() -> centre(button("Done", false)), 420);
		click();
		pause(1100);

		caption("Collapse what you don't need");
		move(() -> headerPoint(2), 480);
		click();
		pause(650);
		move(() -> headerPoint(2), 380);
		click();
		pause(700);

		caption("Drag the gaps to resize");
		move(() -> gapPoint(0), 480);
		press();
		move(() -> gapPoint(0, 80), 650);
		release();
		pause(250);
		press();
		move(() -> gapPoint(0, -45), 450);
		release();
		pause(600);

		caption("Drag headers to reorder");
		move(() -> headerPoint(2), 420);
		press();
		move(() -> headerTop(0, -2), 950);
		release();
		pause(900);

		caption("Save layouts as presets");
		move(() -> centre(button("Skilling", false)), 480);
		click();
		pause(450);
		move(() -> centre(menuItem("New empty preset…")), 420);
		click();
		pause(300);
		type(text -> merged.presetNameField().setText(text), "Bossing", 85);
		pause(250);
		move(() -> centre(button("Save", false)), 320);
		click();
		pause(500);
		move(() -> centre(button("Choose panels", true)), 420);
		click();
		pause(350);
		pickPanels(new String[][]{{"loot", "Loot Tracker"}, {"setups", "Inventory Setups"}, {"xp", "XP Tracker"}}, 55);
		move(() -> centre(button("Done", false)), 360);
		click();
		pause(1000);

		caption("Switch presets in one click");
		move(() -> centre(button("Bossing", false)), 420);
		click();
		pause(380);
		move(() -> centre(menuItem("Skilling")), 320);
		click();
		pause(1250);
		move(() -> centre(button("Skilling", false)), 360);
		click();
		pause(380);
		move(() -> centre(menuItem("Bossing")), 280);
		click();
		pause(1300);

		run(this::tearDown);
	}

	/** In the picker: type a search, switch on the matching row, clear the search. */
	private void pickPanels(String[][] picks, int msPerChar)
	{
		boolean first = true;
		for (String[] p : picks)
		{
			if (first)
			{
				move(() -> centre(searchField()), 380);
				click();
				first = false;
			}
			type(text -> picker().setFilter(text), p[0], msPerChar);
			pause(220);
			move(() -> centre(rowSwitch(p[1])), 380);
			click();
			pause(260);
			run(() -> picker().setFilter(""));
			pause(120);
		}
	}

	// ---- script steps ----

	private static final class Step
	{
		final Runnable action;
		final int ms;

		Step(Runnable action, int ms)
		{
			this.action = action;
			this.ms = ms;
		}
	}

	private void step(Runnable action, int ms)
	{
		script.add(new Step(action, ms));
	}

	private void pause(int ms)
	{
		step(() ->
		{
		}, ms);
	}

	private void run(Runnable r)
	{
		step(r, 0);
	}

	private void caption(String text)
	{
		step(() -> captionLog.printf("%d\t%s%n", now(), text), 0);
	}

	/** Glide the cursor to a point worked out when the step starts (things move). */
	private void move(Supplier<Point> target, int ms)
	{
		step(() ->
		{
			Point p = target.get();
			fromX = cx;
			fromY = cy;
			toX = p.x;
			toY = p.y;
			moveStart = System.nanoTime();
			moveMs = ms;
			moving = true;
		}, ms + 20);
	}

	private void click()
	{
		step(() ->
		{
			Component target = targetAt(cx, cy);
			ripple();
			send(target, MouseEvent.MOUSE_PRESSED, 1, true);
			send(target, MouseEvent.MOUSE_RELEASED, 1, false);
			send(target, MouseEvent.MOUSE_CLICKED, 1, false);
			// A heavyweight popup's items don't always act on synthetic releases; make sure.
			if (target instanceof JMenuItem && target.isShowing())
			{
				((JMenuItem) target).doClick(0);
			}
		}, 140);
	}

	private void press()
	{
		step(() ->
		{
			pressTarget = targetAt(cx, cy);
			buttonDown = true;
			ripple();
			send(pressTarget, MouseEvent.MOUSE_PRESSED, 1, true);
		}, 80);
	}

	private void release()
	{
		step(() ->
		{
			buttonDown = false;
			send(pressTarget, MouseEvent.MOUSE_RELEASED, 1, false);
			pressTarget = null;
		}, 60);
	}

	private void type(Consumer<String> setter, String text, int msPerChar)
	{
		for (int i = 1; i <= text.length(); i++)
		{
			String prefix = text.substring(0, i);
			step(() -> setter.accept(prefix), msPerChar);
		}
	}

	private void nextStep()
	{
		Step s = script.poll();
		if (s == null)
		{
			return;
		}
		try
		{
			s.action.run();
		}
		catch (RuntimeException e)
		{
			log.error("[DEMO] step failed; stopping and restoring", e);
			script.clear();
			tearDown();
			return;
		}
		if (s.ms <= 0)
		{
			nextStep();
		}
		else
		{
			stepTimer = later(s.ms, this::nextStep);
		}
	}

	// ---- cursor and events ----

	private void tickCursor()
	{
		if (!moving)
		{
			return;
		}
		double p = Math.min(1, (System.nanoTime() - moveStart) / 1e6 / moveMs);
		double t = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
		cx = fromX + (toX - fromX) * t;
		cy = fromY + (toY - fromY) * t;
		if (buttonDown)
		{
			send(pressTarget, MouseEvent.MOUSE_DRAGGED, 0, true);
		}
		else
		{
			Component target = targetAt(cx, cy);
			if (target != hover)
			{
				send(hover, MouseEvent.MOUSE_EXITED, 0, false);
				hover = target;
				send(hover, MouseEvent.MOUSE_ENTERED, 0, false);
			}
			send(hover, MouseEvent.MOUSE_MOVED, 0, false);
		}
		if (p >= 1)
		{
			moving = false;
		}
	}

	/**
	 * What a real pointer at (x, y) would hit: an open preset menu first (it's its own window),
	 * else the deepest sidebar component that listens to the mouse, as AWT retargets events.
	 */
	private Component targetAt(double x, double y)
	{
		Point screen = new Point(sidebar.getLocationOnScreen());
		screen.translate((int) x, (int) y);
		JPopupMenu menu = merged.presetMenu();
		Component deepest = null;
		if (menu != null && menu.isShowing())
		{
			Point m = new Point(screen);
			SwingUtilities.convertPointFromScreen(m, menu);
			if (menu.contains(m))
			{
				deepest = SwingUtilities.getDeepestComponentAt(menu, m.x, m.y);
			}
		}
		if (deepest == null)
		{
			deepest = SwingUtilities.getDeepestComponentAt(sidebar, (int) x, (int) y);
		}
		Component c = deepest;
		while (c != null && c.getMouseListeners().length == 0 && c.getMouseMotionListeners().length == 0)
		{
			c = c.getParent();
		}
		return c != null ? c : sidebar;
	}

	private void send(Component target, int id, int clicks, boolean buttonHeld)
	{
		if (target == null || !target.isShowing())
		{
			return;
		}
		Point screen = new Point(sidebar.getLocationOnScreen());
		screen.translate((int) Math.round(cx), (int) Math.round(cy));
		Point local = new Point(screen);
		SwingUtilities.convertPointFromScreen(local, target);
		boolean button = id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_CLICKED;
		int mods = buttonHeld ? InputEvent.BUTTON1_DOWN_MASK : 0;
		target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), mods, local.x, local.y, screen.x, screen.y,
			clicks, false, button ? MouseEvent.BUTTON1 : MouseEvent.NOBUTTON));
	}

	private void ripple()
	{
		rippleAt = System.nanoTime();
		rippleX = cx;
		rippleY = cy;
	}

	// ---- capture ----

	private void capture()
	{
		int w = sidebar.getWidth();
		int h = sidebar.getHeight();
		if (w != recordWidth || h != recordHeight)
		{
			skipped++;
			return;
		}
		BufferedImage img = new BufferedImage(w * SCALE, h * SCALE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.scale(SCALE, SCALE);

		// Paint straight into the 3x image (no 1x back buffer), so text and shapes are drawn sharp.
		RepaintManager rm = RepaintManager.currentManager(sidebar);
		boolean buffered = rm.isDoubleBufferingEnabled();
		rm.setDoubleBufferingEnabled(false);
		try
		{
			sidebar.paint(g);
			JPopupMenu menu = merged.presetMenu();
			if (menu != null && menu.isShowing())
			{
				Point m = menu.getLocationOnScreen();
				Point s = sidebar.getLocationOnScreen();
				Graphics2D mg = (Graphics2D) g.create();
				mg.translate(m.x - s.x, m.y - s.y);
				menu.paint(mg);
				mg.dispose();
			}
		}
		finally
		{
			rm.setDoubleBufferingEnabled(buffered);
		}
		drawCursor(g);
		g.dispose();

		int n = frameNo++;
		frameLog.printf("%d\t%d%n", n, now());
		writers.submit(() -> writeJpeg(img, new File(outDir, String.format("frames/f%05d.jpg", n))));
	}

	private void drawCursor(Graphics2D g)
	{
		Graphics2D c = (Graphics2D) g.create();
		c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (rippleAt > 0)
		{
			double age = (System.nanoTime() - rippleAt) / 1e6;
			if (age < 420)
			{
				double f = age / 420;
				double r = 8 + 20 * f;
				c.setColor(new Color(255, 152, 31, (int) (230 * (1 - f))));
				c.setStroke(new BasicStroke(3f));
				c.draw(new Ellipse2D.Double(rippleX - r, rippleY - r, r * 2, r * 2));
			}
		}
		c.translate(cx, cy);
		c.scale(1.9, 1.9);
		Polygon arrow = new Polygon(new int[]{0, 0, 4, 7, 9, 6, 11}, new int[]{0, 16, 12, 18, 17, 11, 11}, 7);
		c.setColor(new Color(0, 0, 0, 90));
		c.translate(1, 1);
		c.fill(arrow);
		c.translate(-1, -1);
		c.setColor(Color.WHITE);
		c.fill(arrow);
		c.setColor(Color.BLACK);
		c.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		c.draw(arrow);
		if (buttonDown)
		{
			c.setColor(new Color(255, 152, 31));
			c.setComposite(AlphaComposite.SrcOver);
			c.fill(new Ellipse2D.Double(-3, -3, 6, 6));
		}
		c.dispose();
	}

	private static void writeJpeg(BufferedImage img, File file)
	{
		try (ImageOutputStream out = ImageIO.createImageOutputStream(file))
		{
			ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
			ImageWriteParam p = w.getDefaultWriteParam();
			p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			p.setCompressionQuality(0.95f);
			w.setOutput(out);
			w.write(null, new IIOImage(img, null, null), p);
			w.dispose();
		}
		catch (Exception e)
		{
			log.warn("[DEMO] frame write failed", e);
		}
	}

	// ---- finding things ----

	private int tab(String tooltip)
	{
		for (int i = 0; i < sidebar.getTabCount(); i++)
		{
			if (tooltip.equals(sidebar.getToolTipTextAt(i)))
			{
				return i;
			}
		}
		throw new IllegalStateException("no sidebar tab '" + tooltip + "'");
	}

	private Point tabCentre(int index)
	{
		Rectangle r = sidebar.getBoundsAt(index);
		return new Point(r.x + r.width / 2, r.y + r.height / 2);
	}

	/** Centre of a showing component, in sidebar coordinates (works for the popup menu too). */
	private Point centre(Component c)
	{
		Point p = c.getLocationOnScreen();
		Point s = sidebar.getLocationOnScreen();
		return new Point(p.x - s.x + c.getWidth() / 2, p.y - s.y + c.getHeight() / 2);
	}

	private Point headerPoint(int card)
	{
		Component header = merged.cards().get(card).header();
		Point p = centre(header);
		p.translate(-header.getWidth() / 5, 0);
		return p;
	}

	private Point headerTop(int card, int dy)
	{
		Component header = merged.cards().get(card).header();
		Point p = centre(header);
		p.translate(-header.getWidth() / 5, -header.getHeight() / 2 + dy);
		return p;
	}

	private Point gapPoint(int gap)
	{
		return gapPoint(gap, 0);
	}

	private Point gapPoint(int gap, int dy)
	{
		List<SectionCard> cards = merged.cards();
		SectionCard a = cards.get(gap);
		Point bottom = SwingUtilities.convertPoint(a, a.getWidth() / 2, a.getHeight() + 2, sidebar);
		bottom.translate(0, dy);
		return bottom;
	}

	private PickerView picker()
	{
		return find(merged, PickerView.class).get(0);
	}

	private Component searchField()
	{
		return find(picker(), JTextField.class).get(0);
	}

	private Component rowSwitch(String panel)
	{
		for (JLabel l : find(picker(), JLabel.class))
		{
			if (panel.equals(l.getText()))
			{
				return find(l.getParent(), Widgets.Switch.class).get(0);
			}
		}
		throw new IllegalStateException("no picker row '" + panel + "'");
	}

	/** A showing button by its text; {@code textOnly} skips icon buttons whose tooltip matches. */
	private Component button(String label, boolean textOnly)
	{
		for (JButton b : find(merged, JButton.class))
		{
			if (b.isShowing() && (label.equals(b.getText()) || (!textOnly && label.equals(b.getToolTipText()))))
			{
				return b;
			}
		}
		throw new IllegalStateException("no showing button '" + label + "'");
	}

	private Component menuItem(String text)
	{
		for (Component c : merged.presetMenu().getComponents())
		{
			if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText()))
			{
				return c;
			}
		}
		throw new IllegalStateException("no menu item '" + text + "'");
	}

	private static <T> List<T> find(Container root, Class<T> type)
	{
		List<T> out = new ArrayList<>();
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				out.add(type.cast(c));
			}
			if (c instanceof Container)
			{
				out.addAll(find((Container) c, type));
			}
		}
		return out;
	}

	// ---- timing ----

	private long now()
	{
		return (System.nanoTime() - t0) / 1_000_000;
	}

	private Timer later(int ms, Runnable r)
	{
		Timer t = new Timer(ms, e -> r.run());
		t.setRepeats(false);
		t.start();
		return t;
	}

	private void stopTimers()
	{
		for (Timer t : new Timer[]{captureTimer, cursorTimer, stepTimer})
		{
			if (t != null)
			{
				t.stop();
			}
		}
	}

}
