package com.sidebarmerge.ui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/** The handful of controls the merged tab is built from, painted by hand so they match. */
public final class Widgets
{
	private static final int CHEVRON = 8;

	/**
	 * A CardLayout panel sized to the card showing, not the largest one, so the toolbar's
	 * two-icon state doesn't reserve the width of its Cancel + Done state.
	 */
	public static JPanel cardPanel(java.awt.CardLayout layout)
	{
		return new JPanel(layout)
		{
			@Override
			public Dimension getPreferredSize()
			{
				for (java.awt.Component c : getComponents())
				{
					if (c.isVisible())
					{
						java.awt.Insets in = getInsets();
						Dimension d = c.getPreferredSize();
						return new Dimension(d.width + in.left + in.right, d.height + in.top + in.bottom);
					}
				}
				return super.getPreferredSize();
			}
		};
	}

	/**
	 * A row of controls with {@code gap} between them and nothing at either end (FlowLayout pads
	 * both ends too, which knocks edges off the alignment column).
	 */
	public static JPanel hbox(int gap, java.awt.Component... items)
	{
		JPanel row = new JPanel();
		row.setOpaque(false);
		row.setLayout(new javax.swing.BoxLayout(row, javax.swing.BoxLayout.X_AXIS));
		for (int i = 0; i < items.length; i++)
		{
			if (i > 0)
			{
				row.add(javax.swing.Box.createHorizontalStrut(gap));
			}
			if (items[i] instanceof JComponent)
			{
				// Fixed-size controls: pin min and max to the preferred size. A bare JComponent
				// reports its current size as its minimum, so BoxLayout would otherwise squeeze it.
				((JComponent) items[i]).setAlignmentY(java.awt.Component.CENTER_ALIGNMENT);
				((JComponent) items[i]).setMinimumSize(items[i].getPreferredSize());
				((JComponent) items[i]).setMaximumSize(items[i].getPreferredSize());
			}
			row.add(items[i]);
		}
		return row;
	}

