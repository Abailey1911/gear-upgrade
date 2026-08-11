/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defensive profile of a boss, deserialised from monsters.json.
 *
 * <p>Only the fields that affect which gear is worth buying are modelled: the
 * defence roll uses defence level plus the style-specific defence bonus, and
 * magic attacks roll against magic level on most monsters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Monster
{
	private String name;
	private String category;

	/**
	 * Difficulty tier from the wiki's bossing ladder - Easy, Medium, Hard,
	 * Elite, Master. The selector narrows by tier first, since that is how
	 * players actually think about what they are ready to fight.
	 */
	private String tier;

	/** Raid or content the boss belongs to, used to group the selector. */
	private String group;

	/** True once the defensive stats have been checked against the wiki. */
	private boolean verified;

	/**
	 * False for content you cannot bring equipment into - the Gauntlet strips
	 * you and makes you build gear inside, so ranking bank items against it is
	 * meaningless rather than merely inaccurate.
	 *
	 * <p>Boxed so that omitting it in JSON means "true" rather than false.
	 */
	private Boolean gearApplicable;

	/** Explanation shown when {@link #gearApplicable} is false. */
	private String gearNotApplicableReason;

	public boolean isGearApplicable()
	{
		return !Boolean.FALSE.equals(gearApplicable);
	}

	private int hitpoints;
	private int defenceLevel;
	private int magicLevel;

	private int dstab;
	private int dslash;
	private int dcrush;
	private int dmagic;
	private int drange;

	/**
	 * Monsters whose magic defence rolls against their Defence level rather than
	 * their Magic level. True for most non-player monsters in practice, but a few
	 * (notably dragons) are commonly modelled off magic level, so it stays a flag.
	 */
	private boolean magicDefenceUsesDefenceLevel;

	/**
	 * Gear worth bringing for its effect rather than its stats. Surfaced next to
	 * the ranking rather than folded into it, since these are usually a
	 * deliberate DPS sacrifice.
	 */
	private List<GearNote> notes;

	/**
	 * Elemental weakness, eg. "fire" at 50%. Two bosses can share an identical
	 * stat block and still want different spells because of this.
	 */
	private String weaknessElement;
	private int weaknessPercent;

	/**
	 * Things that simply do not work here - "poison", "venom", "freeze",
	 * "cannons". A venom-reliant weapon loses much of its value against a boss
	 * with full poison resistance, which no stat comparison would reveal.
	 */
	private List<String> immunities;

	/**
	 * Monster attributes from the wiki data - "demon", "dragon", "undead" and
	 * so on. These decide whether demonbane, dragonbane and Salve apply, none
	 * of which is visible in a stat block.
	 */
	private List<String> attributes;

	public List<String> getAttributes()
	{
		return attributes == null ? Collections.emptyList() : attributes;
	}

	/**
	 * Styles that must be brought together in one inventory, because the fight
	 * changes form mid-kill. Distinct from a boss that merely offers several
	 * viable routes - the Phantom Muspah needs magic and ranged in the same
	 * trip, whereas Zulrah can be done ranged/magic or melee.
	 */
	private List<String> requiredStyles;

	/**
	 * Styles that each work on their own, as alternatives.
	 */
	private List<String> alternativeStyles;

	/**
	 * Per-form defences for bosses that change stats mid-fight. A single stat
	 * line cannot describe Zulrah, whose magic defence swings from -45 to +300
	 * depending on the form.
	 */
	private List<MonsterForm> forms;

	/**
	 * Styles that actually work here. Where a style is simply not used - melee
	 * in the Inferno, magic against Vardorvis - showing a full setup for it
	 * implies it is an option when it is not.
	 */
	private List<String> viableStyles;

	/** Why the excluded styles do not work, shown in place of a setup. */
	private String styleRestriction;

	public List<String> getViableStyles()
	{
		return viableStyles == null ? Collections.emptyList() : viableStyles;
	}

	public boolean isStyleViable(CombatStyle style)
	{
		return getViableStyles().isEmpty() || getViableStyles().contains(style.name());
	}

	public List<String> getRequiredStyles()
	{
		return requiredStyles == null ? Collections.emptyList() : requiredStyles;
	}

	public List<String> getAlternativeStyles()
	{
		return alternativeStyles == null ? Collections.emptyList() : alternativeStyles;
	}

	public List<MonsterForm> getForms()
	{
		return forms == null ? Collections.emptyList() : forms;
	}

	public List<GearNote> getNotes()
	{
		return notes == null ? Collections.emptyList() : notes;
	}

	public List<String> getImmunities()
	{
		return immunities == null ? Collections.emptyList() : immunities;
	}

	/**
	 * The authored notes plus generated ones for elemental weakness and
	 * immunities, so those never have to be written out by hand.
	 */
	public List<GearNote> allNotes()
	{
		final List<GearNote> all = new ArrayList<>(getNotes());

		if (getRequiredStyles().size() > 1)
		{
			all.add(new GearNote(
				"Bring " + joinStyles(getRequiredStyles()) + " together",
				"This fight changes form mid-kill, so these styles go in the same "
					+ "inventory. The tabs below are the setups you need at once, not "
					+ "alternatives to choose between."));
		}

		if (getAlternativeStyles().size() > 1)
		{
			all.add(new GearNote(
				joinStyles(getAlternativeStyles()) + " all work here",
				"These are alternative routes rather than switches - pick whichever "
					+ "your gear suits best."));
		}

		for (MonsterForm form : getForms())
		{
			all.add(new GearNote(
				"Form: " + form.getName(),
				(form.getNote() == null ? "" : form.getNote() + " ")
					+ "Defences - stab " + form.getDstab()
					+ ", slash " + form.getDslash()
					+ ", crush " + form.getDcrush()
					+ ", magic " + form.getDmagic()
					+ ", ranged " + form.getDrange() + "."));
		}

		if (weaknessElement != null && weaknessPercent > 0)
		{
			all.add(new GearNote(
				weaknessPercent + "% weak to " + weaknessElement,
				"Spells of this element are substantially more effective here, which "
					+ "outweighs raw magic bonuses when picking a staff or tome."));
		}

		if (!getImmunities().isEmpty())
		{
			all.add(new GearNote(
				"Immune to " + String.join(", ", getImmunities()),
				"Weapons or strategies relying on these do nothing here, however good "
					+ "their stats look."));
		}

		return all;
	}

	/**
	 * "MAGIC", "RANGED" becomes "Magic and Ranged".
	 */
	private static String joinStyles(List<String> styles)
	{
		final List<String> pretty = new ArrayList<>();
		for (String style : styles)
		{
			pretty.add(style.isEmpty()
				? style
				: style.charAt(0) + style.substring(1).toLowerCase());
		}

		if (pretty.size() == 1)
		{
			return pretty.get(0);
		}

		final String last = pretty.remove(pretty.size() - 1);
		return String.join(", ", pretty) + " and " + last;
	}

	/**
	 * The effective defence level used when rolling against a given attack type.
	 */
	public int defenceLevelFor(DamageType type)
	{
		if (type == DamageType.MAGIC && !magicDefenceUsesDefenceLevel)
		{
			return magicLevel;
		}
		return defenceLevel;
	}
}
