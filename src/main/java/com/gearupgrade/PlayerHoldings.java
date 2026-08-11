/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;

/**
 * What the local player owns and can spend: every item held in the bank,
 * inventory and equipment, plus their coins.
 *
 * <p>The bank container is only populated while the bank interface has been
 * opened, so the contents are cached from the last time it was seen.
 */
@Slf4j
@Singleton
public class PlayerHoldings
{
	private final Client client;
	private final ItemManager itemManager;

	/**
	 * Canonical item ids per container. Kept split so that re-reading one
	 * container replaces its contents instead of accumulating items the player
	 * has since sold or dropped.
	 */
	private final Map<Integer, Set<Integer>> byContainer = new HashMap<>();

	/**
	 * Flattened union of {@link #byContainer}, so ownership tests are a single
	 * hash lookup. Rebuilt whenever a container changes.
	 */
	private final Set<Integer> owned = new HashSet<>();

	/** Coins held per container, so a refresh replaces rather than double-counts. */
	private final Map<Integer, Long> coinsByContainer = new HashMap<>();

	@Getter
	private long coins;

	/** False until the bank has been opened at least once this session. */
	@Getter
	private boolean bankSeen;

	/**
	 * Bumped on every change, so callers can cache derived data and cheaply
	 * detect when it has gone stale.
	 */
	@Getter
	private int version;

	@Inject
	public PlayerHoldings(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/**
	 * Ownership test for an id that is already canonical - as every id in
	 * {@link EquipmentIndex} is. Avoids a per-call cache lookup, which matters
	 * because this runs tens of thousands of times per analysis.
	 */
	public boolean ownsCanonical(int canonicalItemId)
	{
		return owned.contains(canonicalItemId);
	}

	/**
	 * False before any container has been read - at login the containers are
	 * briefly empty, and treating that as "owns nothing" produces a misleading
	 * burst of empty slots.
	 */
	public boolean hasAnything()
	{
		return !owned.isEmpty();
	}

	public boolean owns(int itemId)
	{
		return owned.contains(itemManager.canonicalize(itemId));
	}

	/**
	 * Re-reads the given container. Must run on the client thread.
	 */
	public void refresh(int containerId)
	{
		final ItemContainer container = client.getItemContainer(containerId);
		if (container == null)
		{
			return;
		}

		if (containerId == InventoryID.BANK)
		{
			bankSeen = true;
		}

		final Set<Integer> items = new HashSet<>();
		long coinsHere = 0;
		int placeholdersSkipped = 0;

		for (Item item : container.getItems())
		{
			final int id = item.getId();
			if (id <= 0)
			{
				continue;
			}

			// Bank placeholders canonicalise to the real item, so counting them
			// reports gear the player no longer has. The placeholder template id
			// is the definitive test: -1 on a genuine item, set on a placeholder.
			//
			// Quantity is deliberately NOT used. A placeholder sits at 0 because
			// the item was withdrawn - it may well be in the inventory or worn
			// right now, and those containers are read too, so a genuinely held
			// item is still counted. Filtering on quantity risked dropping
			// equipped gear that reports 0.
			final ItemComposition composition = itemManager.getItemComposition(id);
			if (composition.getPlaceholderTemplateId() > -1)
			{
				placeholdersSkipped++;
				continue;
			}

			final int canonical = itemManager.canonicalize(id);
			items.add(canonical);

			if (canonical == ItemID.COINS)
			{
				coinsHere += item.getQuantity();
			}
		}

		// Only worth logging where placeholders actually occur - the inventory
		// changes constantly and never has any.
		if (placeholdersSkipped > 0 || containerId == InventoryID.BANK)
		{
			log.debug("Container {}: {} real items, {} placeholders ignored",
				containerId, items.size(), placeholdersSkipped);
		}

		byContainer.put(containerId, items);

		// Coins live in the bank and inventory; worn gear never holds any.
		if (containerId == InventoryID.BANK || containerId == InventoryID.INV)
		{
			coinsByContainer.put(containerId, coinsHere);
			coins = coinsByContainer.values().stream().mapToLong(Long::longValue).sum();
		}

		rebuildOwned();
		version++;
	}

	private void rebuildOwned()
	{
		owned.clear();
		for (Set<Integer> items : byContainer.values())
		{
			owned.addAll(items);
		}
	}

	public void clear()
	{
		byContainer.clear();
		coinsByContainer.clear();
		owned.clear();
		coins = 0;
		bankSeen = false;
		version++;
	}
}
