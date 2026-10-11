package com.sidebarmerge;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Not a unit test: the dev client launcher behind `gradlew dev`. */
public class SidebarMergePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SidebarMergePlugin.class, UiWatchdogPlugin.class);
		RuneLite.main(args);
	}
}
