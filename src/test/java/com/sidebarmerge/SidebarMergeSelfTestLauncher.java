package com.sidebarmerge;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SidebarMergeSelfTestLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SidebarMergePlugin.class, SidebarMergeSelfTestPlugin.class, UiWatchdogPlugin.class);
		RuneLite.main(args);
	}
}
