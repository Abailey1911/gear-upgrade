/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.util.LinkedHashMap;
import java.util.Map;

import net.runelite.api.EquipmentInventorySlot;

/**
 * A set of items occupying equipment slots, and the summed stats that result.
 */
public class Loadout
{
	/** Slots that carry offensive stats worth optimising. */
	static final int[] SLOTS = {
		EquipmentInventorySlot.HEAD.getSlotIdx(),
		EquipmentInventorySlot.CAPE.getSlotIdx(),
		EquipmentInventorySlot.AMULET.getSlotIdx(),
		EquipmentInventorySlot.WEAPON.getSlotIdx(),
		EquipmentInventorySlot.BODY.getSlotIdx(),
		EquipmentInventorySlot.SHIELD.getSlotIdx(),
		EquipmentInventorySlot.LEGS.getSlotIdx(),
		EquipmentInventorySlot.GLOVES.getSlotIdx(),
		EquipmentInventorySlot.BOOTS.getSlotIdx(),
		EquipmentInventorySlot.RING.getSlotIdx(),
		EquipmentInventorySlot.AMMO.getSlotIdx(),
	};

	private static final int WEAPON_SLOT = EquipmentInventorySlot.WEAPON.getSlotIdx();
	private static final int SHIELD_SLOT = EquipmentInventorySlot.SHIELD.getSlotIdx();

	private final Map<Integer, EquipmentItem> items = new LinkedHashMap<>();

	public EquipmentItem get(int slot)
	{
		return items.get(slot);
	}

	/**
	 * Places an item, honouring the two-handed rule: a two-handed weapon clears
	 * the shield slot, and equipping a shield clears a two-handed weapon.
	 */
	public void put(int slot, EquipmentItem item)
	{
		if (item == null)
		{
			items.remove(slot);
			return;
		}

		items.put(slot, item);

		if (slot == WEAPON_SLOT && item.isTwoHanded())
		{
			items.remove(SHIELD_SLOT);
		}
		else if (slot == SHIELD_SLOT)
		{
			final EquipmentItem weapon = items.get(WEAPON_SLOT);
			if (weapon != null && weapon.isTwoHanded())
			{
				items.remove(WEAPON_SLOT);
			}
		}
	}

	private static final int HEAD_SLOT = EquipmentInventorySlot.HEAD.getSlotIdx();
	private static final int BODY_SLOT = EquipmentInventorySlot.BODY.getSlotIdx();
	private static final int LEGS_SLOT = EquipmentInventorySlot.LEGS.getSlotIdx();
	private static final int AMMO_SLOT = EquipmentInventorySlot.AMMO.getSlotIdx();

	public GearStats stats()
	{
		final GearStats stats = new GearStats();
		final EquipmentItem weapon = items.get(WEAPON_SLOT);

		for (Map.Entry<Integer, EquipmentItem> entry : items.entrySet())
		{
			// Ammunition only counts when the weapon actually fires it -
			// javelins do nothing for thrown knives or a crystal bow.
			if (entry.getKey() == AMMO_SLOT
				&& !WeaponRules.ammoCounts(weapon, entry.getValue()))
			{
				continue;
			}

			stats.add(entry.getValue().getStats());
		}

		WeaponRules.applyCrystalSetBonus(stats, items, HEAD_SLOT, BODY_SLOT, LEGS_SLOT,
			WEAPON_SLOT);

		return stats;
	}

	public Loadout copy()
	{
		final Loadout copy = new Loadout();
		copy.items.putAll(items);
		return copy;
	}
}
