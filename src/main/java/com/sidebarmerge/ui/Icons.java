package com.sidebarmerge.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import net.runelite.client.util.ImageUtil;

/** Drawn in code on a 16x16 grid, so they stay crisp and need no resources. */
public final class Icons
{
	private static final BasicStroke STROKE = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final float MUTED_LOGO_ALPHA = 0.45f;

	private static final Color WOOD_SHADOW = new Color(54, 38, 20);
	private static final Color WOOD = new Color(92, 66, 36);
	private static final Color WOOD_LIT_EDGE = new Color(130, 96, 56);
	private static final Color PARCHMENT_LIT_EDGE = new Color(244, 228, 184);
	private static final Color PARCHMENT = new Color(218, 194, 142);
	private static final Color PARCHMENT_SHADOW = new Color(176, 150, 100);
	private static final Color INK = new Color(110, 80, 44);
	private static final Color OSRS_INTERFACE_ORANGE = new Color(255, 152, 31);
	private static final Color GOLD_CORNER = new Color(226, 168, 46);

	/** Points right at 0 degrees, down at 90. */
	public static Icon chevron(DoubleSupplier degrees)
	{
		return new Vector(16, (g, c) ->
		{
			g.rotate(Math.toRadians(degrees.getAsDouble()), 8, 8);
			g.draw(path(6, 4, 10, 8, 6, 12));
		});
	}

	public static Icon collapseAll()
	{
		return new Vector(16, (g, c) ->
		{
			g.draw(path(5, 1.5, 8, 4.5, 11, 1.5));
			// Without the centre line, the two chevrons read as an X.
			g.draw(new java.awt.geom.Line2D.Double(2.5, 8, 13.5, 8));
			g.draw(path(5, 14.5, 8, 11.5, 11, 14.5));
		});
	}

	public static Icon expandAll()
	{
		return new Vector(16, (g, c) ->
		{
			g.draw(path(5, 4.5, 8, 1.5, 11, 4.5));
			g.draw(new java.awt.geom.Line2D.Double(2.5, 8, 13.5, 8));
			g.draw(path(5, 11.5, 8, 14.5, 11, 11.5));
		});
	}

	public static Icon tune()
	{
		return new Vector(16, (g, c) ->
		{
			double[][] rows = {{3.5, 10}, {8, 5}, {12.5, 11}};
			for (double[] r : rows)
			{
				g.draw(new java.awt.geom.Line2D.Double(2, r[0], 14, r[0]));
				fillCircle(g, r[1], r[0], 2.2);
			}
		});
	}

	public static Icon openOwnTab()
	{
		return new Vector(16, (g, c) ->
		{
			Path2D box = new Path2D.Double();
			box.moveTo(7, 3.5);
			box.lineTo(3.5, 3.5);
			box.lineTo(3.5, 12.5);
			box.lineTo(12.5, 12.5);
			box.lineTo(12.5, 9);
			g.draw(box);
			g.draw(new java.awt.geom.Line2D.Double(8, 8, 13, 3));
			g.draw(path(9.5, 3, 13, 3, 13, 6.5));
		});
	}

	public static Icon plus()
	{
		return new Vector(16, (g, c) ->
		{
			g.draw(new java.awt.geom.Line2D.Double(8, 3.5, 8, 12.5));
			g.draw(new java.awt.geom.Line2D.Double(3.5, 8, 12.5, 8));
		});
	}

	public static Icon copy()
	{
		return new Vector(16, (g, c) ->
		{
			g.draw(new java.awt.geom.RoundRectangle2D.Double(5.5, 5.5, 8, 8, 2, 2));
			Path2D back = new Path2D.Double();
			back.moveTo(3, 10.5);
			back.lineTo(2.5, 10.5);
			back.lineTo(2.5, 2.5);
			back.lineTo(10.5, 2.5);
			back.lineTo(10.5, 3);
			g.draw(back);
		});
	}

	public static Icon pencil()
	{
		return new Vector(16, (g, c) ->
		{
			g.draw(path(3, 13, 3.5, 10, 11, 2.5, 13.5, 5, 6, 12.5, 3, 13));
			g.draw(new java.awt.geom.Line2D.Double(9.5, 4, 12, 6.5));
		});
	}

