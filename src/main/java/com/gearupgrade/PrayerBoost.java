/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Which offensive prayers to assume when comparing gear. This shifts absolute
 * DPS numbers, so it is exposed rather than hard-coded.
 */
@Getter
@RequiredArgsConstructor
public enum PrayerBoost
{
	NONE("No prayers", 1.0d, 1.0d, 1.0d, 1.0d, 1.0d),
	STANDARD("Piety / Rigour / Augury", 1.20d, 1.23d, 1.20d, 1.23d, 1.25d);

	private final String displayName;
	private final double meleeAttack;
	private final double meleeStrength;
	private final double rangedAttack;
	private final double rangedStrength;
	private final double magicAttack;

	@Override
	public String toString()
	{
		return displayName;
	}
}
