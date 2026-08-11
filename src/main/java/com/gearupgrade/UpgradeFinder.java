/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/**
 * Works out the best loadout the player already owns, then ranks the items
 * that would improve on it - split into what they can afford right now and
 * what they would need to save up for.
 */
@Singleton
public class UpgradeFinder
{
	/** Greedy passes over the slots; the loadout settles well before this. */
	private static final int CONVERGENCE_PASSES = 3;

	/** DPS values closer than this are treated as a tie. */
	private static final double DPS_EPSILON = 1e-9;

	private final EquipmentIndex index;
	private final PlayerHoldings holdings;
	private final ItemRequirements requirements;

	/**
	 * Owned items per slot, cached against the holdings version. Without this,
	 * every analysis re-scans all ~5,600 equippable items per slot.
	 */
	private final Map<Integer, List<EquipmentItem>> ownedBySlot = new HashMap<>();
	private int ownedCacheVersion = -1;

	@Inject
	public UpgradeFinder(EquipmentIndex index, PlayerHoldings holdings,
						 ItemRequirements requirements)
	{
		this.index = index;
		this.holdings = holdings;
		this.requirements = requirements;
	}

	@Value
	public static class Analysis
	{
		Loadout currentBest;
		double currentDps;
		int maxHit;
		double accuracy;

		/** Upgrades within budget, best gain first. */
		List<UpgradeSuggestion> affordable;

		/** Upgrades beyond budget, best gain first. */
		List<UpgradeSuggestion> aspirational;
	}

	public Analysis analyse(DamageType type, Monster monster, PlayerLevels levels,
							PrayerBoost prayer, int baseSpellMaxHit, int maxSuggestions,
							long budget)
	{
		refreshOwnedCache();

		final Loadout best = bestOwnedLoadout(type, monster, levels, prayer, baseSpellMaxHit);
		final DpsCalculator.Result baseline =
			DpsCalculator.compute(best.stats(), levels, monster, type, prayer, baseSpellMaxHit);

		final List<UpgradeSuggestion> affordable = new ArrayList<>();
		final List<UpgradeSuggestion> aspirational = new ArrayList<>();

		collectUpgrades(best, baseline.getDps(), type, monster, levels, prayer, baseSpellMaxHit,
			budget, affordable, aspirational);

		final Comparator<UpgradeSuggestion> byGain =
			Comparator.comparingDouble(UpgradeSuggestion::getDpsGain).reversed();
		affordable.sort(byGain);
		aspirational.sort(byGain);

		return new Analysis(
			best,
			baseline.getDps(),
			baseline.getMaxHit(),
			baseline.getAccuracy(),
			affordable.subList(0, Math.min(maxSuggestions, affordable.size())),
			aspirational.subList(0, Math.min(maxSuggestions, aspirational.size())));
	}

	/**
	 * Rebuilds the owned-item lists if the player's holdings have changed.
	 */
	private void refreshOwnedCache()
	{
		if (ownedCacheVersion == holdings.getVersion())
		{
			return;
		}

		ownedBySlot.clear();
		for (int slot : Loadout.SLOTS)
		{
			final List<EquipmentItem> mine = new ArrayList<>();
			for (EquipmentItem item : index.forSlot(slot))
			{
				// Ancient Warriors' gear cannot be used at a boss at all.
				if (item.isPvpOnly())
				{
					continue;
				}
				if (holdings.ownsCanonical(item.getId()))
				{
					mine.add(item);
				}
			}
			ownedBySlot.put(slot, mine);
		}

		ownedCacheVersion = holdings.getVersion();
	}

