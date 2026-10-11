package com.sidebarmerge;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Dev launcher: `gradlew dev` loads Sidebar Merge as a builtin in a live client. */
public class SidebarMergePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SidebarMergePlugin.class, UiWatchdogPlugin.class);
		RuneLite.main(args);
	}
}
