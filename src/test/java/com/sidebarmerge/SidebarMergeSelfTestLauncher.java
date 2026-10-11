package com.sidebarmerge;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Dev launcher: `gradlew dev -Pselftest` loads Sidebar Merge plus the scripted self-test. */
public class SidebarMergeSelfTestLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SidebarMergePlugin.class, SidebarMergeSelfTestPlugin.class, UiWatchdogPlugin.class);
		RuneLite.main(args);
	}
}
