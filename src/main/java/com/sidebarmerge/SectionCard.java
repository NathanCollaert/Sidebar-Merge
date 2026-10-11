package com.sidebarmerge;

import com.sidebarmerge.ui.Icons;
import com.sidebarmerge.ui.Theme;
import com.sidebarmerge.ui.Widgets;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * One merged panel: a rounded card with a header (chevron, icon, title, "open on its own")
 * over the plugin's content. Click the header to collapse, drag it to reorder.
 */
final class SectionCard extends JPanel
{
	interface Listener
	{
		void toggle(SectionCard card);

		void openOwnTab(SectionCard card);

		void dragMoved(SectionCard card, int stackY);

		void dragEnded(SectionCard card, int stackY);
	}

	private static final int DRAG_THRESHOLD = 5;

	final BorrowedPanel borrowed;
	private final Listener listener;
	private final JPanel header = new JPanel(new BorderLayout());
	private final JButton openButton;
	private final Component content;

	private boolean collapsed;
	private int fixedHeight;
	private boolean hover;
	private boolean dragging;
	/** While the stack glides, content keeps the larger of its old and new size and is clipped. */
	private int frozenHeight = -1;
	private double chevronFrom;
	private double chevronTo;
	private double chevron;

	SectionCard(BorrowedPanel borrowed, boolean collapsed, int fixedHeight, Listener listener)
	{
		super(null);
		this.borrowed = borrowed;
		this.listener = listener;
		this.collapsed = collapsed;
		this.fixedHeight = fixedHeight;
		chevron = chevronTo = chevronFrom = collapsed ? 0 : 90;
		setOpaque(false);
		setName(borrowed.tab.tooltip);

		openButton = Widgets.iconButton(Icons.openOwnTab(), "Open on its own", () -> listener.openOwnTab(this));
		buildHeader();

		content = borrowed.content();
		add(header);
	}

	/**
	 * Bring the plugin's content into the card; call once the card is in the stack. Uses
	 * setComponentZOrder, which re-parents within the window without removeNotify/addNotify: a
	 * plain add() rebuilds the native state of every component inside, and for a panel like World
	 * Switcher (hundreds of rows) that alone took over a second.
	 */
	void attachContent()
	{
		if (content.getParent() != this)
		{
			// setComponentZOrder only moves within one window; new content (an unwrapped panel's
			// fresh scroll pane) has no window yet and takes the plain add.
			java.awt.Window here = SwingUtilities.getWindowAncestor(this);
			if (here != null && content.getParent() != null && SwingUtilities.getWindowAncestor(content) == here)
			{
				setComponentZOrder(content, getComponentCount());
			}
			else
			{
				add(content);
			}
		}
		content.setVisible(!collapsed);
	}

	boolean isCollapsed()
	{
		return collapsed;
	}

	JComponent header()
	{
		return header;
	}

	JButton openButton()
	{
		return openButton;
	}

	/** Height the user dragged this card to, or -1 when it shares the space equally (auto). */
	int fixedHeight()
	{
		return fixedHeight;
	}

	void setFixedHeight(int height)
	{
		fixedHeight = height;
	}

	/** Call between the stack's beginTransition and revalidate, so the change glides. */
	void setCollapsed(boolean collapsed)
	{
		if (this.collapsed == collapsed)
		{
			return;
		}
		this.collapsed = collapsed;
		chevronFrom = chevron;
		chevronTo = collapsed ? 0 : 90;
		if (!collapsed)
		{
			content.setVisible(true);
		}
		header.setToolTipText(tooltip());
	}

	void setDragging(boolean dragging)
	{
		this.dragging = dragging;
		header.setCursor(Cursor.getPredefinedCursor(dragging ? Cursor.MOVE_CURSOR : Cursor.HAND_CURSOR));
		repaint();
	}

	void freezeContent(int cardHeight)
	{
		frozenHeight = cardHeight;
	}

	void transitionProgress(double t)
	{
		chevron = chevronFrom + (chevronTo - chevronFrom) * t;
		header.repaint();
	}

	void transitionDone()
	{
		frozenHeight = -1;
		chevron = chevronFrom = chevronTo;
		content.setVisible(!collapsed);
		revalidate();
		repaint();
	}

	@Override
	public void doLayout()
	{
		int w = getWidth();
		header.setBounds(0, 0, w, Theme.HEADER_HEIGHT);
		int contentHeight = Math.max(getHeight(), frozenHeight) - Theme.HEADER_HEIGHT;
		content.setBounds(0, Theme.HEADER_HEIGHT, w, Math.max(0, contentHeight));
	}

