/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.util.Locale;

/**
 * Ancient Warriors' equipment - the Bounty Hunter / Last Man Standing sets.
 *
 * <p>These carry strong offensive stats but can only be used inside PvP areas
 * (Daimon's Crater, Castle Wars, Clan Wars, Soul Wars, the TzHaar Fight Pit and
 * the house/clan hall combat rings). Attacking anything elsewhere is
 * interrupted, so they are never a real answer for a boss and must be kept out
 * of the rankings.
 */
final class PvpOnlyItems
{
	private static final String[] PREFIXES = {
		"vesta's",
		"statius's",
		"statius'",
		"morrigan's",
		"zuriel's",
	};

	private PvpOnlyItems()
	{
	}

	static boolean matches(String itemName)
	{
		if (itemName == null)
		{
			return false;
		}

		final String lower = itemName.toLowerCase(Locale.ROOT);
		for (String prefix : PREFIXES)
		{
			if (lower.startsWith(prefix))
			{
				return true;
			}
		}
		return false;
	}
}
