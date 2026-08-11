/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import lombok.Value;

/**
 * Standard Old School combat arithmetic, used purely to rank one piece of
 * equipment against another.
 *
 * <p>These are the same published formulas the wiki's gear comparison uses:
 * an accuracy roll derived from effective level and equipment bonus, and a max
 * hit derived from effective strength. Nothing here observes or reacts to a
 * live fight - it is arithmetic over item stats and a stored monster profile.
 */
public final class DpsCalculator
{
	/** Length of one game tick, in seconds. */
	private static final double TICK_SECONDS = 0.6d;

	/** Style bonus from an accurate/aggressive attack option. */
	private static final int STYLE_BONUS = 3;

	private DpsCalculator()
	{
	}

	@Value
	public static class Result
	{
		double dps;
		int maxHit;
		double accuracy;
	}

	public static Result compute(GearStats gear, PlayerLevels levels, Monster monster,
								 DamageType type, PrayerBoost prayer, int baseSpellMaxHit)
	{
		final int attackBonus = type.attackBonus(gear);
		final int defenceBonus = type.defenceBonus(monster);
		final int monsterDefLevel = monster.defenceLevelFor(type);

		final int effectiveAttack;
		final int maxHit;

		switch (type.getStyle())
		{
			case RANGED:
				effectiveAttack = effectiveLevel(levels.getRanged(), prayer.getRangedAttack());
				maxHit = rangedMaxHit(levels.getRanged(), prayer.getRangedStrength(), gear.getRstr());
				break;

			case MAGIC:
				effectiveAttack = effectiveLevel(levels.getMagic(), prayer.getMagicAttack());
				maxHit = magicMaxHit(baseSpellMaxHit, gear.getMdmg());
				break;

			case MELEE:
			default:
				effectiveAttack = effectiveLevel(levels.getAttack(), prayer.getMeleeAttack());
				maxHit = meleeMaxHit(levels.getStrength(), prayer.getMeleeStrength(), gear.getStr());
				break;
		}

		// Set bonuses (crystal armour with a crystal bow) scale the roll and the
		// hit rather than adding flat equipment bonuses.
		final int attackRoll =
			(int) (effectiveAttack * (attackBonus + 64) * gear.getAccuracyMultiplier());
		final int defenceRoll = (monsterDefLevel + 9) * (defenceBonus + 64);

		final int boostedMaxHit = (int) Math.floor(maxHit * gear.getDamageMultiplier());

		final double accuracy = hitChance(attackRoll, defenceRoll);
		final double interval = Math.max(1, gear.getAttackSpeed()) * TICK_SECONDS;

		// Damage is uniform over 0..maxHit, so the mean of a landed hit is maxHit / 2.
		final double dps = accuracy * (boostedMaxHit / 2.0d) / interval;

		return new Result(dps, boostedMaxHit, accuracy);
	}

	/**
	 * Effective level = floor(level * prayer multiplier) + style bonus + 8.
	 */
	private static int effectiveLevel(int level, double prayerMultiplier)
	{
		return (int) Math.floor(level * prayerMultiplier) + STYLE_BONUS + 8;
	}

	static int meleeMaxHit(int strengthLevel, double prayerMultiplier, int strengthBonus)
	{
		final int effectiveStrength = effectiveLevel(strengthLevel, prayerMultiplier);
		return (int) Math.floor(0.5d + effectiveStrength * (strengthBonus + 64) / 640.0d);
	}

	static int rangedMaxHit(int rangedLevel, double prayerMultiplier, int rangedStrength)
	{
		final int effectiveRanged = effectiveLevel(rangedLevel, prayerMultiplier);
		return (int) Math.floor(0.5d + effectiveRanged * (rangedStrength + 64) / 640.0d);
	}

	/**
	 * Magic damage is set by the spell, then scaled by the magic damage bonus on
	 * gear. The base is supplied by the user because it depends which spell or
	 * powered staff they intend to use.
	 */
	static int magicMaxHit(int baseSpellMaxHit, float magicDamageBonus)
	{
		return (int) Math.floor(baseSpellMaxHit * (1.0d + magicDamageBonus / 100.0d));
	}

	/**
	 * Probability that an attack roll beats a defence roll.
	 */
	static double hitChance(int attackRoll, int defenceRoll)
	{
		if (attackRoll > defenceRoll)
		{
			return 1.0d - (defenceRoll + 2.0d) / (2.0d * (attackRoll + 1.0d));
		}
		return attackRoll / (2.0d * (defenceRoll + 1.0d));
	}
}
