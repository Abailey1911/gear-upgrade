/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A concrete attack type. Melee splits into three because a monster's defence
 * against stab/slash/crush can differ wildly (eg. Vorkath vs. Zulrah), and that
 * choice usually decides which weapon is actually the best buy.
 */
@Getter
@RequiredArgsConstructor
public enum DamageType
{
	STAB(CombatStyle.MELEE, "Stab"),
	SLASH(CombatStyle.MELEE, "Slash"),
	CRUSH(CombatStyle.MELEE, "Crush"),
	RANGED(CombatStyle.RANGED, "Ranged"),
	MAGIC(CombatStyle.MAGIC, "Magic");

	private final CombatStyle style;
	private final String displayName;

	/**
	 * The player's offensive bonus for this attack type.
	 */
	public int attackBonus(GearStats gear)
	{
		switch (this)
		{
			case STAB:
				return gear.getAstab();
			case SLASH:
				return gear.getAslash();
			case CRUSH:
				return gear.getAcrush();
			case RANGED:
				return gear.getArange();
			default:
				return gear.getAmagic();
		}
	}

	/**
	 * The monster's defensive bonus against this attack type.
	 */
	public int defenceBonus(Monster monster)
	{
		switch (this)
		{
			case STAB:
				return monster.getDstab();
			case SLASH:
				return monster.getDslash();
			case CRUSH:
				return monster.getDcrush();
			case RANGED:
				return monster.getDrange();
			default:
				return monster.getDmagic();
		}
	}
}
