package com.gearupgrade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DpsCalculatorTest
{
	/**
	 * 99 Strength, no prayer, no strength bonus, aggressive style is a max hit
	 * of 11 - the standard published unarmed figure.
	 */
	@Test
	public void unarmedMaxHitAt99Strength()
	{
		assertEquals(11, DpsCalculator.meleeMaxHit(99, 1.0d, 0));
	}

	@Test
	public void strengthBonusRaisesMaxHit()
	{
		final int plain = DpsCalculator.meleeMaxHit(99, 1.0d, 0);
		final int buffed = DpsCalculator.meleeMaxHit(99, 1.0d, 100);
		assertTrue("more strength bonus must not lower max hit", buffed > plain);
	}

	@Test
	public void pietyRaisesMaxHit()
	{
		final int noPrayer = DpsCalculator.meleeMaxHit(99, 1.0d, 80);
		final int piety = DpsCalculator.meleeMaxHit(99, PrayerBoost.STANDARD.getMeleeStrength(), 80);
		assertTrue("piety must raise max hit", piety > noPrayer);
	}

	@Test
	public void magicDamageBonusScalesBaseSpell()
	{
		// A 30 base spell with +20% magic damage caps at 36.
		assertEquals(36, DpsCalculator.magicMaxHit(30, 20f));
		assertEquals(30, DpsCalculator.magicMaxHit(30, 0f));
	}

	@Test
	public void hitChanceStaysWithinBounds()
	{
		for (int attack = 1; attack <= 30000; attack += 977)
		{
			for (int defence = 1; defence <= 30000; defence += 977)
			{
				final double chance = DpsCalculator.hitChance(attack, defence);
				assertTrue("hit chance below 0: " + chance, chance >= 0.0d);
				assertTrue("hit chance above 1: " + chance, chance <= 1.0d);
			}
		}
	}

	@Test
	public void higherAttackRollBeatsLowerAgainstSameDefence()
	{
		final double weak = DpsCalculator.hitChance(5000, 10000);
		final double strong = DpsCalculator.hitChance(20000, 10000);
		assertTrue("a bigger attack roll must hit more often", strong > weak);
	}

	/**
	 * A monster with no defence at all should still never be hit more than
	 * always, and a huge defence should not produce a negative chance.
	 */
	@Test
	public void extremeDefenceIsHandled()
	{
		assertTrue(DpsCalculator.hitChance(20000, 0) <= 1.0d);
		assertTrue(DpsCalculator.hitChance(1, 1_000_000) >= 0.0d);
	}
}
