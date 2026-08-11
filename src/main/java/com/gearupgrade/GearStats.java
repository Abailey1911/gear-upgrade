/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import lombok.Getter;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Running total of the equipment bonuses for a whole loadout.
 *
 * <p>This is the same set of numbers the in-game equipment stats screen shows,
 * summed across every worn slot.
 */
@Getter
public class GearStats
{
	/** Attack speed of a bare-handed attack, in game ticks. */
	private static final int UNARMED_SPEED = 4;

	private int astab;
	private int aslash;
	private int acrush;
	private int amagic;
	private int arange;

	private int dstab;
	private int dslash;
	private int dcrush;
	private int dmagic;
	private int drange;

	private int str;
	private int rstr;
	private float mdmg;
	private int prayer;

	private int attackSpeed = UNARMED_SPEED;

	/** Set-bonus multipliers, eg. crystal armour with a crystal bow. */
	private double accuracyMultiplier = 1.0d;
	private double damageMultiplier = 1.0d;

	void applyMultipliers(double accuracy, double damage)
	{
		this.accuracyMultiplier *= accuracy;
		this.damageMultiplier *= damage;
	}

	public void add(ItemEquipmentStats s)
	{
		if (s == null)
		{
			return;
		}

		astab += s.getAstab();
		aslash += s.getAslash();
		acrush += s.getAcrush();
		amagic += s.getAmagic();
		arange += s.getArange();

		dstab += s.getDstab();
		dslash += s.getDslash();
		dcrush += s.getDcrush();
		dmagic += s.getDmagic();
		drange += s.getDrange();

		str += s.getStr();
		rstr += s.getRstr();
		mdmg += s.getMdmg();
		prayer += s.getPrayer();

		// Only the weapon carries a meaningful attack speed.
		if (s.getAspeed() > 0)
		{
			attackSpeed = s.getAspeed();
		}
	}

	public void remove(ItemEquipmentStats s)
	{
		if (s == null)
		{
			return;
		}

		astab -= s.getAstab();
		aslash -= s.getAslash();
		acrush -= s.getAcrush();
		amagic -= s.getAmagic();
		arange -= s.getArange();

		dstab -= s.getDstab();
		dslash -= s.getDslash();
		dcrush -= s.getDcrush();
		dmagic -= s.getDmagic();
		drange -= s.getDrange();

		str -= s.getStr();
		rstr -= s.getRstr();
		mdmg -= s.getMdmg();
		prayer -= s.getPrayer();
	}

	public GearStats copy()
	{
		GearStats c = new GearStats();
		c.astab = astab;
		c.aslash = aslash;
		c.acrush = acrush;
		c.amagic = amagic;
		c.arange = arange;
		c.dstab = dstab;
		c.dslash = dslash;
		c.dcrush = dcrush;
		c.dmagic = dmagic;
		c.drange = drange;
		c.str = str;
		c.rstr = rstr;
		c.mdmg = mdmg;
		c.prayer = prayer;
		c.attackSpeed = attackSpeed;
		return c;
	}

	void setAttackSpeed(int ticks)
	{
		this.attackSpeed = ticks;
	}
}
