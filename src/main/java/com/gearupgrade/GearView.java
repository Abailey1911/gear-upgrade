/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * One sub-tab within a combat style.
 *
 * <p>A single ladder per style cannot be right for everyone, because best in
 * slot depends on what you are hitting with. Virtus is only best with Ancient
 * Magicks - with a powered staff Ancestral wins - and Oathplate only beats
 * Torva when the weapon slashes. The wiki's own progression guides split their
 * endgame setups the same way, and these views follow those splits.
 *
 * <p>Each style keeps a Recommended view, which is the boss's own wiki table
 * when one exists. The other views are general ladders for one way of fighting,
 * so they are not specific to the selected boss.
 */
@Getter
public enum GearView
{
	MELEE_RECOMMENDED(CombatStyle.MELEE, "Best", null, null),
	MELEE_CRUSH(CombatStyle.MELEE, "Crush", "CRUSH", DamageType.CRUSH),
	MELEE_SLASH(CombatStyle.MELEE, "Slash", "SLASH", DamageType.SLASH),
	MELEE_STAB(CombatStyle.MELEE, "Stab", "STAB", DamageType.STAB),

	RANGED_RECOMMENDED(CombatStyle.RANGED, "Best", null, null),
	RANGED_BOW(CombatStyle.RANGED, "Bows", "BOW", DamageType.RANGED),
	RANGED_CROSSBOW(CombatStyle.RANGED, "Crossbows", "CROSSBOW", DamageType.RANGED),
	/** The fast, high-DPS pair: Ascension crossbows and the blowpipe. */
	RANGED_DPS(CombatStyle.RANGED, "DPS", "DPS", DamageType.RANGED),
	/** Knives, darts, thrownaxes and chinchompas. */
	RANGED_THROWN(CombatStyle.RANGED, "Thrown", "THROWN", DamageType.RANGED),

	MAGIC_RECOMMENDED(CombatStyle.MAGIC, "Best", null, null),
	MAGIC_POWERED(CombatStyle.MAGIC, "Powered", "POWERED", DamageType.MAGIC),
	MAGIC_STANDARD(CombatStyle.MAGIC, "Standard", "STANDARD", DamageType.MAGIC),
	MAGIC_ANCIENT(CombatStyle.MAGIC, "Ancient", "ANCIENT", DamageType.MAGIC),
	MAGIC_ARCEUUS(CombatStyle.MAGIC, "Arceuus", "ARCEUUS", DamageType.MAGIC);

	private final CombatStyle style;
	private final String label;

	/**
	 * Matches {@code variant} in the bundled tables, or null for the view that
	 * shows the boss's own recommendation.
	 */
	private final String variant;

	/**
	 * The attack type this view is judged on, or null to use whichever type the
	 * style's computed pass found best against the boss.
	 */
	private final DamageType damageType;

	GearView(CombatStyle style, String label, String variant, DamageType damageType)
	{
		this.style = style;
		this.label = label;
		this.variant = variant;
		this.damageType = damageType;
	}

	public boolean isRecommended()
	{
		return variant == null;
	}

	public static List<GearView> of(CombatStyle style)
	{
		final List<GearView> views = new ArrayList<>();
		for (GearView view : values())
		{
			if (view.style == style)
			{
				views.add(view);
			}
		}
		return views;
	}

	public static GearView recommended(CombatStyle style)
	{
		for (GearView view : values())
		{
			if (view.style == style && view.isRecommended())
			{
				return view;
			}
		}
		throw new IllegalArgumentException("No recommended view for " + style);
	}
}
