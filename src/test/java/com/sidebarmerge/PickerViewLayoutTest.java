package com.sidebarmerge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.sidebarmerge.ui.Icons;
import com.sidebarmerge.ui.Widgets;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.Test;

public class PickerViewLayoutTest
{
	private static final int SIDEBAR_WIDTH = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH;

	// SidebarTab keys are panel class names, so each fake plugin needs its own class.
	static class Hunter extends PluginPanel
	{
	}

	static class Toa extends PluginPanel
	{
	}

	static class Setups extends PluginPanel
	{
	}

	static class Bank extends PluginPanel
	{
	}

	static class Xp extends PluginPanel
	{
	}

	static class Worlds extends PluginPanel
	{
	}

	@Test
	public void rowsStayInsideTheSidebar() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RuneLiteLAF.setup();
			JTabbedPane sidebar = new JTabbedPane();
			String[] names = {"Hunter Rumour Locations", "Toa Mistake Tracker", "Inventory Setups", "Bank Memory", "XP Tracker", "World Switcher"};
			PluginPanel[] panels = {new Hunter(), new Toa(), new Setups(), new Bank(), new Xp(), new Worlds()};
			for (int i = 0; i < names.length; i++)
			{
				sidebar.addTab(null, Icons.logo(1), panels[i].getWrappedPanel(), names[i]);
			}
			List<SidebarTab> tabs = SidebarTab.list(sidebar, null, new IdentityHashMap<>());

			PickerView picker = new PickerView();
			picker.load(tabs, Arrays.asList(tabs.get(0).key(), tabs.get(1).key(), tabs.get(2).key()));
			picker.setSize(SIDEBAR_WIDTH, 480);
			for (int pass = 0; pass < 3; pass++)
			{
				layOutByHand(picker);
			}

			JViewport viewport = all(picker, JViewport.class).get(0);
			List<Widgets.Switch> switches = all(picker, Widgets.Switch.class);
			assertEquals("one switch per panel", names.length, switches.size());
			for (Widgets.Switch sw : switches)
			{
				Rectangle r = SwingUtilities.convertRectangle(sw.getParent(), sw.getBounds(), viewport);
				assertTrue("switch inside the visible width: " + r + " in " + viewport.getWidth(), r.x + r.width <= viewport.getWidth());
				assertEquals("switch drawn full width", sw.getPreferredSize().width, r.width);
				assertEquals("switch drawn full height", sw.getPreferredSize().height, r.height);
				Rectangle row = SwingUtilities.convertRectangle(sw.getParent().getParent().getParent(), sw.getParent().getParent().getBounds(), viewport);
				int rowMid = row.y + row.height / 2;
				int switchMid = r.y + r.height / 2;
				assertTrue("switch centred in its row (" + switchMid + " vs " + rowMid + ")", Math.abs(switchMid - rowMid) <= 1);
			}
			for (JLabel label : all(picker, JLabel.class))
			{
				Rectangle r = SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), viewport);
				assertTrue("label inside the visible width: " + label.getText() + " " + r, r.x + r.width <= viewport.getWidth());
			}

			try
			{
				BufferedImage img = new BufferedImage(SIDEBAR_WIDTH, 480, BufferedImage.TYPE_INT_RGB);
				Graphics2D g = img.createGraphics();
				RepaintManager.currentManager(picker).setDoubleBufferingEnabled(false);
				picker.paint(g);
				g.dispose();
				ImageIO.write(img, "png", new File("build/picker-layout.png"));
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
	}

	/** Container.validate does nothing without a native peer, and offscreen there is none. */
	private static void layOutByHand(Component c)
	{
		if (c instanceof Container)
		{
			((Container) c).doLayout();
			for (Component child : ((Container) c).getComponents())
			{
				layOutByHand(child);
			}
		}
	}

	private static <T> List<T> all(Container root, Class<T> type)
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
				out.addAll(all((Container) c, type));
			}
		}
		return out;
	}
}
