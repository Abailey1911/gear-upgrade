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
 * One form of a boss that changes defences mid-fight.
 *
 * <p>Zulrah is the clearest case: the same monster, the same Defence level, but
 * magic defence of -45, 0 or +300 depending on which form is out. Collapsing
 * that into one stat line makes the rotation look arbitrary when it is the
 * entire point of the fight.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonsterForm
{
	private String name;

	private int dstab;
	private int dslash;
	private int dcrush;
	private int dmagic;
	private int drange;

	/** Short explanation of what this form means for style choice. */
	private String note;
}