	@Override
	public Dimension getPreferredSize()
	{
		Dimension c = content.getPreferredSize();
		return new Dimension(c.width, Theme.HEADER_HEIGHT + (collapsed ? 0 : c.height));
	}

	/**
	 * Plugin content repaints itself (pings, timers). Painting from the card instead keeps the
	 * rounded corners and outline drawn over it from being wiped by those repaints.
	 */
	@Override
	protected boolean isPaintingOrigin()
	{
		return true;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = Theme.smooth(g);
		g2.setColor(hover ? Theme.SURFACE_HOVER : Theme.SURFACE);
		g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), Theme.ARC, Theme.ARC));
		g2.dispose();
	}

	@Override
	public void paint(Graphics g)
	{
		Graphics2D outer = (Graphics2D) g.create();
		if (dragging)
		{
			outer.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
		}
		super.paint(outer);

		int w = getWidth();
		int h = getHeight();
		Graphics2D g2 = Theme.smooth(outer);
		RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w, h, Theme.ARC, Theme.ARC);

		// The plugin's content is opaque and square; mask its corners back to the canvas.
		Area corners = new Area(new Rectangle(0, 0, w, h));
		corners.subtract(new Area(shape));
		g2.setColor(getParent() != null ? getParent().getBackground() : Theme.CANVAS);
		g2.fill(corners);

		if (h > Theme.HEADER_HEIGHT + 1)
		{
			g2.setColor(Theme.OUTLINE);
			g2.drawLine(1, Theme.HEADER_HEIGHT - 1, w - 2, Theme.HEADER_HEIGHT - 1);
		}
		g2.setColor(header.isFocusOwner() ? Theme.ACCENT : Theme.OUTLINE);
		g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, Theme.ARC, Theme.ARC));
		g2.dispose();
		outer.dispose();
	}

	private void buildHeader()
	{
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(0, Theme.CARD_PAD_LEFT, 0, Theme.CARD_PAD_RIGHT));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setToolTipText(tooltip());
		header.setFocusable(true);

		JLabel chevronLabel = new JLabel(Icons.chevron(() -> chevron));
		JLabel title = new JLabel(borrowed.tab.tooltip, borrowed.tab.icon, JLabel.LEFT);
		title.setFont(Theme.small());
		title.setForeground(Theme.TEXT);
		title.setIconTextGap(Theme.SPACE_S);

		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
		left.add(chevronLabel);
		left.add(Box.createHorizontalStrut(Theme.SPACE_XS));
		left.add(title);

		JPanel right = new JPanel(new BorderLayout());
		right.setOpaque(false);
		int inset = (Theme.HEADER_HEIGHT - Theme.CONTROL) / 2;
		right.setBorder(new EmptyBorder(inset, 0, inset, 0));
		right.add(openButton, BorderLayout.CENTER);

		header.add(left, BorderLayout.CENTER);
		header.add(right, BorderLayout.EAST);

		MouseAdapter mouse = new MouseAdapter()
		{
			private Point pressed;
			private boolean dragStarted;

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setHover(true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				// Moving onto the open button also "exits" the header.
				setHover(header.contains(e.getPoint()));
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				pressed = e.getPoint();
				dragStarted = false;
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (pressed == null)
				{
					return;
				}
				if (!dragStarted && pressed.distance(e.getPoint()) > DRAG_THRESHOLD)
				{
					dragStarted = true;
					setDragging(true);
				}
				if (dragStarted)
				{
					listener.dragMoved(SectionCard.this, stackY(e));
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (dragStarted)
				{
					setDragging(false);
					listener.dragEnded(SectionCard.this, stackY(e));
				}
				else if (pressed != null && SwingUtilities.isLeftMouseButton(e) && header.contains(e.getPoint()))
				{
					listener.toggle(SectionCard.this);
				}
				pressed = null;
				dragStarted = false;
			}
		};
		header.addMouseListener(mouse);
		header.addMouseMotionListener(mouse);

		header.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle");
		header.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "toggle");
		header.getActionMap().put("toggle", new AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				listener.toggle(SectionCard.this);
			}
		});
		header.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				repaint();
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				repaint();
			}
		});
	}

	private int stackY(MouseEvent e)
	{
		return SwingUtilities.convertPoint(header, e.getPoint(), getParent()).y;
	}

	private void setHover(boolean hover)
	{
		if (this.hover != hover)
		{
			this.hover = hover;
			repaint(0, 0, getWidth(), Theme.HEADER_HEIGHT);
		}
	}

	private String tooltip()
	{
		return (collapsed ? "Click to expand" : "Click to collapse") + ", drag to reorder";
	}
}
