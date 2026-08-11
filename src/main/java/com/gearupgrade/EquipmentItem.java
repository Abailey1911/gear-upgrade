/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import lombok.Value;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * A single equippable item, with its stats and current Grand Exchange price.
 */
@Value
public class EquipmentItem
{
	int id;
	String name;
	int slot;
	boolean twoHanded;
	ItemEquipmentStats stats;

	/** Live GE price per unit, or 0 if the item is not tradeable. */
	int price;

	/** True if this item is untradeable, so it can never be "bought". */
	boolean untradeable;

	/** True for Ancient Warriors' gear, which only works inside PvP areas. */
	boolean pvpOnly;
}
