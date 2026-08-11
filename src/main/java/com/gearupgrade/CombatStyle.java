/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The three combat styles the panel shows a tab for.
 */
@Getter
@RequiredArgsConstructor
public enum CombatStyle
{
	MELEE("Melee"),
	RANGED("Ranged"),
	MAGIC("Magic");

	private final String displayName;
}
