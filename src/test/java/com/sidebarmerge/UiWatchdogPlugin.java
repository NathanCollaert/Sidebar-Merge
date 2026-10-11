package com.sidebarmerge;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(name = "Sidebar Merge UI Watchdog", description = "Dev only: logs UI-thread freezes with a stack trace")
public class UiWatchdogPlugin extends Plugin
{
	private static final long VISIBLE_FREEZE_MS = 250;
	private static final long PING_INTERVAL_MS = 100;
	private static final long POLL_MS = 20;
	private static final int MAX_STACK_FRAMES = 30;

	private volatile boolean watching;

	@Override
	protected void startUp()
	{
		watching = true;
		Thread t = new Thread(this::watch, "sm-ui-watchdog");
		t.setDaemon(true);
		t.start();
	}

	@Override
	protected void shutDown()
	{
		watching = false;
	}

	private void watch()
	{
		while (watching)
		{
			AtomicBoolean done = new AtomicBoolean();
			long sent = System.nanoTime();
			SwingUtilities.invokeLater(() -> done.set(true));
			boolean reported = false;
			while (!done.get() && watching)
			{
				sleep(POLL_MS);
				if (!reported && (System.nanoTime() - sent) / 1_000_000 > VISIBLE_FREEZE_MS)
				{
					reported = true;
					log.warn("[SM-WATCHDOG] UI thread stalled >{}ms; it is in:{}", VISIBLE_FREEZE_MS, swingStack());
				}
			}
			if (reported)
			{
				log.warn("[SM-WATCHDOG] UI thread stall ended after {} ms", (System.nanoTime() - sent) / 1_000_000);
			}
			sleep(PING_INTERVAL_MS);
		}
	}

	private static String swingStack()
	{
		StringBuilder trace = new StringBuilder();
		for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet())
		{
			if (e.getKey().getName().startsWith("AWT-EventQueue"))
			{
				for (int i = 0; i < Math.min(MAX_STACK_FRAMES, e.getValue().length); i++)
				{
					trace.append(System.lineSeparator()).append("\t    at ").append(e.getValue()[i]);
				}
			}
		}
		return trace.toString();
	}

	private static void sleep(long ms)
	{
		try
		{
			Thread.sleep(ms);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}
}