	/**
	 * Greedily fills each slot with the owned item that maximises DPS, repeating
	 * so that later choices (a two-handed weapon, say) can be reconsidered.
	 */
	private Loadout bestOwnedLoadout(DamageType type, Monster monster, PlayerLevels levels,
									 PrayerBoost prayer, int baseSpellMaxHit)
	{
		final Loadout loadout = new Loadout();

		for (int pass = 0; pass < CONVERGENCE_PASSES; pass++)
		{
			for (int slot : Loadout.SLOTS)
			{
				EquipmentItem bestItem = loadout.get(slot);
				double bestDps = dpsOf(loadout, type, monster, levels, prayer, baseSpellMaxHit);
				int bestScore = tieBreakScore(bestItem, type);

				for (EquipmentItem candidate : ownedBySlot.getOrDefault(slot, List.of()))
				{
					final Loadout trial = loadout.copy();
					trial.put(slot, candidate);
					final double dps = dpsOf(trial, type, monster, levels, prayer, baseSpellMaxHit);
					final int score = tieBreakScore(candidate, type);

					// Many slots contribute nothing to a given style, leaving a
					// large tie. Picking the first match makes the setup look
					// random (ranged legs in a melee loadout), so ties fall back
					// to the more useful piece.
					final boolean better = dps > bestDps + DPS_EPSILON
						|| (Math.abs(dps - bestDps) <= DPS_EPSILON && score > bestScore);

					if (better)
					{
						bestDps = dps;
						bestScore = score;
						bestItem = candidate;
					}
				}

				if (bestItem != null)
				{
					loadout.put(slot, bestItem);
				}
			}
		}

		return loadout;
	}

	/**
	 * Every tradeable item the player does not own that beats what is currently
	 * in its slot, bucketed by whether they can pay for it today.
	 */
	private void collectUpgrades(Loadout current, double currentDps, DamageType type,
								 Monster monster, PlayerLevels levels, PrayerBoost prayer,
								 int baseSpellMaxHit, long budget,
								 List<UpgradeSuggestion> affordable,
								 List<UpgradeSuggestion> aspirational)
	{
		for (int slot : Loadout.SLOTS)
		{
			for (EquipmentItem candidate : index.forSlot(slot))
			{
				if (candidate.isUntradeable() || candidate.getPrice() <= 0)
				{
					continue;
				}
				if (candidate.isPvpOnly())
				{
					continue;
				}
				if (holdings.ownsCanonical(candidate.getId()))
				{
					continue;
				}
				// No point recommending something they cannot wear.
				if (!requirements.canEquip(candidate, levels))
				{
					continue;
				}

				final Loadout trial = current.copy();
				trial.put(slot, candidate);
				final double dps = dpsOf(trial, type, monster, levels, prayer, baseSpellMaxHit);

				if (dps <= currentDps)
				{
					continue;
				}

				final long cost = candidate.getPrice();
				final UpgradeSuggestion suggestion = new UpgradeSuggestion(
					candidate, current.get(slot), cost, currentDps, dps,
					Math.max(0, cost - budget));

				if (cost <= budget)
				{
					affordable.add(suggestion);
				}
				else
				{
					aspirational.add(suggestion);
				}
			}
		}
	}

	/**
	 * Ranks otherwise-equal items so the chosen setup looks sensible: offensive
	 * bonus for the style in use first, then general defensive value.
	 */
	private static int tieBreakScore(EquipmentItem item, DamageType type)
	{
		if (item == null)
		{
			return -1;
		}

		final GearStats stats = new GearStats();
		stats.add(item.getStats());

		final int offence;
		switch (type.getStyle())
		{
			case RANGED:
				offence = type.attackBonus(stats) + stats.getRstr();
				break;
			case MAGIC:
				offence = type.attackBonus(stats) + (int) stats.getMdmg();
				break;
			case MELEE:
			default:
				offence = type.attackBonus(stats) + stats.getStr();
				break;
		}

		final int defence = stats.getDstab() + stats.getDslash() + stats.getDcrush()
			+ stats.getDmagic() + stats.getDrange();

		return offence * 1000 + defence + stats.getPrayer();
	}

	private double dpsOf(Loadout loadout, DamageType type, Monster monster,
						 PlayerLevels levels, PrayerBoost prayer, int baseSpellMaxHit)
	{
		return DpsCalculator
			.compute(loadout.stats(), levels, monster, type, prayer, baseSpellMaxHit)
			.getDps();
	}
}
