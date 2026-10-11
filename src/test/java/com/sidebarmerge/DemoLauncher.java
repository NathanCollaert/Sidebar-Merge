package com.sidebarmerge;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Dev launcher: `gradlew dev -Pdemo` loads Sidebar Merge plus the demo recorder. */
public class DemoLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SidebarMergePlugin.class, DemoDirectorPlugin.class);
		RuneLite.main(args);
	}
}
