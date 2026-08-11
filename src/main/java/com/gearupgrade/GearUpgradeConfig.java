/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(GearUpgradeConfig.GROUP)
public interface GearUpgradeConfig extends Config
{
	String GROUP = "gear-upgrade";

	@ConfigItem(
		keyName = "prayerBoost",
		name = "Assumed prayers",
		description = "Which offensive prayers to assume when comparing gear",
		position = 1
	)
	default PrayerBoost prayerBoost()
	{
		return PrayerBoost.STANDARD;
	}

	@ConfigItem(
		keyName = "baseSpellMaxHit",
		name = "Base spell max hit",
		description = "Max hit of the spell you intend to cast, before magic damage bonuses. "
			+ "Magic damage depends on the spell rather than your gear, so this sets the baseline.",
		position = 2
	)
	@Range(min = 1, max = 100)
	default int baseSpellMaxHit()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "maxSuggestions",
		name = "Suggestions per style",
		description = "How many upgrades to list in each combat style tab",
		position = 3
	)
	@Range(min = 1, max = 25)
	default int maxSuggestions()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "budgetOverride",
		name = "Budget override (gp)",
		description = "Spend limit to use instead of your actual coins. Set to 0 to use your coins.",
		position = 4
	)
	default long budgetOverride()
	{
		return 0;
	}
}
