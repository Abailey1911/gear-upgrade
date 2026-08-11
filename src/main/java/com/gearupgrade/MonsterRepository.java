/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the bundled monster defence profiles.
 *
 * <p>The data ships as a resource so the plugin makes no network calls of its
 * own; extending the list is a matter of editing monsters.json.
 */
@Slf4j
@Singleton
public class MonsterRepository
{
	private static final String RESOURCE = "monsters.json";
	private static final String REFERENCE_GROUP = "Reference";

	private final Gson gson;
	private final Map<String, Monster> monsters = new LinkedHashMap<>();

	@Inject
	public MonsterRepository(Gson gson)
	{
		this.gson = gson;
	}

	public void load()
	{
		if (!monsters.isEmpty())
		{
			return;
		}

		try (InputStream in = MonsterRepository.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("Could not find bundled {}", RESOURCE);
				return;
			}

			final Type listType = new TypeToken<List<Monster>>()
			{
			}.getType();

			final List<Monster> loaded = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), listType);

			if (loaded == null)
			{
				return;
			}

			for (Monster monster : loaded)
			{
				monsters.put(monster.getName(), monster);
			}

			log.debug("Loaded {} monster profiles", monsters.size());
		}
		catch (IOException e)
		{
			log.warn("Failed to read {}", RESOURCE, e);
		}
	}

	public List<String> names()
	{
		return Collections.unmodifiableList(new ArrayList<>(monsters.keySet()));
	}

	/**
	 * Difficulty order, not alphabetical - this is the order players progress
	 * through content, so it is the order the selector should offer.
	 */
	private static final List<String> TIER_ORDER =
		Arrays.asList("Reference", "Easy", "Medium", "Hard", "Elite", "Master");

	/**
	 * Tiers that actually have bosses loaded, in difficulty order.
	 */
	public List<String> tiers()
	{
		final Set<String> present = new HashSet<>();
		for (Monster monster : monsters.values())
		{
			if (monster.getTier() != null)
			{
				present.add(monster.getTier());
			}
		}

		final List<String> ordered = new ArrayList<>();
		for (String tier : TIER_ORDER)
		{
			if (present.remove(tier))
			{
				ordered.add(tier);
			}
		}
		// Anything not in the known order still gets offered, alphabetically.
		final List<String> leftovers = new ArrayList<>(present);
		leftovers.sort(String.CASE_INSENSITIVE_ORDER);
		ordered.addAll(leftovers);

		return ordered;
	}

	/**
	 * Content groups within a tier, alphabetically. A group may be a raid, a
	 * cluster like Barrows or the Wilderness, or a single boss.
	 */
	public List<String> groupsInTier(String tier)
	{
		final Set<String> unique = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (Monster monster : monsters.values())
		{
			if (tier != null && tier.equals(monster.getTier()) && monster.getGroup() != null)
			{
				unique.add(monster.getGroup());
			}
		}
		return new ArrayList<>(unique);
	}

	/**
	 * Bosses within a tier and group, alphabetically. A raid returns each of its
	 * bosses; a standalone boss returns just itself.
	 */
	public List<String> namesInGroup(String tier, String group)
	{
		final List<String> names = new ArrayList<>();
		for (Monster monster : monsters.values())
		{
			final boolean tierMatches = tier == null || tier.equals(monster.getTier());
			if (tierMatches && monster.getGroup() != null && monster.getGroup().equals(group))
			{
				names.add(monster.getName());
			}
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public Monster byName(String name)
	{
		return monsters.get(name);
	}

	public boolean isEmpty()
	{
		return monsters.isEmpty();
	}
}
