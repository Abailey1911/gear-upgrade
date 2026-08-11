/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A best-in-slot setup for one boss and one combat style, taken from the
 * wiki's recommended equipment tables.
 *
 * <p>These tables are the source of truth for what to aim at. Computing a
 * setup from raw equipment bonuses cannot work, because it has no way to see
 * passive effects, weapon requirements, special attacks or set bonuses - which
 * is exactly what decides real best-in-slot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BisSetup
{
	/** Specific boss this table is for, or null when it covers a whole group. */
	private String monster;

	/**
	 * Content group this table covers, eg. "Tombs of Amascut". The wiki
	 * publishes one melee/ranged/magic switch for a whole raid rather than a
	 * table per boss, so a group entry applies to every boss in it unless a
	 * boss-specific entry overrides it.
	 */
	private String group;

	/**
	 * True for the general per-style ladders used when a boss has no table of
	 * its own. Better than falling back to a computed ranking, which cannot see
	 * passives or set bonuses and produces obviously wrong gear.
	 */
	private boolean isDefault;

	/** MELEE, RANGED or MAGIC. */
	private String style;

	private List<BisSlot> slots;

	/**
	 * Special attack weapons the method relies on - defence reducers and the
	 * like. These are brought in addition to the worn setup, so they sit
	 * outside the slot list.
	 */
	private List<String> specWeapons;

	/** Free-text guidance, eg. a required weapon class or the kill method. */
	private String note;

	/**
	 * Set when the bundled data has not yet been checked by hand. Surfaced in
	 * the UI so a questionable entry is never presented as authoritative.
	 */
	private boolean needsReview;

	public List<BisSlot> getSlots()
	{
		return slots == null ? Collections.emptyList() : slots;
	}

	public List<String> getSpecWeapons()
	{
		return specWeapons == null ? Collections.emptyList() : specWeapons;
	}

	public CombatStyle combatStyle()
	{
		try
		{
			return CombatStyle.valueOf(style);
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			return CombatStyle.MELEE;
		}
	}
}
