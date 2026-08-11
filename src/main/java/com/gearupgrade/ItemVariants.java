/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Cosmetic and imbued equivalents of an item, keyed by item id.
 *
 * <p>Owning any variant counts as owning the base item: an Echo virtus robe
 * top satisfies a table asking for a Virtus robe top, a Necklace of anguish
 * (or) satisfies one asking for the plain necklace.
 *
 * <p>Precomputed at build time rather than matched on names at runtime. Trying
 * to spot these by string pattern failed repeatedly - kits rename from the
 * front, from the back, and sometimes both.
 */
@Slf4j
@Singleton
public class ItemVariants
{
	private static final String RESOURCE = "variants.json";

	/**
	 * One part of a built item, with the quantity the recipe calls for.
	 *
	 * <p>Names are stored rather than looked up, because a component need not be
	 * equipment and so may not be in the index at all.
	 */
	@lombok.Value
	public static class Component
	{
		int id;
		int qty;
		String name;
	}

	private static class Document
	{
		Map<String, List<Integer>> variants;
		Map<String, Integer> tradeable;
		Map<String, List<Component>> components;
	}

	private final Gson gson;
	private Map<String, List<Integer>> variants = Collections.emptyMap();
	private Map<String, Integer> tradeable = Collections.emptyMap();
	private Map<String, List<Component>> components = Collections.emptyMap();

	@Inject
	public ItemVariants(Gson gson)
	{
		this.gson = gson;
	}

	public void load()
	{
		if (!variants.isEmpty())
		{
			return;
		}

		try (InputStream in = ItemVariants.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("Could not find bundled {}", RESOURCE);
				return;
			}

			final Document doc = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), Document.class);

			if (doc != null && doc.variants != null)
			{
				variants = doc.variants;
			}
			if (doc != null && doc.tradeable != null)
			{
				tradeable = doc.tradeable;
			}
			if (doc != null && doc.components != null)
			{
				components = doc.components;
			}

			log.debug("Loaded {} variant groups, {} tradeable forms and {} recipes",
				variants.size(), tradeable.size(), components.size());
		}
		catch (IOException e)
		{
			log.warn("Failed to read {}", RESOURCE, e);
		}
	}

	/**
	 * Ids that count as owning the given item, excluding the item itself.
	 */
	public List<Integer> variantsOf(int itemId)
	{
		final List<Integer> found = variants.get(String.valueOf(itemId));
		return found == null ? Collections.emptyList() : found;
	}

	/**
	 * The buyable form of a charged or degraded item, or 0 if there is none.
	 *
	 * <p>A Tumeken's shadow cannot be bought, but its uncharged form can, so
	 * quoting "not on the GE" is unhelpful when there is a real price to give.
	 */
	public int tradeableFormOf(int itemId)
	{
		final Integer found = tradeable.get(String.valueOf(itemId));
		return found == null ? 0 : found;
	}

	/**
	 * The parts an untradeable item is built from, or empty if it is not made
	 * from anything buyable.
	 *
	 * <p>"Not on the Grand Exchange" is the wrong answer for an Elidinis' ward
	 * (f): the ward, the sigil and the runes are each buyable, so it has a real
	 * price - just not one the item itself carries.
	 */
	public List<Component> componentsOf(int itemId)
	{
		final List<Component> found = components.get(String.valueOf(itemId));
		return found == null ? Collections.emptyList() : found;
	}
}
