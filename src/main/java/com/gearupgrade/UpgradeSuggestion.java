/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import javax.annotation.Nullable;
import lombok.Value;

/**
 * One item that would raise DPS against the selected monster.
 */
@Value
public class UpgradeSuggestion
{
	EquipmentItem item;

	/** The item currently filling that slot, or null if the slot is empty. */
	@Nullable
	EquipmentItem replacing;

	long cost;
	double dpsBefore;
	double dpsAfter;

	/** Coins still needed to afford this, or 0 if it is already within budget. */
	long shortfall;

	public double getDpsGain()
	{
		return dpsAfter - dpsBefore;
	}

	public double getPercentGain()
	{
		if (dpsBefore <= 0)
		{
			return 0;
		}
		return (getDpsGain() / dpsBefore) * 100.0d;
	}

	/**
	 * DPS gained per million coins spent - the "is this worth it" number.
	 */
	public double getGainPerMillion()
	{
		if (cost <= 0)
		{
			return Double.MAX_VALUE;
		}
		return getDpsGain() / (cost / 1_000_000.0d);
	}
}
