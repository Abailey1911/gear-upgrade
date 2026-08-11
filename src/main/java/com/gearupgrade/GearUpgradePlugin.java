/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Gear Upgrade",
	description = "Ranks the best gear upgrade you can afford per combat style for a chosen boss",
	tags = {"gear", "upgrade", "bis", "dps", "bank", "ge", "boss", "equipment"}
)
public class GearUpgradePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private GearUpgradeConfig config;

	@Inject
	private EquipmentIndex equipmentIndex;

	@Inject
	private PlayerHoldings holdings;

	@Inject
	private UpgradeFinder upgradeFinder;

	@Inject
	private MonsterRepository monsters;

	@Inject
	private BisRepository bisRepository;

	@Inject
	private BisAnalyser bisAnalyser;

	@Inject
	private ItemRequirements itemRequirements;

	@Inject
	private ItemVariants itemVariants;

	@Inject
	private ItemManager itemManager;

	private GearUpgradePanel panel;
	private NavigationButton navButton;

	/** Tier holding the no-defence dummy, used as the opening view. */
	private static final String REFERENCE_TIER = "Reference";

	private String selectedTier;
	private String selectedGroup;
	private String selectedMonster;
	private boolean dirty;

	/** Captured on each recompute so the Swing callback can reuse them. */
	private PlayerLevels lastLevels;
	private Monster lastMonster;

	/**
	 * Narrows the group list to the chosen tier and picks the first entry, so
	 * the three selectors always stay consistent with one another.
	 */
	private void selectFirstGroupInTier()
	{
		final List<String> groups = monsters.groupsInTier(selectedTier);
		selectedGroup = groups.isEmpty() ? null : groups.get(0);
		panel.setGroups(groups, selectedGroup);
		selectFirstMonsterInGroup();
	}

	private void selectFirstMonsterInGroup()
	{
		final List<String> inGroup = monsters.namesInGroup(selectedTier, selectedGroup);
		selectedMonster = inGroup.isEmpty() ? null : inGroup.get(0);
		panel.setMonsters(inGroup, selectedMonster);
	}

	/**
	 * Containers that changed since the last tick. Opening a bank fires a burst
	 * of these, and re-reading the container on each one is what made the client
	 * stutter, so the reads are coalesced to one per tick.
	 */
	private final Set<Integer> pendingContainers = new HashSet<>();

	@Override
	protected void startUp()
	{
		monsters.load();
		bisRepository.load();
		itemRequirements.load();
		itemVariants.load();

		panel = new GearUpgradePanel(itemManager);
		panel.setOnMonsterSelected(name ->
		{
			selectedMonster = name;
			dirty = true;
		});
		panel.setOnTierSelected(tier ->
		{
			selectedTier = tier;
			selectFirstGroupInTier();
			dirty = true;
		});
		panel.setOnGroupSelected(group ->
		{
			selectedGroup = group;
			selectFirstMonsterInGroup();
			dirty = true;
		});

		final List<String> tiers = monsters.tiers();
		if (!tiers.isEmpty())
		{
			// Open on the Reference entry, which ranks gear on raw stats against a
			// target with no defence at all. That is the "what is my best gear"
			// answer, and it is what you want before picking a boss - opening on a
			// specific boss implies its quirks apply to everything.
			selectedTier = tiers.contains(REFERENCE_TIER)
				? REFERENCE_TIER
				: tiers.get(tiers.size() - 1);
			panel.setTiers(tiers, selectedTier);
			selectFirstGroupInTier();
		}

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Gear Upgrade")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::indexAndRefresh);
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;

		equipmentIndex.clear();
		holdings.clear();
		pendingContainers.clear();
		dirty = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::indexAndRefresh);
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			holdings.clear();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		final int id = event.getContainerId();
		if (id != InventoryID.BANK && id != InventoryID.INV && id != InventoryID.WORN)
		{
			return;
		}

		pendingContainers.add(id);
		dirty = true;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GearUpgradeConfig.GROUP.equals(event.getGroup()))
		{
			dirty = true;
		}
	}

	/**
	 * Recomputes at most once per tick, so a burst of container updates does not
	 * trigger a burst of analyses.
	 */
	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!dirty)
		{
			return;
		}
		dirty = false;

		for (int containerId : pendingContainers)
		{
			holdings.refresh(containerId);
		}
		pendingContainers.clear();

		recompute();
	}

	private void indexAndRefresh()
	{
		equipmentIndex.build();
		pendingContainers.add(InventoryID.INV);
		pendingContainers.add(InventoryID.WORN);
		pendingContainers.add(InventoryID.BANK);
		dirty = true;
	}

	/**
	 * Runs on the client thread: reads levels and holdings, then hands finished
	 * results to Swing.
	 */
	private void recompute()
	{
		if (panel == null || selectedMonster == null || !equipmentIndex.isBuilt())
		{
			return;
		}

		final Monster monster = monsters.byName(selectedMonster);
		if (monster == null)
		{
			return;
		}

		// Some content strips your equipment entirely, so there is nothing to rank.
		if (!monster.isGearApplicable())
		{
			final String reason = monster.getGearNotApplicableReason();
			SwingUtilities.invokeLater(() ->
			{
				if (panel == null)
				{
					return;
				}
				panel.setBudget(config.budgetOverride() > 0
					? config.budgetOverride() : holdings.getCoins(), false, holdings.isBankSeen());
				panel.setStatus("");
				panel.setNotes(java.util.Collections.emptyList());
				for (CombatStyle style : CombatStyle.values())
				{
					panel.showGearNotApplicable(style, reason);
				}
			});
			return;
		}

		final PlayerLevels levels = PlayerLevels.from(client);
		lastLevels = levels;
		lastMonster = monster;
		final PrayerBoost prayer = config.prayerBoost();
		final int baseSpellMaxHit = config.baseSpellMaxHit();
		final int maxSuggestions = config.maxSuggestions();

		final long override = config.budgetOverride();
		final long budget = override > 0 ? override : holdings.getCoins();
		final boolean bankSeen = holdings.isBankSeen();

		final long start = System.nanoTime();

		final StyleResult melee = analyseMelee(monster, levels, prayer, baseSpellMaxHit,
			maxSuggestions, budget);
		final StyleResult ranged = analyse(DamageType.RANGED, monster, levels, prayer,
			baseSpellMaxHit, maxSuggestions, budget);
		final StyleResult magic = analyse(DamageType.MAGIC, monster, levels, prayer,
			baseSpellMaxHit, maxSuggestions, budget);

		log.debug("Analysis for {} took {}ms", selectedMonster,
			(System.nanoTime() - start) / 1_000_000);

		// Wiki best-in-slot tables are the authority where they exist; the
		// computed ranking is only a fallback for bosses not yet covered.
		final Map<CombatStyle, BisAnalyser.Result> bisResults = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.values())
		{
			final BisSetup setup = bisRepository.find(selectedMonster, monster.getGroup(), style);
			if (setup != null)
			{
				// Use the attack type the computed pass found best for this style,
				// so melee is judged on stab/slash/crush as appropriate.
				final DamageType type = style == CombatStyle.MELEE ? melee.type
					: style == CombatStyle.RANGED ? ranged.type : magic.type;

				bisResults.put(style, bisAnalyser.analyse(setup, budget, bankSeen, levels,
					monster, type, prayer, baseSpellMaxHit));
			}
		}

		SwingUtilities.invokeLater(() ->
		{
			if (panel == null)
			{
				return;
			}

			panel.setBudget(budget, override > 0, bankSeen);
			panel.setStatus(bankSeen
				? ""
				: "Bank not read yet - open your bank once. Until then this only sees "
					+ "worn and inventory items, so gear you own may show as missing "
					+ "and prices are not compared against your real coins.");
			panel.setNotes(monster.allNotes());

			showStyle(CombatStyle.MELEE, melee, bisResults, budget);
			showStyle(CombatStyle.RANGED, ranged, bisResults, budget);
			showStyle(CombatStyle.MAGIC, magic, bisResults, budget);

			// The style with the best DPS is the one worth opening on.
			CombatStyle best = CombatStyle.MELEE;
			double bestDps = melee.analysis.getCurrentDps();
			if (ranged.analysis.getCurrentDps() > bestDps)
			{
				best = CombatStyle.RANGED;
				bestDps = ranged.analysis.getCurrentDps();
			}
			if (magic.analysis.getCurrentDps() > bestDps)
			{
				best = CombatStyle.MAGIC;
			}
			panel.setBestStyle(best);
		});
	}

	private void showStyle(CombatStyle style, StyleResult computed,
						   Map<CombatStyle, BisAnalyser.Result> bisResults, long budget)
	{
		// Some styles simply are not used at some bosses. Showing a full setup
		// implies it is a choice when it is not.
		if (lastMonster != null && !lastMonster.isStyleViable(style))
		{
			final String reason = lastMonster.getStyleRestriction() != null
				? lastMonster.getStyleRestriction()
				: "This style is not used at this boss.";
			panel.showGearNotApplicable(style, reason);
			return;
		}

		final BisAnalyser.Result bis = bisResults.get(style);

		panel.showComputed(style, computed.analysis, computed.type, bis != null);

		if (bis != null)
		{
			panel.showBis(style, bis, budget);

			// Measure the summary from the gear they would actually wear here,
			// rather than from the computed loadout shown when there is no table.
			final DpsCalculator.Result dps = DpsCalculator.compute(
				bis.getWornFromBis().stats(), lastLevels, lastMonster, computed.type,
				config.prayerBoost(), config.baseSpellMaxHit());
			panel.showBisSummary(style, dps, computed.type);
		}
	}

	/**
	 * Melee has three attack types and the monster's defence against each can
	 * differ a lot, so the best of the three is what the tab reports.
	 */
	private StyleResult analyseMelee(Monster monster, PlayerLevels levels, PrayerBoost prayer,
									 int baseSpellMaxHit, int maxSuggestions, long budget)
	{
		StyleResult best = null;
		for (DamageType type : new DamageType[]{DamageType.STAB, DamageType.SLASH, DamageType.CRUSH})
		{
			final StyleResult result = analyse(type, monster, levels, prayer, baseSpellMaxHit,
				maxSuggestions, budget);
			if (best == null || result.analysis.getCurrentDps() > best.analysis.getCurrentDps())
			{
				best = result;
			}
		}
		return best;
	}

	private StyleResult analyse(DamageType type, Monster monster, PlayerLevels levels,
								PrayerBoost prayer, int baseSpellMaxHit, int maxSuggestions,
								long budget)
	{
		return new StyleResult(type, upgradeFinder.analyse(type, monster, levels, prayer,
			baseSpellMaxHit, maxSuggestions, budget));
	}

	private static class StyleResult
	{
		private final DamageType type;
		private final UpgradeFinder.Analysis analysis;

		StyleResult(DamageType type, UpgradeFinder.Analysis analysis)
		{
			this.type = type;
			this.analysis = analysis;
		}
	}

	@Provides
	GearUpgradeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GearUpgradeConfig.class);
	}
}
