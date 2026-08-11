package com.gearupgrade;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GearUpgradePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GearUpgradePlugin.class);
		RuneLite.main(args);
	}
}
