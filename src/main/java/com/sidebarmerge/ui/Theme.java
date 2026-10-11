package com.sidebarmerge.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public final class Theme
{
	/** Darker than plugin content (DARK_GRAY), so the cards stand out. */
	public static final Color CANVAS = ColorScheme.DARKER_GRAY_COLOR;
	public static final Color SURFACE = new Color(46, 46, 46);
	public static final Color SURFACE_HOVER = new Color(55, 55, 55);
	public static final Color OUTLINE = new Color(60, 60, 60);
	public static final Color CONTENT = ColorScheme.DARK_GRAY_COLOR;

	public static final Color TEXT = Color.WHITE;
	public static final Color TEXT_MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	public static final Color TEXT_FAINT = new Color(120, 120, 120);

	public static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	public static final Color ACCENT_HOVER = new Color(240, 158, 30);
	public static final Color ON_ACCENT = new Color(28, 28, 28);
	public static final Color DANGER = new Color(190, 58, 46);
	public static final Color SWITCH_OFF = new Color(88, 88, 88);
	public static final Color DANGER_HOVER = new Color(214, 72, 58);

	public static final Color HOVER_FILL = new Color(255, 255, 255, 20);
	public static final Color PRESS_FILL = new Color(255, 255, 255, 36);

	/*
	 * Padding inside a box is never larger than the space around it, so related things sit closer
	 * together than unrelated ones.
	 */
	public static final int SPACE_XS = 4;
	public static final int SPACE_S = 8;
	public static final int SPACE_M = 12;
	public static final int SPACE_L = 16;
	public static final int SPACE_XL = 24;

	private static final int ICON_SIZE = 16;
	public static final int CONTROL_HEIGHT = 24;
	private static final int ICON_BUTTON_INSET = (CONTROL_HEIGHT - ICON_SIZE) / 2;
	public static final int HEADER_HEIGHT = ICON_SIZE + 2 * SPACE_S;
	public static final int ARC = 10;

	public static final int CONTENT_INSET = SPACE_M;
	public static final int CARD_GAP = SPACE_XS;
	/**
	 * At most 4px: a wrapped plugin panel is 225px plus its own scrollbar (7px, or 10px for some
	 * plugins) in a 242px sidebar, so a 10px scrollbar then costs one pixel of the panel's own
	 * padding, never its content. The self-test checks this.
	 */
	public static final int CARD_SIDE_MARGIN = SPACE_XS;
	public static final int GRIP_REACH = SPACE_XS;
	public static final int CARD_HEADER_PAD_LEFT = CONTENT_INSET - CARD_SIDE_MARGIN;
	public static final int CARD_HEADER_PAD_RIGHT = CONTENT_INSET - CARD_SIDE_MARGIN - ICON_BUTTON_INSET;

	public static Font title()
	{
		return FontManager.getRunescapeBoldFont();
	}

	public static Font body()
	{
		return FontManager.getRunescapeFont();
	}

	public static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	/** The caller disposes the returned copy. */
	public static Graphics2D smooth(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return g2;
	}

	private Theme()
	{
	}
}
