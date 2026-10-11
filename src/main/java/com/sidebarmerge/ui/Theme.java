package com.sidebarmerge.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Colours, sizes and fonts for the merged tab, built on RuneLite's own palette. */
public final class Theme
{
	/** Behind the cards. Plugin content is DARK_GRAY, so cards stand out against this. */
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
	 * Spacing. Every gap, padding and margin comes from this 4px scale, and padding inside a box
	 * is never larger than the space around it, so related things sit closer together than
	 * unrelated ones (an overline hugs the list it labels, a card's content sits no further from
	 * its edge than the card does from the next card).
	 *
	 * Content edges line up on one column, x = 12: the toolbar's logo, each card's chevron and
	 * each picker row's icon. On the right, icons end 12px from the edge.
	 */
	public static final int SPACE_XS = 4;
	public static final int SPACE_S = 8;
	public static final int SPACE_M = 12;
	public static final int SPACE_L = 16;
	public static final int SPACE_XL = 24;

	/** Buttons, pills and fields. */
	public static final int CONTROL = 24;
	/** A card header: a 16px row with 8px above and below. */
	public static final int HEADER_HEIGHT = 32;
	public static final int ARC = 10;

	/** The left edge of content everywhere in the tab; right-hand icons end the same distance in. */
	public static final int ALIGN = SPACE_M;
	/**
	 * Space between cards, and the margin around the stack, by request: tight, so the panels get
	 * the height. The resize handle reaches past the gap into the cards (see {@link #GRAB}).
	 */
	public static final int GAP = SPACE_XS;
	/**
	 * Side margin around cards. A wrapped plugin panel is 225px wide plus its own scrollbar (7px
	 * usually, 10px for some plugins) inside a 242px sidebar; at 4px a 10px scrollbar costs one
	 * pixel of the panel's own padding, never its content. The self-test checks that.
	 */
	public static final int SIDE = SPACE_XS;
	/** How far the resize handle reaches into the cards on either side of a gap. */
	public static final int GRAB = SPACE_XS;
	/** Card header padding, derived so the chevron sits on {@link #ALIGN}. */
	public static final int CARD_PAD_LEFT = ALIGN - SIDE;
	/** ... and the open button's icon ends {@link #ALIGN} from the edge (the button has a 4px inset). */
	public static final int CARD_PAD_RIGHT = ALIGN - SIDE - (CONTROL - 16) / 2;

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

	/** Antialiased copy of {@code g}; dispose it when done. */
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
