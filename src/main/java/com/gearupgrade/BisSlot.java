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
import net.runelite.api.EquipmentInventorySlot;

/**
 * One equipment slot within a best-in-slot setup: the wiki's preferred item
 * followed by its listed alternatives, best first.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BisSlot
{
	/** Name of an {@link EquipmentInventorySlot} constant. */
	private String slot;

	/** Item names, best first. Later entries are accepted downgrades. */
	private List<String> items;

	/**
	 * The same items as stable item ids, resolved at build time and kept in the
	 * same order as {@link #items}.
	 *
	 * <p>Ids are what the plugin matches on. Item names turned out to be a
	 * persistent source of bugs - ornament kits rename from the front
	 * ("Echo virtus robe top"), imbues and kits append suffixes, and several
	 * plausible-looking names are not items at all. An id either exists or it
	 * does not.
	 */
	private List<Integer> ids;

	private String note;

	public List<String> getItems()
	{
		return items == null ? Collections.emptyList() : items;
	}

	public List<Integer> getIds()
	{
		return ids == null ? Collections.emptyList() : ids;
	}

	/**
	 * The slot index used by the equipment container, or -1 if unrecognised.
	 */
	public int slotIndex()
	{
		try
		{
			return EquipmentInventorySlot.valueOf(slot).getSlotIdx();
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			return -1;
		}
	}
}
