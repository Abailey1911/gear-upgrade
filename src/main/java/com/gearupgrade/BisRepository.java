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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Best-in-slot tables, keyed by boss and combat style.
 */
@Slf4j
@Singleton
public class BisRepository
{
	/**
	 * Split across files purely to keep them editable; raids and group-wide
	 * tables live separately from single-boss ones.
	 */
	private static final String[] RESOURCES = {
		"bis.json", "bis-raids.json", "bis-bosses.json", "bis-default.json",
	};

	private final Gson gson;
	private final Map<String, BisSetup> setups = new HashMap<>();
	private final Map<String, BisSetup> groupSetups = new HashMap<>();
	private final Map<String, BisSetup> defaults = new HashMap<>();

	@Inject
	public BisRepository(Gson gson)
	{
		this.gson = gson;
	}

	private static String key(String name, CombatStyle style, String variant)
	{
		return name.toLowerCase(Locale.ROOT) + "|" + style.name()
			+ (variant == null ? "" : "|" + variant.toUpperCase(Locale.ROOT));
	}

	private static String defaultKey(CombatStyle style, String variant)
	{
		return key("", style, variant);
	}

	public void load()
	{
		if (!setups.isEmpty() || !groupSetups.isEmpty() || !defaults.isEmpty())
		{
			return;
		}

		for (String resource : RESOURCES)
		{
			loadResource(resource);
		}

		log.debug("Loaded {} boss, {} group and {} default best-in-slot setups",
			setups.size(), groupSetups.size(), defaults.size());
	}

	private void loadResource(String resource)
	{
		try (InputStream in = BisRepository.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				log.warn("Could not find bundled {}", resource);
				return;
			}

			final Type listType = new TypeToken<List<BisSetup>>()
			{
			}.getType();

			final List<BisSetup> loaded = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), listType);

			if (loaded == null)
			{
				return;
			}

			for (BisSetup setup : loaded)
			{
				final String variant = setup.getVariant();
				if (setup.isDefault())
				{
					defaults.put(defaultKey(setup.combatStyle(), variant), setup);
					continue;
				}
				if (setup.getMonster() != null)
				{
					setups.put(key(setup.getMonster(), setup.combatStyle(), variant), setup);
				}
				if (setup.getGroup() != null)
				{
					groupSetups.put(key(setup.getGroup(), setup.combatStyle(), variant), setup);
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to read {}", resource, e);
		}
	}

	/**
	 * The wiki setup for this boss and style, or null if it has not been
	 * added to the bundled data yet.
	 */
	public BisSetup find(String monster, String group, GearView view)
	{
		final CombatStyle style = view.getStyle();
		final String variant = view.getVariant();

		if (monster != null)
		{
			final BisSetup specific = setups.get(key(monster, style, variant));
			if (specific != null)
			{
				return specific;
			}
		}

		// Then the raid-wide table, which is how the wiki publishes them.
		if (group != null)
		{
			final BisSetup groupSetup = groupSetups.get(key(group, style, variant));
			if (groupSetup != null)
			{
				return groupSetup;
			}
		}

		// Finally the general ladder for the view, so nothing falls through to a
		// computed ranking. A variant view deliberately never borrows the boss's
		// unconstrained table: a crush tab showing the boss's slash setup would
		// be answering a different question.
		return defaults.get(defaultKey(style, variant));
	}
}
