/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Every equippable item in the game, grouped by equipment slot.
 *
 * <p>Built once by walking the item definitions already loaded in the client
 * cache, so no external data source is needed for stats. Prices come from
 * {@link ItemManager}, which tracks the live Grand Exchange feed.
 */
@Slf4j
@Singleton
public class EquipmentIndex
{
	private final Client client;
	private final ItemManager itemManager;

	private final Map<Integer, List<EquipmentItem>> bySlot = new HashMap<>();

	/** Lower-cased item name to item, for resolving names in the BiS tables. */
	private final Map<String, EquipmentItem> byName = new HashMap<>();

	/** Item id to item - the primary lookup, since ids are unambiguous. */
	private final Map<Integer, EquipmentItem> byId = new HashMap<>();

	@Getter
	private volatile boolean built;

	@Inject
	public EquipmentIndex(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/**
	 * Populates the index. Must run on the client thread, because item
	 * compositions are read from the client's cache.
	 */
	public void build()
	{
		if (built)
		{
			return;
		}

		final long start = System.currentTimeMillis();
		final int itemCount = client.getItemCount();
		int found = 0;

		for (int id = 0; id < itemCount; id++)
		{
			// Skip noted and placeholder variants, which share stats with the real item.
			if (itemManager.canonicalize(id) != id)
			{
				continue;
			}

			final ItemStats stats = itemManager.getItemStats(id);
			if (stats == null || !stats.isEquipable())
			{
				continue;
			}

			final ItemEquipmentStats equipment = stats.getEquipment();
			if (equipment == null)
			{
				continue;
			}

			final ItemComposition composition = itemManager.getItemComposition(id);
			final String name = composition.getName();
			if (name == null || name.isEmpty() || "null".equals(name))
			{
				continue;
			}

			final boolean tradeable = composition.isGeTradeable();
			final int price = tradeable ? itemManager.getItemPrice(id) : 0;

			final EquipmentItem item = new EquipmentItem(
				id, name, equipment.getSlot(), equipment.isTwoHanded(),
				equipment, price, !tradeable, PvpOnlyItems.matches(name));

			bySlot.computeIfAbsent(equipment.getSlot(), k -> new ArrayList<>()).add(item);
			byName.putIfAbsent(name.toLowerCase(Locale.ROOT), item);
			byId.put(id, item);
			found++;
		}

		built = true;
		log.debug("Indexed {} equippable items in {}ms", found, System.currentTimeMillis() - start);
	}

	public List<EquipmentItem> forSlot(int slot)
	{
		return Collections.unmodifiableList(bySlot.getOrDefault(slot, Collections.emptyList()));
	}

	/**
	 * Resolves an item name from the BiS tables. Returns null when the name does
	 * not match a real item, which usually means the bundled data needs fixing.
	 */
	public EquipmentItem byName(String name)
	{
		return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
	}

	/**
	 * Primary lookup. Ids are stable and unambiguous, unlike item names.
	 */
	public EquipmentItem byId(int itemId)
	{
		return byId.get(itemId);
	}

	/**
	 * Grand Exchange price of any item, not just equipment.
	 *
	 * <p>The index only holds equippable items, but the parts an item is built
	 * from are often not wearable themselves - an Arcane sigil and ten thousand
	 * soul runes make a fortified ward, and neither goes in a gear slot.
	 */
	public int priceOf(int itemId)
	{
		return itemId <= 0 ? 0 : itemManager.getItemPrice(itemId);
	}

	/**
	 * Suffixes that mark a variant which is cosmetically different but at least
	 * as good as the base item - ornament kits, imbues, locked and trouver
	 * versions. Owning one of these counts as owning the item a best-in-slot
	 * table names.
	 *
	 * <p>Only ever applied in this direction: owning an imbued ring satisfies a
	 * table asking for the plain one, never the reverse.
	 */
	private static final String[] EQUIVALENT_SUFFIXES = {
		" (or)", " (or, i)", " (i)", " (l)", " (t)", " (g)", " (cr)", " (u)",
		" (ornament)", " (deadman)",
	};

	/**
	 * Some cosmetic kits rename the item from the front instead - the Theatre of
	 * Blood purple kits and the whip recolours. Same rule applies: identical
	 * stats, so owning one satisfies a table naming the plain item.
	 */
	private static final String[] EQUIVALENT_PREFIXES = {
		"Holy ", "Sanguine ", "Volcanic ", "Frozen ", "Blessed ", "Echo ", "Gilded ",
	};

	/**
	 * Every item that would satisfy a best-in-slot entry named {@code name},
	 * the exact match first.
	 */
	public List<EquipmentItem> variantsOf(String name)
	{
		if (name == null)
		{
			return Collections.emptyList();
		}

		final List<EquipmentItem> found = new ArrayList<>();

		final EquipmentItem exact = byName(name);
		if (exact != null)
		{
			found.add(exact);
		}

		for (String suffix : EQUIVALENT_SUFFIXES)
		{
			final EquipmentItem variant = byName(name + suffix);
			if (variant != null)
			{
				found.add(variant);
			}
		}

		for (String prefix : EQUIVALENT_PREFIXES)
		{
			final EquipmentItem variant = byName(prefix + name);
			if (variant != null)
			{
				found.add(variant);
			}

			// Kits often apply to an already-suffixed form too, eg. a recoloured
			// and ornamented weapon.
			for (String suffix : EQUIVALENT_SUFFIXES)
			{
				final EquipmentItem both = byName(prefix + name + suffix);
				if (both != null)
				{
					found.add(both);
				}
			}
		}

		// Catch-all for kits the lists above do not know about. Any item in the
		// same slot whose name is this one with a word in front ("Echo virtus
		// robe top") or a parenthesised suffix after it ("Toxic blowpipe (or)")
		// is the same equipment under a cosmetic name. Restricted to the same
		// slot so unrelated items cannot match.
		if (exact != null)
		{
			final String lower = name.toLowerCase(Locale.ROOT);
			for (EquipmentItem candidate : forSlot(exact.getSlot()))
			{
				if (candidate.getId() == exact.getId() || candidate.getName() == null)
				{
					continue;
				}

				final String other = candidate.getName().toLowerCase(Locale.ROOT);
				if ((other.endsWith(' ' + lower) || other.startsWith(lower + " ("))
					&& !found.contains(candidate))
				{
					found.add(candidate);
				}
			}
		}

		return found;
	}

	public void clear()
	{
		bySlot.clear();
		byName.clear();
		built = false;
	}
}
