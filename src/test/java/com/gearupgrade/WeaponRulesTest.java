/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WeaponRulesTest
{
	private static EquipmentItem item(String name)
	{
		return new EquipmentItem(0, name, 0, false, null, 0, false, false);
	}

	/**
	 * The ascension crossbows fire nothing but ascension bolts, and those bolts
	 * fit no other crossbow - so neither side may be paired with ordinary bolts.
	 */
	@Test
	public void ascensionCrossbowsTakeOnlyAscensionBolts()
	{
		final EquipmentItem ascension = item("Ascension crossbows");
		final EquipmentItem zaryte = item("Zaryte crossbow");

		assertTrue(WeaponRules.ammoCounts(ascension, item("Diamond ascension bolts (e)")));
		assertTrue(WeaponRules.ammoCounts(ascension, item("Ascension bolts")));
		assertFalse(WeaponRules.ammoCounts(ascension, item("Ruby dragon bolts (e)")));

		assertFalse(WeaponRules.ammoCounts(zaryte, item("Onyx ascension bolts (e)")));
		assertTrue(WeaponRules.ammoCounts(zaryte, item("Ruby dragon bolts (e)")));
	}
}