	public static Icon trash()
	{
		return new Vector(16, (g, c) ->
		{
			g.draw(new java.awt.geom.Line2D.Double(2.5, 4, 13.5, 4));
			g.draw(path(6, 4, 6, 2.5, 10, 2.5, 10, 4));
			g.draw(path(4, 4, 4.8, 13.5, 11.2, 13.5, 12, 4));
			g.draw(new java.awt.geom.Line2D.Double(6.8, 6.5, 6.8, 11));
			g.draw(new java.awt.geom.Line2D.Double(9.2, 6.5, 9.2, 11));
		});
	}

	public static Icon arrowUp()
	{
		return new Vector(16, (g, c) -> g.draw(path(4.5, 10, 8, 6, 11.5, 10)));
	}

	public static Icon arrowDown()
	{
		return new Vector(16, (g, c) -> g.draw(path(4.5, 6, 8, 10, 11.5, 6)));
	}

	/** Hard pixels with a black outline, like an OSRS item sprite, to match the other sidebar icons. */
	private static final String[] LOGO_PIXELS = {
		"................",
		"..KKKKKKKKKKKK..",
		".KgccccccccccgK.",
		".KbpoooooppppbK.",
		".KbqllllllqqqbK.",
		".KbqqqqqqqqqqbK.",
		".KbrrrrrrrrrrbK.",
		".KccccccccccccK.",
		".KaaaaaaaaaaaaK.",
		".KbpoooopppppbK.",
		".KbqlllllllqqbK.",
		".KbqqqqqqqqqqbK.",
		".KbrrrrrrrrrrbK.",
		".KgaaaaaaaaaagK.",
		"..KKKKKKKKKKKK..",
		"................",
	};

	private static BufferedImage logo;

	public static BufferedImage logoImage()
	{
		if (logo == null)
		{
			logo = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 16; y++)
			{
				for (int x = 0; x < 16; x++)
				{
					Color c = logoColor(LOGO_PIXELS[y].charAt(x));
					if (c != null)
					{
						logo.setRGB(x, y, c.getRGB());
					}
				}
			}
		}
		return logo;
	}

	public static Icon logo(int zoom)
	{
		BufferedImage src = logoImage();
		BufferedImage img = new BufferedImage(16 * zoom, 16 * zoom, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(src, 0, 0, 16 * zoom, 16 * zoom, null);
		g.dispose();
		return new ImageIcon(img);
	}

	public static Icon logoMuted()
	{
		BufferedImage grey = ImageUtil.grayscaleImage(logoImage());
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, MUTED_LOGO_ALPHA));
		g.drawImage(grey, 0, 0, null);
		g.dispose();
		return new ImageIcon(img);
	}

	private static Color logoColor(char ch)
	{
		switch (ch)
		{
			case 'K':
				return Color.BLACK;
			case 'a':
				return WOOD_SHADOW;
			case 'b':
				return WOOD;
			case 'c':
				return WOOD_LIT_EDGE;
			case 'p':
				return PARCHMENT_LIT_EDGE;
			case 'q':
				return PARCHMENT;
			case 'r':
				return PARCHMENT_SHADOW;
			case 'l':
				return INK;
			case 'o':
				return OSRS_INTERFACE_ORANGE;
			case 'g':
				return GOLD_CORNER;
			default:
				return null;
		}
	}

	static Color colorFor(Component c)
	{
		if (c == null)
		{
			return Theme.TEXT_MUTED;
		}
		if (!c.isEnabled())
		{
			return Theme.TEXT_FAINT;
		}
		if (c instanceof AbstractButton && ((AbstractButton) c).getModel().isRollover())
		{
			return Theme.TEXT;
		}
		return Theme.TEXT_MUTED;
	}

	private static Path2D path(double... xy)
	{
		Path2D p = new Path2D.Double();
		p.moveTo(xy[0], xy[1]);
		for (int i = 2; i < xy.length; i += 2)
		{
			p.lineTo(xy[i], xy[i + 1]);
		}
		return p;
	}

	private static void fillCircle(Graphics2D g, double cx, double cy, double r)
	{
		g.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
	}

	private static final class Vector implements Icon
	{
		private final int size;
		private final BiConsumer<Graphics2D, Component> painter;

		Vector(int size, BiConsumer<Graphics2D, Component> painter)
		{
			this.size = size;
			this.painter = painter;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = Theme.smooth(g);
			g2.translate(x, y);
			g2.setColor(colorFor(c));
			g2.setStroke(STROKE);
			painter.accept(g2, c);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return size;
		}

		@Override
		public int getIconHeight()
		{
			return size;
		}
	}

	private Icons()
	{
	}
}
