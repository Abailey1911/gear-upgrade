/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.util.Locale;
import java.util.Map;

/**
 * Weapon behaviour that raw equipment stats do not capture.
 *
 * <p>Summing slot bonuses treats every piece as independent, which produces
 * obviously wrong loadouts: javelins counting towards thrown knives, arrows
 * counting towards a bow that makes its own, and set bonuses being ignored.
 * Each of those changes which item is genuinely the best buy.
 */
final class WeaponRules
{
	/**
	 * What a weapon draws from the ammo slot, if anything.
	 */
	enum AmmoType
	{
		NONE,
		ARROWS,
		BOLTS,
		JAVELINS,
		/**
		 * The ascension crossbows fire only ascension bolts, and ascension bolts
		 * fit no other crossbow. Treating them as ordinary bolts would load Ruby
		 * dragon bolts into ascension crossbows and count them.
		 */
		ASCENSION_BOLTS,
		/** Eclipse atlatl's own darts. */
		ATLATL_DARTS
	}

	/** Crystal armour: accuracy and damage bonuses to the crystal bows. */
	private static final double CRYSTAL_HELM_ACCURACY = 0.05d;
	private static final double CRYSTAL_HELM_DAMAGE = 0.025d;
	private static final double CRYSTAL_BODY_ACCURACY = 0.15d;
	private static final double CRYSTAL_BODY_DAMAGE = 0.075d;
	private static final double CRYSTAL_LEGS_ACCURACY = 0.10d;
	private static final double CRYSTAL_LEGS_DAMAGE = 0.05d;

	private WeaponRules()
	{
	}

	private static String normalise(EquipmentItem item)
	{
		return item == null || item.getName() == null
			? ""
			: item.getName().toLowerCase(Locale.ROOT);
	}

	/**
	 * Ammunition a weapon actually fires.
	 *
	 * <p>Thrown weapons (knives, darts, axes), powered staves and the crystal
	 * bows all report NONE - they either carry their own ammunition or need
	 * none, so anything sitting in the ammo slot contributes nothing.
	 */
	static AmmoType weaponAmmo(EquipmentItem weapon)
	{
		final String name = normalise(weapon);
		if (name.isEmpty())
		{
			return AmmoType.NONE;
		}

		// Generate their own arrows.
		if (name.startsWith("crystal bow") || name.startsWith("bow of faerdhinen"))
		{
			return AmmoType.NONE;
		}

		if (name.startsWith("ascension crossbow"))
		{
			return AmmoType.ASCENSION_BOLTS;
		}
		if (name.contains("ballista"))
		{
			return AmmoType.JAVELINS;
		}
		if (name.startsWith("eclipse atlatl"))
		{
			return AmmoType.ATLATL_DARTS;
		}
		if (name.contains("crossbow") || name.contains("c'bow"))
		{
			return AmmoType.BOLTS;
		}
		if (name.contains("bow"))
		{
			return AmmoType.ARROWS;
		}

		return AmmoType.NONE;
	}

	/**
	 * What an ammo-slot item counts as.
	 */
	static AmmoType ammoType(EquipmentItem ammo)
	{
		final String name = normalise(ammo);
		if (name.isEmpty())
		{
			return AmmoType.NONE;
		}

		if (name.contains("javelin"))
		{
			return AmmoType.JAVELINS;
		}
		if (name.startsWith("atlatl dart"))
		{
			return AmmoType.ATLATL_DARTS;
		}
		if (name.contains("ascension bolt"))
		{
			return AmmoType.ASCENSION_BOLTS;
		}
		if (name.contains("bolt"))
		{
			return AmmoType.BOLTS;
		}
		if (name.contains("arrow"))
		{
			return AmmoType.ARROWS;
		}

		return AmmoType.NONE;
	}

	/**
	 * True when the ammo slot actually feeds the wielded weapon.
	 */
	static boolean ammoCounts(EquipmentItem weapon, EquipmentItem ammo)
	{
		final AmmoType needed = weaponAmmo(weapon);
		return needed != AmmoType.NONE && needed == ammoType(ammo);
	}

	private static boolean isCrystalBow(EquipmentItem weapon)
	{
		final String name = normalise(weapon);
		return name.startsWith("crystal bow") || name.startsWith("bow of faerdhinen");
	}

	/**
	 * Max-hit gain from a Soulreaper axe at its full five stacks. Each stack
	 * boosts Strength by 6% of the base level, so five add 30% of 99 - about 24%
	 * on the max hit once prayer and the style bonus are in the effective level.
	 */
	static final double SOULREAPER_STACK_DAMAGE = 1.24d;

	/**
	 * The Soulreaper axe's stacks are its whole point: on raw stats it ranks
	 * below weapons it beats comfortably in a real fight, so the model wears it
	 * at full stacks, as it is during any sustained kill.
	 */
	/**
	 * The Eclipse atlatl attacks with Ranged but hits with Strength: its max hit
	 * uses the Strength level and the melee strength bonus, not ranged strength.
	 * Rated on ranged strength it would look useless, whatever the player's
	 * Strength.
	 */
	static boolean hitsWithStrength(EquipmentItem weapon)
	{
		return normalise(weapon).startsWith("eclipse atlatl");
	}

	static void applySoulreaperStacks(GearStats stats, EquipmentItem weapon)
	{
		if (weapon != null && weapon.getName() != null
			&& weapon.getName().toLowerCase(Locale.ROOT).startsWith("soulreaper axe"))
		{
			stats.applyMultipliers(1.0d, SOULREAPER_STACK_DAMAGE);
		}
	}

	/**
	 * Applies the crystal armour set bonus, which only exists when a crystal bow
	 * is wielded. Without it a Bow of faerdhinen looks far weaker than it really
	 * is in a full crystal setup, and crystal armour looks pointless.
	 */
	static void applyCrystalSetBonus(GearStats stats, Map<Integer, EquipmentItem> worn,
									 int headSlot, int bodySlot, int legsSlot, int weaponSlot)
	{
		if (!isCrystalBow(worn.get(weaponSlot)))
		{
			return;
		}

		double accuracy = 0;
		double damage = 0;

		if (normalise(worn.get(headSlot)).startsWith("crystal helm"))
		{
			accuracy += CRYSTAL_HELM_ACCURACY;
			damage += CRYSTAL_HELM_DAMAGE;
		}
		if (normalise(worn.get(bodySlot)).startsWith("crystal body"))
		{
			accuracy += CRYSTAL_BODY_ACCURACY;
			damage += CRYSTAL_BODY_DAMAGE;
		}
		if (normalise(worn.get(legsSlot)).startsWith("crystal legs"))
		{
			accuracy += CRYSTAL_LEGS_ACCURACY;
			damage += CRYSTAL_LEGS_DAMAGE;
		}

		stats.applyMultipliers(1.0d + accuracy, 1.0d + damage);
	}
}
