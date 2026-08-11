package com.gearupgrade;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PvpOnlyItemsTest
{
	@Test
	public void ancientWarriorsGearIsExcluded()
	{
		assertTrue(PvpOnlyItems.matches("Vesta's chainbody"));
		assertTrue(PvpOnlyItems.matches("Vesta's longsword"));
		assertTrue(PvpOnlyItems.matches("Statius's warhammer"));
		assertTrue(PvpOnlyItems.matches("Morrigan's javelin"));
		assertTrue(PvpOnlyItems.matches("Zuriel's staff"));
	}

	@Test
	public void normalPvmGearIsNotExcluded()
	{
		assertFalse(PvpOnlyItems.matches("Torva full helm"));
		assertFalse(PvpOnlyItems.matches("Serpentine helm"));
		assertFalse(PvpOnlyItems.matches("Twisted bow"));
		assertFalse(PvpOnlyItems.matches("Abyssal whip"));
		assertFalse(PvpOnlyItems.matches("Masori body (f)"));
	}

	@Test
	public void matchingIsCaseInsensitiveAndNullSafe()
	{
		assertTrue(PvpOnlyItems.matches("VESTA'S CHAINBODY"));
		assertFalse(PvpOnlyItems.matches(null));
		assertFalse(PvpOnlyItems.matches(""));
	}
}
