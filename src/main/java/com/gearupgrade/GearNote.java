/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gear that is worth bringing to a particular boss for its effect rather than
 * its combat stats - venom immunity, elemental weaknesses, and so on.
 *
 * <p>These are shown alongside the DPS ranking instead of being forced into it,
 * because the trade-off is the player's to make: a Serpentine helm at Zulrah
 * saves an inventory slot but is a genuine DPS loss against an Oathplate helm.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GearNote
{
	private String item;
	private String reason;
}
