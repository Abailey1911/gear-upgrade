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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Skill requirements for equipping items.
 *
 * <p>Deliberately fails open: an item with no recorded requirement is treated
 * as wearable rather than hidden. The bundled data comes from a snapshot that
 * predates a lot of current endgame gear, and wrongly hiding a Torva platebody
 * would be far worse than occasionally listing something out of reach.
 */
@Slf4j
@Singleton
public class ItemRequirements
{
	private static final String RESOURCE = "requirements.json";

	private static class Document
	{
		Map<String, Map<String, Integer>> byId;
		Map<String, Map<String, Integer>> byName;
	}

	private final Gson gson;

	private Map<String, Map<String, Integer>> byId = Collections.emptyMap();
	private Map<String, Map<String, Integer>> byName = Collections.emptyMap();

	@Inject
	public ItemRequirements(Gson gson)
	{
		this.gson = gson;
	}

	public void load()
	{
		if (!byId.isEmpty() || !byName.isEmpty())
		{
			return;
		}

		try (InputStream in = ItemRequirements.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("Could not find bundled {}", RESOURCE);
				return;
			}

			final Document doc = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), Document.class);

			if (doc == null)
			{
				return;
			}

			byId = doc.byId == null ? Collections.emptyMap() : doc.byId;

			// Lower-case the name keys once so lookups do not have to.
			final Map<String, Map<String, Integer>> names = new LinkedHashMap<>();
			if (doc.byName != null)
			{
				for (Map.Entry<String, Map<String, Integer>> e : doc.byName.entrySet())
				{
					names.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
				}
			}
			byName = names;

			log.debug("Loaded {} requirement entries by id and {} by name",
				byId.size(), byName.size());
		}
		catch (IOException e)
		{
			log.warn("Failed to read {}", RESOURCE, e);
		}
	}

	/**
	 * The requirements for an item, or null when none are recorded.
	 */
	@Nullable
	private Map<String, Integer> forItem(EquipmentItem item)
	{
		final Map<String, Integer> byIdHit = byId.get(String.valueOf(item.getId()));
		if (byIdHit != null)
		{
			return byIdHit;
		}
		return item.getName() == null
			? null
			: byName.get(item.getName().toLowerCase(Locale.ROOT));
	}

	/**
	 * True when the player meets every recorded requirement, and also true when
	 * nothing is recorded at all.
	 */
	public boolean canEquip(EquipmentItem item, PlayerLevels levels)
	{
		final Map<String, Integer> reqs = forItem(item);
		if (reqs == null)
		{
			return true;
		}

		for (Map.Entry<String, Integer> req : reqs.entrySet())
		{
			if (levelFor(req.getKey(), levels) < req.getValue())
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * "82 Attack" style summary of what is missing, or null if nothing is.
	 */
	@Nullable
	public String describeShortfall(EquipmentItem item, PlayerLevels levels)
	{
		final Map<String, Integer> reqs = forItem(item);
		if (reqs == null)
		{
			return null;
		}

		final StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Integer> req : reqs.entrySet())
		{
			if (levelFor(req.getKey(), levels) < req.getValue())
			{
				if (sb.length() > 0)
				{
					sb.append(", ");
				}
				sb.append(req.getValue()).append(' ').append(capitalise(req.getKey()));
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	/**
	 * Skills not tracked here (Prayer, Slayer, Agility and so on) return a very
	 * high level so they never gate anything - failing open again.
	 */
	private static int levelFor(String skill, PlayerLevels levels)
	{
		switch (skill.toLowerCase(Locale.ROOT))
		{
			case "attack":
				return levels.getAttack();
			case "strength":
				return levels.getStrength();
			case "defence":
				return levels.getDefence();
			case "ranged":
				return levels.getRanged();
			case "magic":
				return levels.getMagic();
			default:
				return Integer.MAX_VALUE;
		}
	}

	private static String capitalise(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
