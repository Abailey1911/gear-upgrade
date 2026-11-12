/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.Test;

public class ItemVariantsTest
{
	private static ItemVariants variants;

	@BeforeClass
	public static void load()
	{
		variants = new ItemVariants(new Gson());
		variants.load();
	}

	/**
	 * An ornament kit on an upgraded item replaces the upgrade's suffix, so an
	 * Elidinis' ward (or) must count for a table asking for the (f).
	 */
	@Test
	public void ornamentedUpgradeCountsAsTheUpgrade()
	{
		assertTrue(variants.variantsOf(27251).contains(27253));
		assertTrue(variants.variantsOf(25985).contains(27253));
	}

	/** Identical stats are not enough: an unenchanted bolt is not the (e) bolt. */
	@Test
	public void passivesAreNotCosmetic()
	{
		assertFalse(variants.variantsOf(21944).contains(21905)); // Ruby dragon bolts (e) <- Dragon bolts
		assertFalse(variants.variantsOf(10588).contains(4081)); // Salve amulet (e) <- Salve amulet
	}

	/** A charged weapon is priced by its buyable uncharged form. */
	@Test
	public void chargedWeaponsHaveABuyableForm()
	{
		assertEquals(28919, variants.tradeableFormOf(28922)); // Tonalztics of ralos
		assertEquals(34717, variants.tradeableFormOf(34715)); // Obligator
	}
}