	/** Borderless square button with a vector icon and a soft rounded hover. */
	public static JButton iconButton(Icon icon, String tooltip, Runnable action)
	{
		JButton b = new JButton(icon)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = Theme.smooth(g);
				if (getModel().isPressed())
				{
					g2.setColor(Theme.PRESS_FILL);
					g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 8, 8));
				}
				else if (getModel().isRollover())
				{
					g2.setColor(Theme.HOVER_FILL);
					g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 8, 8));
				}
				Icon i = getIcon();
				i.paintIcon(this, g2, (getWidth() - i.getIconWidth()) / 2, (getHeight() - i.getIconHeight()) / 2);
				g2.dispose();
			}
		};
		plain(b);
		b.setToolTipText(tooltip);
		b.setPreferredSize(new Dimension(Theme.CONTROL, Theme.CONTROL));
		b.addActionListener(e -> action.run());
		return b;
	}

	/** Filled accent pill, for the one primary action on screen. */
	public static JButton primaryButton(String text, Runnable action)
	{
		return pill(text, Theme.ACCENT, Theme.ACCENT_HOVER, Theme.ON_ACCENT, action);
	}

	/** Filled red pill, for confirming something that can't be undone. */
	public static JButton dangerButton(String text, Runnable action)
	{
		return pill(text, Theme.DANGER, Theme.DANGER_HOVER, java.awt.Color.WHITE, action);
	}

	private static JButton pill(String text, java.awt.Color fill, java.awt.Color hover, java.awt.Color ink, Runnable action)
	{
		JButton b = new JButton(text)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = Theme.smooth(g);
				g2.setColor(!isEnabled() ? Theme.OUTLINE : getModel().isRollover() ? hover : fill);
				g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
				paintCenteredText(g2, this, isEnabled() ? ink : Theme.TEXT_FAINT);
				g2.dispose();
			}
		};
		plain(b);
		b.setFont(Theme.small());
		b.setPreferredSize(new Dimension(textWidth(b) + 2 * Theme.SPACE_M, Theme.CONTROL));
		b.addActionListener(e -> action.run());
		return b;
	}

	/**
	 * Icon, text and a down chevron, for opening a menu. Long text is cut short with an ellipsis
	 * rather than pushing the toolbar's other buttons off the edge.
	 */
	public static JButton dropdownButton(Icon icon, int maxWidth, Runnable action)
	{
		JButton b = new JButton()
		{
			@Override
			public Dimension getPreferredSize()
			{
				int text = getFontMetrics(getFont()).stringWidth(getText() == null ? "" : getText());
				return new Dimension(Math.min(maxWidth, Theme.SPACE_XS + icon.getIconWidth() + Theme.SPACE_S + text + Theme.SPACE_S + CHEVRON + Theme.SPACE_XS), Theme.CONTROL);
			}

			@Override
			public Dimension getMaximumSize()
			{
				return getPreferredSize();
			}

			// Room for the icon, a few letters and the chevron; BoxLayout shrinks it this far.
			@Override
			public Dimension getMinimumSize()
			{
				return new Dimension(Theme.SPACE_XS + icon.getIconWidth() + Theme.SPACE_S + Theme.SPACE_XL + Theme.SPACE_S + CHEVRON + Theme.SPACE_XS,
					Theme.CONTROL);
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = Theme.smooth(g);
				if (getModel().isPressed() || getModel().isRollover())
				{
					g2.setColor(getModel().isPressed() ? Theme.PRESS_FILL : Theme.HOVER_FILL);
					g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 8, 8));
				}
				icon.paintIcon(this, g2, Theme.SPACE_XS, (getHeight() - icon.getIconHeight()) / 2);

				int textX = Theme.SPACE_XS + icon.getIconWidth() + Theme.SPACE_S;
				int chevronX = getWidth() - Theme.SPACE_XS - CHEVRON;
				g2.setFont(getFont());
				g2.setColor(Theme.TEXT);
				FontMetrics fm = g2.getFontMetrics();
				String text = ellipsize(getText() == null ? "" : getText(), fm, chevronX - Theme.SPACE_S - textX);
				g2.drawString(text, textX, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());

				g2.setColor(getModel().isRollover() ? Theme.TEXT : Theme.TEXT_MUTED);
				g2.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
				double cy = getHeight() / 2.0;
				java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
				p.moveTo(chevronX, cy - 2);
				p.lineTo(chevronX + CHEVRON / 2.0, cy + 2.5);
				p.lineTo(chevronX + CHEVRON, cy - 2);
				g2.draw(p);
				g2.dispose();
			}
		};
		plain(b);
		b.setFont(Theme.title());
		b.addActionListener(e -> action.run());
		return b;
	}

	private static String ellipsize(String text, FontMetrics fm, int width)
	{
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		String dots = "…";
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > width)
		{
			end--;
		}
		return text.substring(0, end).trim() + dots;
	}

	/** Text-only button for secondary actions such as Cancel. */
	public static JButton textButton(String text, Runnable action)
	{
		JButton b = new JButton(text)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = Theme.smooth(g);
				if (getModel().isRollover())
				{
					g2.setColor(Theme.HOVER_FILL);
					g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
				}
				paintCenteredText(g2, this, getModel().isRollover() ? Theme.TEXT : Theme.TEXT_MUTED);
				g2.dispose();
			}
		};
		plain(b);
		b.setFont(Theme.small());
		b.setPreferredSize(new Dimension(textWidth(b) + 2 * Theme.SPACE_S, Theme.CONTROL));
		b.addActionListener(e -> action.run());
		return b;
	}

	/** Small rounded count badge, e.g. "4" next to a title. */
	public static JLabel countPill()
	{
		JLabel l = new JLabel("", SwingConstants.CENTER)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = Theme.smooth(g);
				g2.setColor(Theme.OUTLINE);
				g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
				g2.dispose();
				super.paintComponent(g);
			}

			// Its text changes, so a fixed maximum size would squeeze it; track the preferred size.
			@Override
			public Dimension getMaximumSize()
			{
				return getPreferredSize();
			}
		};
		l.setFont(Theme.small());
		l.setForeground(Theme.TEXT_MUTED);
		l.setBorder(new EmptyBorder(2, Theme.SPACE_S, 2, Theme.SPACE_S));
		return l;
	}

	/** Small caps-style section label, e.g. "IN THIS TAB". */
	public static JLabel overline(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(Theme.small());
		l.setForeground(Theme.TEXT_FAINT);
		return l;
	}

	/** On/off switch. Display only: the row it sits in handles the click. */
	public static final class Switch extends JComponent
	{
		private boolean on;

		public Switch(boolean on)
		{
			this.on = on;
			Dimension size = new Dimension(28, 16);
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
		}

		public void setOn(boolean on)
		{
			this.on = on;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			int w = 28;
			int h = 16;
			int y = (getHeight() - h) / 2;
			g2.setColor(on ? Theme.ACCENT : Theme.SWITCH_OFF);
			g2.fill(new RoundRectangle2D.Double(0, y, w, h, h, h));
			g2.setColor(java.awt.Color.WHITE);
			double knob = h - 4;
			g2.fill(new Ellipse2D.Double(on ? w - knob - 2 : 2, y + 2, knob, knob));
			g2.dispose();
		}
	}

	private static void plain(JButton b)
	{
		b.setContentAreaFilled(false);
		b.setBorderPainted(false);
		b.setFocusPainted(false);
		b.setOpaque(false);
		b.setRolloverEnabled(true);
		b.setBorder(new EmptyBorder(0, 0, 0, 0));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	private static void paintCenteredText(Graphics2D g2, JButton b, java.awt.Color color)
	{
		g2.setFont(b.getFont());
		g2.setColor(color);
		FontMetrics fm = g2.getFontMetrics();
		int x = (b.getWidth() - fm.stringWidth(b.getText())) / 2;
		int y = (b.getHeight() - fm.getHeight()) / 2 + fm.getAscent();
		g2.drawString(b.getText(), x, y);
	}

	private static int textWidth(JButton b)
	{
		return b.getFontMetrics(b.getFont()).stringWidth(b.getText());
	}

	private Widgets()
	{
	}
}
