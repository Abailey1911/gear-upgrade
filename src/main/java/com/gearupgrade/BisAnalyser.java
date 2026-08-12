/*
 * Copyright (c) 2026, Alexb
 * All rights reserved.
 * Licensed under BSD 2-Clause. See LICENSE.
 */
package com.gearupgrade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.util.QuantityFormatter;

/**
 * Compares the player's holdings against a wiki best-in-slot table and works
 * out what they already have, what is missing, and what to buy next.
 */
@Slf4j
@Singleton
public class BisAnalyser
{
	private final EquipmentIndex index;
	private final PlayerHoldings holdings;
	private final ItemRequirements requirements;
	private final ItemVariants itemVariants;

	/**
	 * How many levels short still counts as "nearly there". Beyond this, telling
	 * someone to train for an item is not advice they can act on today.
	 */
	private static final int TRAINABLE_GAP = 10;

	@Inject
	public BisAnalyser(EquipmentIndex index, PlayerHoldings holdings,
					   ItemRequirements requirements, ItemVariants itemVariants)
	{
		this.index = index;
		this.holdings = holdings;
		this.requirements = requirements;
		this.itemVariants = itemVariants;
	}

	/**
	 * One rung of a slot's ladder, for the hover-over progression list.
	 */
	@Value
	public static class Step
	{
		String name;
		boolean owned;

		/** 0 when it cannot be bought - earned from content, or untradeable. */
		long price;

		/** Levels still needed, eg. "82 Attack", or null when wearable. */
		@Nullable
		String levelShortfall;

		/**
		 * "stab", "slash" or "crush" for a melee weapon, else null. Lets the
		 * hover list show only the weapons that suit a boss's weakness - a crush
		 * boss should show the crush tier, not every melee weapon in the game.
		 */
		@Nullable
		String attackType;
	}

	/**
	 * One slot of the target setup, resolved against what the player owns.
	 */
	@Value
	public static class SlotStatus
	{
		String slotName;

		/** The wiki's first choice for this slot. */
		@Nullable
		EquipmentItem target;

		/** Best listed item the player actually owns, if any. */
		@Nullable
		EquipmentItem owned;

		/** True when the owned item is the wiki's top pick. */
		boolean hasBest;

		/** Cost of the best listed item the player could buy next. */
		long cost;

		/** Coins still needed, or 0 if affordable. */
		long shortfall;

		/** The item {@link #cost} refers to. */
		@Nullable
		EquipmentItem nextBuy;

		@Nullable
		String note;

		/** True when the top pick simply cannot be bought on the Grand Exchange. */
		boolean targetOffGe;

		/** Levels still needed for the top pick, eg. "82 Attack", or null. */
		@Nullable
		String levelShortfall;

		/**
		 * How to build the top pick from buyable parts, when it is untradeable
		 * but its components are not. Null when there is nothing to build.
		 */
		@Nullable
		String craftNote;

		/**
		 * Best item in this slot the player can actually afford today, which is
		 * often far below the top pick. Null when the top pick is affordable, or
		 * when nothing in reach beats what they already have.
		 */
		@Nullable
		EquipmentItem affordable;

		/** An affordable item that only levels are holding back. */
		@Nullable
		EquipmentItem trainFor;

		/** Levels still needed for {@link #trainFor}, eg. "75 Attack". */
		@Nullable
		String trainNeed;

		/**
		 * The whole ladder for this slot, best first, for the hover tooltip. Shows
		 * where the player currently sits and what the rungs above and below are -
		 * a row saying "buy Ancestral hat" does not tell you that Virtus sits
		 * between it and your Ahrim's.
		 */
		List<Step> progression;

		/**
		 * The attack type this boss is softest against, on the weapon slot only.
		 * Null elsewhere, and null for a boss with no preference.
		 */
		@Nullable
		String attackFocus;
	}

	/**
	 * A purchase worth making, measured by what it does to DPS rather than by
	 * where it sits in a list.
	 */
	@Value
	public static class Recommendation
	{
		EquipmentItem item;
		String slotName;
		long cost;

		/** DPS with this item worn, minus DPS as things stand. */
		double dpsGain;

		/** Percentage improvement, which reads better than a raw delta. */
		double percentGain;

		/** Coins still needed, or 0 when it is already within budget. */
		long shortfall;

		/**
		 * Offensive stat improvement, eg. "+3 strength, +12 slash". Max hit is a
		 * whole number, so a small upgrade can leave computed DPS untouched while
		 * still being a real improvement - this is what makes it visible.
		 */
		@Nullable
		String statNote;

		/**
		 * False when this upgrade cannot be bought at all - an Avernic defender
		 * or an Infernal cape is still the next step, it just is not a purchase.
		 */
		boolean purchasable;
	}

	@Value
	public static class Result
	{
		BisSetup setup;
		List<SlotStatus> slots;

		/**
		 * The best listed item the player owns in each slot. This is what they
		 * would actually wear to this boss today, so the equipment diagram and
		 * the DPS summary are both derived from it.
		 */
		Loadout wornFromBis;

		/** Cheapest meaningful next purchase the player can afford, if any. */
		@Nullable
		SlotStatus nextBuy;

		/** Names in the table that did not resolve to a real item. */
		List<String> unresolved;

		/**
		 * Special attack weapons for this setup, resolved the same way as a slot
		 * ladder so they can show what is owned and what a missing one costs.
		 * These are carried in the inventory rather than worn, so they are not a
		 * slot - but the progression question is identical.
		 */
		List<Step> specWeapons;

		/**
		 * The single purchase that raises DPS the most out of everything the
		 * player can afford, whichever slot it happens to be in. A weapon often
		 * beats an armour piece here, which a per-slot list never shows.
		 */
		@Nullable
		Recommendation bestBuy;

		/** Most DPS per million spent, when that is a different item. */
		@Nullable
		Recommendation bestValue;
	}

	/** A buildable item priced up from the parts still missing. */
	private static class Recipe
	{
		long cost;
		String description;
	}

	/**
	 * Prices an untradeable item by the parts it is built from, counting only
	 * the ones the player does not already have.
	 *
	 * <p>Returns null when there is no recipe, or when any missing part has no
	 * Grand Exchange price - a part-price total that silently omits a component
	 * would understate the cost, which is worse than declining to quote one.
	 */
	@Nullable
	private Recipe priceRecipe(int itemId)
	{
		final List<ItemVariants.Component> parts = itemVariants.componentsOf(itemId);
		if (parts.isEmpty())
		{
			return null;
		}

		final Recipe recipe = new Recipe();
		final List<String> needed = new ArrayList<>();

		for (ItemVariants.Component part : parts)
		{
			if (holdings.ownsCanonical(part.getId()))
			{
				continue;
			}

			final int unit = index.priceOf(part.getId());
			if (unit <= 0)
			{
				log.debug("No price for component {} ({}) of {}",
					part.getName(), part.getId(), itemId);
				return null;
			}

			recipe.cost += (long) unit * part.getQty();
			needed.add(part.getQty() > 1
				? QuantityFormatter.quantityToStackSize(part.getQty()) + " x " + part.getName()
				: part.getName());
		}

		if (needed.isEmpty())
		{
			// Every part already in the bank - it just needs assembling.
			recipe.description = "You have all the parts - combine them to make this";
			return recipe;
		}

		recipe.description = "Not sold directly. Buy " + String.join(", ", needed)
			+ ", then combine";
		return recipe;
	}

	/**
	 * @param budgetKnown false until the bank has been read, in which case the
	 *                    coin total is not the player's real budget and a
	 *                    shortfall would be meaningless.
	 */
	public Result analyse(BisSetup setup, long budget, boolean budgetKnown,
						  PlayerLevels levels, Monster monster, DamageType type,
						  PrayerBoost prayer, int baseSpellMaxHit)
	{
		final List<SlotStatus> statuses = new ArrayList<>();
		final List<String> unresolved = new ArrayList<>();

		// Everything the player owns from this table, per slot. The wiki decides
		// what is eligible; DPS decides which of them to actually wear, because
		// list order cannot know that an Eye of ayak attacks a tick faster than
		// a Tumeken's shadow, or that crystal armour transforms a Bow of
		// faerdhinen.
		final Map<Integer, List<EquipmentItem>> ownedBySlot = new HashMap<>();

		for (BisSlot slot : setup.getSlots())
		{
			EquipmentItem target = null;
			EquipmentItem bestOwned = null;
			EquipmentItem nextBuy = null;

			int rank = 0;
			int ownedRank = Integer.MAX_VALUE;
			int nextBuyRank = Integer.MAX_VALUE;
			boolean targetOffGe = false;
			EquipmentItem tradeableForm = null;
			String craftNote = null;
			long craftCost = 0;

			EquipmentItem affordable = null;
			int affordableRank = Integer.MAX_VALUE;
			EquipmentItem trainFor = null;
			int trainRank = Integer.MAX_VALUE;
			String trainNeed = null;
			final List<Step> progression = new ArrayList<>();

			// Ids, not names. Every ownership bug in this plugin so far has been a
			// string-matching failure of one kind or another.
			final List<Integer> slotIds = slot.getIds();
			final List<String> slotNames = slot.getItems();

			for (int idx = 0; idx < slotIds.size(); idx++)
			{
				final int itemId = slotIds.get(idx);
				final String itemName = idx < slotNames.size() ? slotNames.get(idx) : "?";

				EquipmentItem item = itemId > 0 ? index.byId(itemId) : null;

				if (item == null)
				{
					// The bundled ids come from the wiki dataset, which sometimes
					// names a non-canonical variant (a locked or degraded form)
					// that the client does not index. Fall back to the name so a
					// single bad id does not silently empty a slot.
					item = index.byName(itemName);
					if (item != null)
					{
						log.debug("Id {} for '{}' is not canonical; matched by name as {}",
							itemId, itemName, item.getId());
					}
				}

				if (item == null)
				{
					unresolved.add(itemName + " (id " + itemId + ")");
					rank++;
					continue;
				}

				// Match ownership on whatever the client actually indexes.
				final int matchId = item.getId();

				if (target == null)
				{
					target = item;
					targetOffGe = item.isUntradeable() || item.getPrice() <= 0;

					// A charged Scythe or Tumeken's shadow cannot be bought, but
					// its uncharged form can - quote that price rather than
					// dismissing the item as unobtainable.
					if (targetOffGe)
					{
						final int buyableId = itemVariants.tradeableFormOf(item.getId());
						final EquipmentItem buyable =
							buyableId > 0 ? index.byId(buyableId) : null;

						if (buyable != null && !buyable.isUntradeable() && buyable.getPrice() > 0)
						{
							tradeableForm = buyable;
							targetOffGe = false;
						}
					}

					// Still nothing to buy? It may still be buildable. An
					// Elidinis' ward (f) is untradeable, but the ward, the
					// sigil and the runes are all on the Grand Exchange, so
					// "earned from content" would be simply wrong.
					if (targetOffGe)
					{
						final Recipe recipe = priceRecipe(item.getId());
						if (recipe != null)
						{
							craftNote = recipe.description;
							craftCost = recipe.cost;
							targetOffGe = false;
						}
					}

					// Last resort: quote the item it is built on. A Berserker
					// ring (i) costs a Berserker ring plus a trip to Nightmare
					// Zone, and the ring is the part with a price.
					if (targetOffGe)
					{
						final int baseId = itemVariants.pricedAsOf(item.getId());
						final EquipmentItem base = baseId > 0 ? index.byId(baseId) : null;
						final int basePrice = baseId > 0 ? index.priceOf(baseId) : 0;

						if (basePrice > 0)
						{
							craftNote = "Not sold directly. Buy "
								+ (base != null ? base.getName() : "the base item")
								+ " for " + QuantityFormatter.quantityToStackSize(basePrice)
								+ " gp, then earn the upgrade.";
							craftCost = basePrice;
							targetOffGe = false;
						}
					}
				}

				// An ornamented or imbued version of the listed item counts as
				// owning it - otherwise a Necklace of anguish (or) reads as
				// "not owned" and the slot looks empty.
				final EquipmentItem ownedVariant = ownedVariantOf(itemId, item);

				progression.add(new Step(
					item.getName(),
					ownedVariant != null,
					item.isUntradeable() ? 0 : item.getPrice(),
					requirements.describeShortfall(item, levels),
					attackTypeOf(item)));

				if (ownedVariant != null)
				{
					if (rank < ownedRank)
					{
						ownedRank = rank;
						bestOwned = ownedVariant;
					}

					final int equipSlot = slot.slotIndex();
					if (equipSlot >= 0)
					{
						ownedBySlot.computeIfAbsent(equipSlot, k -> new ArrayList<>())
							.add(ownedVariant);
					}
				}
				else if (item.getPrice() > 0 && !item.isUntradeable())
				{
					if (requirements.canEquip(item, levels))
					{
						if (rank < nextBuyRank)
						{
							nextBuy = item;
							nextBuyRank = rank;
						}

						// The best thing actually within reach today. Without this
						// the panel tells a mid-level account to buy a 170m Torva
						// platebody and never mentions the 24m Bandos chestplate
						// sitting four rows below it.
						if (rank < affordableRank && budgetKnown && item.getPrice() <= budget)
						{
							affordable = item;
							affordableRank = rank;
						}
					}
					else if (rank < trainRank && (!budgetKnown || item.getPrice() <= budget))
					{
						// Affordable but not yet wearable. Worth surfacing when the
						// gap is small - "train 5 Attack" is advice, "train 25" is
						// noise.
						final int gap = requirements.levelsShort(item, levels);
						if (gap > 0 && gap <= TRAINABLE_GAP)
						{
							trainFor = item;
							trainRank = rank;
							trainNeed = requirements.describeShortfall(item, levels);
						}
					}
				}

				rank++;
			}

			// Never suggest buying something that ranks below what is already
			// owned - that is a downgrade, not an upgrade.
			if (nextBuyRank >= ownedRank)
			{
				nextBuy = null;
			}
			if (affordableRank >= ownedRank)
			{
				affordable = null;
			}
			if (trainRank >= ownedRank)
			{
				trainFor = null;
				trainNeed = null;
			}

			// Training advice only helps when it beats what is already buyable.
			if (trainFor != null && affordable != null && trainRank >= affordableRank)
			{
				trainFor = null;
				trainNeed = null;
			}

			// If the top pick is only buyable in its uncharged form, that is the
			// purchase to point at - provided they do not already have it.
			if (nextBuy == null && tradeableForm != null && ownedRank > 0
				&& !holdings.ownsCanonical(tradeableForm.getId()))
			{
				nextBuy = tradeableForm;
			}

			// Rank 0 means the player has the top pick - or something the variant
			// map treats as the same thing. Comparing ids directly called an Echo
			// Virtus piece "not the best", and told anyone holding an Imbued
			// saradomin cape to go and earn the identical Guthix one.
			final boolean hasBest = bestOwned != null && ownedRank == 0;

			// Nothing to build once you have the thing.
			if (hasBest)
			{
				craftNote = null;
				craftCost = 0;
			}

			final long cost = nextBuy != null ? nextBuy.getPrice()
				: (craftNote != null ? craftCost : 0);
			// Without the bank read, coins are not the real budget, so claiming a
			// shortfall would be worse than saying nothing.
			final long shortfall = budgetKnown ? Math.max(0, cost - budget) : 0;

			// If the top pick is out of reach on levels, say so rather than
			// silently offering something lower down the list.
			final String levelShortfall = target == null
				? null
				: requirements.describeShortfall(target, levels);

			// Nothing to point at once the top pick is already affordable.
			if (affordable != null && nextBuy != null
				&& affordable.getId() == nextBuy.getId())
			{
				affordable = null;
			}

			// Only the weapon slot cares which attack type the boss is soft to.
			final String attackFocus = "WEAPON".equals(slot.getSlot())
				? weakestAttackType(monster)
				: null;

			statuses.add(new SlotStatus(
				slot.getSlot(), target, bestOwned, hasBest, cost, shortfall, nextBuy,
				slot.getNote(), targetOffGe, levelShortfall, craftNote,
				affordable, trainFor, trainNeed, progression, attackFocus));
		}

		// Diagnostic: a slot where every listed item resolved to a real item but
		// none registered as owned is the signature of an ownership-detection
		// bug rather than a data gap, so log the ids to tell them apart.
		if (log.isDebugEnabled() && holdings.hasAnything())
		{
			for (SlotStatus status : statuses)
			{
				if (status.getOwned() != null || status.getTarget() == null)
				{
					continue;
				}

				final StringBuilder ids = new StringBuilder();
				for (String itemName : setup.getSlots().stream()
					.filter(s -> s.getSlot().equals(status.getSlotName()))
					.findFirst().map(BisSlot::getItems).orElse(java.util.Collections.emptyList()))
				{
					final EquipmentItem candidate = index.byName(itemName);
					ids.append(itemName).append('=')
						.append(candidate == null ? "unresolved" : String.valueOf(candidate.getId()))
						.append(' ');
				}
				log.debug("Nothing owned in {} slot; candidates: {}", status.getSlotName(), ids);
			}
		}

		if (!unresolved.isEmpty())
		{
			// Group setups have no monster name, so fall back to the group.
			log.debug("BiS entries for {} ({}) did not resolve to items: {}",
				setup.getMonster() != null ? setup.getMonster() : setup.getGroup(),
				setup.getStyle(), unresolved);
		}

		final Loadout worn = bestLoadoutFrom(ownedBySlot, monster, levels, type, prayer,
			baseSpellMaxHit);

		// Report the item DPS actually chose, so the list and the diagram agree.
		final List<SlotStatus> resolved = new ArrayList<>();
		for (SlotStatus status : statuses)
		{
			EquipmentItem chosen = null;
			try
			{
				chosen = worn.get(EquipmentInventorySlot.valueOf(status.getSlotName())
					.getSlotIdx());
			}
			catch (IllegalArgumentException e)
			{
				log.debug("Unknown slot name {}", status.getSlotName());
			}

			if (chosen == null || chosen.equals(status.getOwned()))
			{
				resolved.add(status);
				continue;
			}

			// The loadout may wear something other than the best listed item the
			// player owns - crystal armour gets pulled in whole to power a Bow of
			// faerdhinen, for instance. That is right for the diagram, but the row
			// must still say you own the Masori you own. Overwriting it here read
			// as "you have Crystal helm" to someone holding a Masori mask, and
			// painted an owned best-in-slot item amber.
			if (status.isHasBest())
			{
				resolved.add(status);
				continue;
			}

			resolved.add(new SlotStatus(
				status.getSlotName(), status.getTarget(), chosen,
				status.getTarget() != null && chosen.getId() == status.getTarget().getId(),
				status.getCost(), status.getShortfall(), status.getNextBuy(),
				status.getNote(), status.isTargetOffGe(), status.getLevelShortfall(),
				status.getCraftNote(), status.getAffordable(), status.getTrainFor(),
				status.getTrainNeed(), status.getProgression(), status.getAttackFocus()));
		}
		statuses.clear();
		statuses.addAll(resolved);

		// Drop ammunition the weapon cannot fire. Showing ruby bolts next to a
		// Bow of faerdhinen is wrong twice over - the bow makes its own arrows,
		// and bolts are crossbow ammunition.
		// Blessings live in the ammo slot for their prayer bonus and are not
		// ammunition, so only real arrows, bolts and javelins are checked.
		final int ammoSlot = EquipmentInventorySlot.AMMO.getSlotIdx();
		final EquipmentItem ammo = worn.get(ammoSlot);
		final EquipmentItem wornWeapon = worn.get(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (ammo != null
			&& WeaponRules.ammoType(ammo) != WeaponRules.AmmoType.NONE
			&& !WeaponRules.ammoCounts(wornWeapon, ammo))
		{
			worn.put(ammoSlot, null);
		}

		final List<Recommendation> ranked = rankPurchases(statuses, worn, monster, levels,
			type, prayer, baseSpellMaxHit, budget, budgetKnown);

		Recommendation bestBuy = null;
		Recommendation bestValue = null;
		if (!ranked.isEmpty())
		{
			bestBuy = ranked.get(0);

			// Most DPS per million. Only interesting when it is not the same item,
			// and only for something that actually costs money.
			Recommendation value = null;
			double bestRatio = 0;
			for (Recommendation r : ranked)
			{
				if (r.getCost() <= 0)
				{
					continue;
				}
				final double ratio = r.getDpsGain() / (r.getCost() / 1_000_000d);
				if (ratio > bestRatio)
				{
					bestRatio = ratio;
					value = r;
				}
			}
			if (value != null && value.getItem().getId() != bestBuy.getItem().getId())
			{
				bestValue = value;
			}
		}

		return new Result(setup, statuses, worn, pickNextBuy(statuses), unresolved,
			resolveSpecWeapons(setup, levels), bestBuy, bestValue);
	}

	/**
	 * Every affordable purchase, scored by what it actually does to DPS when worn
	 * rather than by its position in a list. Ordering slots by rank cannot tell
	 * you that a weapon is worth more than three armour pieces put together.
	 */
	private List<Recommendation> rankPurchases(List<SlotStatus> statuses, Loadout worn,
											   Monster monster, PlayerLevels levels,
											   DamageType type, PrayerBoost prayer,
											   int baseSpellMaxHit, long budget,
											   boolean budgetKnown)
	{
		final List<Recommendation> ranked = new ArrayList<>();
		if (monster == null || !monster.isGearApplicable())
		{
			return ranked;
		}

		final double baseline = dpsOf(worn, monster, levels, type, prayer, baseSpellMaxHit);

		for (SlotStatus status : statuses)
		{
			// What they could buy today, else the next real purchase, else the
			// wiki's pick even though it is earned rather than bought. Only the
			// last of those is not a purchase, and saying nothing at all because
			// an upgrade happens to be untradeable is worse than naming it.
			EquipmentItem candidate = status.getAffordable() != null
				? status.getAffordable()
				: status.getNextBuy();
			boolean purchasable = candidate != null;

			if (candidate == null && !status.isHasBest() && status.getTarget() != null)
			{
				candidate = status.getTarget();
				purchasable = false;
			}

			if (candidate == null)
			{
				continue;
			}

			// Already wearing it, so there is nothing to suggest.
			final EquipmentItem wornHere = status.getOwned();
			if (wornHere != null && wornHere.getId() == candidate.getId())
			{
				continue;
			}

			final int slotIdx;
			try
			{
				slotIdx = EquipmentInventorySlot.valueOf(status.getSlotName()).getSlotIdx();
			}
			catch (IllegalArgumentException e)
			{
				continue;
			}

			// Buying the uncharged form of a charged weapon still ends with the
			// charged weapon in your hand, so score the item you will actually
			// wield. An uncharged Scythe has no stats at all, which made every
			// melee recommendation score zero and disappear.
			EquipmentItem worthIt = candidate;
			final EquipmentItem target = status.getTarget();
			if (target != null
				&& itemVariants.tradeableFormOf(target.getId()) == candidate.getId())
			{
				worthIt = target;
			}

			final Loadout trial = worn.copy();
			trial.put(slotIdx, worthIt);

			final double after = dpsOf(trial, monster, levels, type, prayer, baseSpellMaxHit);
			final double gain = after - baseline;

			// Max hit is a whole number, so against a target with no Defence a
			// small strength upgrade can leave computed DPS identical. Judging
			// purely on that number silently discarded every real upgrade on the
			// reference dummy, so the raw stat change is the tiebreak.
			final GearStats before = worn.stats();
			final GearStats now = trial.stats();
			final int strDelta = strengthOf(now, type) - strengthOf(before, type);
			final int attDelta = attackOf(now, type) - attackOf(before, type);
			final int defDelta = defenceOf(now) - defenceOf(before);

			// Defence counts too - a piece that only adds survivability is still
			// an upgrade, and saying "nothing" because it adds no damage is wrong.
			if (gain <= 0 && strDelta <= 0 && attDelta <= 0 && defDelta <= 0)
			{
				continue;
			}

			final StringBuilder sb = new StringBuilder();
			if (strDelta > 0)
			{
				sb.append('+').append(strDelta).append(" strength");
			}
			if (attDelta > 0)
			{
				if (sb.length() > 0)
				{
					sb.append(", ");
				}
				sb.append('+').append(attDelta).append(' ')
					.append(type.name().toLowerCase(java.util.Locale.ROOT))
					.append(" accuracy");
			}
			if (sb.length() == 0 && defDelta > 0)
			{
				sb.append('+').append(defDelta).append(" defence");
			}
			final String statNote = sb.length() == 0 ? null : sb.toString();

			final long price = purchasable ? candidate.getPrice() : 0;
			ranked.add(new Recommendation(candidate, status.getSlotName(),
				price, gain,
				baseline > 0 ? (gain / baseline) * 100d : 0,
				budgetKnown && price > 0 ? Math.max(0, price - budget) : 0,
				statNote, purchasable));
		}

		// Something you can actually buy comes first - this section is headed
		// "recommended next purchase", so an earned item is the fallback answer
		// rather than the headline. Within each group, biggest gain wins.
		ranked.sort((a, b) ->
		{
			if (a.isPurchasable() != b.isPurchasable())
			{
				return a.isPurchasable() ? -1 : 1;
			}
			final int byDps = Double.compare(b.getDpsGain(), a.getDpsGain());
			if (byDps != 0)
			{
				return byDps;
			}
			return Long.compare(b.getCost(), a.getCost());
		});

		if (log.isDebugEnabled())
		{
			final StringBuilder sb = new StringBuilder();
			for (SlotStatus status : statuses)
			{
				final EquipmentItem c = status.getAffordable() != null
					? status.getAffordable() : status.getNextBuy();
				sb.append(status.getSlotName()).append('=')
					.append(c == null ? "none" : c.getName()).append(' ');
			}
			log.debug("Purchases [{}] baseline={} candidates: {} -> ranked {}",
				type, String.format("%.2f", baseline), sb, ranked.size());
		}

		return ranked;
	}

	/**
	 * Turns the setup's spec weapon names into the same rungs a slot ladder uses,
	 * so the panel can tick the ones already owned and price the rest.
	 */
	private List<Step> resolveSpecWeapons(BisSetup setup, PlayerLevels levels)
	{
		final List<Step> steps = new ArrayList<>();
		for (String name : setup.getSpecWeapons())
		{
			final EquipmentItem item = index.byName(name);
			if (item == null)
			{
				// Keep the name visible rather than silently dropping it.
				steps.add(new Step(name, false, 0, null, null));
				continue;
			}

			steps.add(new Step(
				item.getName(),
				ownedVariantOf(item.getId(), item) != null,
				item.isUntradeable() ? 0 : item.getPrice(),
				requirements.describeShortfall(item, levels),
				attackTypeOf(item)));
		}
		return steps;
	}

	/** Greedy passes; the loadout settles well before this. */
	private static final int CONVERGENCE_PASSES = 3;

	/**
	 * Picks the highest-DPS combination of the wiki-listed gear the player owns.
	 *
	 * <p>Choosing by list position instead would ignore everything the engine
	 * knows: attack speed, the crystal set bonus, whether the ammo slot even
	 * applies. The wiki table still decides what is eligible - this only ranks
	 * within it.
	 */
	private Loadout bestLoadoutFrom(Map<Integer, List<EquipmentItem>> ownedBySlot,
									Monster monster, PlayerLevels levels, DamageType type,
									PrayerBoost prayer, int baseSpellMaxHit)
	{
		final int weaponSlot = EquipmentInventorySlot.WEAPON.getSlotIdx();
		final int shieldSlot = EquipmentInventorySlot.SHIELD.getSlotIdx();

		final Loadout loadout = new Loadout();

		// Wiki order decides, not computed DPS. Ranking by our own DPS model
		// reproduced exactly the nonsense the wiki tables exist to prevent -
		// mixed hide over Oathplate, Barrows gloves over Confliction gauntlets -
		// because the model cannot see passives, set bonuses or effects. The
		// lists are already ordered by people who know the content.
		final int ammoSlotIdx = EquipmentInventorySlot.AMMO.getSlotIdx();

		// Void is all-or-nothing: a single piece gives no bonus and is simply bad
		// armour, so void gloves must never end up next to crystal or Masori.
		final boolean fullVoid = voidIsTheWholeSet(ownedBySlot);

		for (int slot : Loadout.SLOTS)
		{
			// Ammo waits until the weapon is known - it has to match.
			if (slot == weaponSlot || slot == shieldSlot || slot == ammoSlotIdx)
			{
				continue;
			}

			final List<EquipmentItem> candidates = ownedBySlot.get(slot);
			if (candidates == null || candidates.isEmpty())
			{
				continue;
			}

			for (EquipmentItem candidate : candidates)
			{
				if (!fullVoid && isVoidPiece(candidate))
				{
					continue;
				}

				loadout.put(slot, candidate);
				break;
			}
		}

		// Weapon and shield are still one decision: a two-handed weapon cannot
		// share the slot with a shield, so the shield is only added when the
		// chosen weapon leaves room for it.
		final List<EquipmentItem> weapons =
			ownedBySlot.getOrDefault(weaponSlot, java.util.Collections.emptyList());
		final List<EquipmentItem> shields =
			ownedBySlot.getOrDefault(shieldSlot, java.util.Collections.emptyList());

		// Set weapons are only worth wielding with their armour - a Bow of
		// faerdhinen without crystal, or an Eclipse atlatl without Eclipse moon,
		// is a weak weapon and must not outrank a crossbow the player owns.
		final int crystalPieces = countArmourPieces(ownedBySlot, "crystal ");
		final List<EquipmentItem> usable = new ArrayList<>();
		for (EquipmentItem candidate : weapons)
		{
			final String requiredSet = requiredSetFor(candidate);
			if (requiredSet != null && countArmourPieces(ownedBySlot, requiredSet) < 2)
			{
				continue;
			}
			usable.add(candidate);
		}

		final EquipmentItem weapon = usable.isEmpty() ? null : usable.get(0);

		if (weapon != null)
		{
			loadout.put(weaponSlot, weapon);
		}

		if (!shields.isEmpty() && (weapon == null || !weapon.isTwoHanded()))
		{
			loadout.put(shieldSlot, shields.get(0));
		}

		// Ammo is chosen to suit the weapon rather than picked by rank and then
		// discarded: a crossbow takes the best bolts, a bow the best arrows, and
		// anything that fires nothing keeps a blessing for the prayer bonus.
		for (EquipmentItem ammo : ownedBySlot.getOrDefault(ammoSlotIdx,
			java.util.Collections.emptyList()))
		{
			final boolean isAmmunition =
				WeaponRules.ammoType(ammo) != WeaponRules.AmmoType.NONE;

			if (!isAmmunition || WeaponRules.ammoCounts(weapon, ammo))
			{
				loadout.put(ammoSlotIdx, ammo);
				break;
			}
		}

		applyCrystalCoherence(loadout, ownedBySlot, weaponSlot);

		if (log.isDebugEnabled())
		{
			final StringBuilder sb = new StringBuilder();
			for (int slot : Loadout.SLOTS)
			{
				final EquipmentItem worn = loadout.get(slot);
				if (worn != null)
				{
					sb.append(slotName(slot)).append('=').append(worn.getName()).append("  ");
				}
			}
			log.debug("Chosen [{}] crystalPieces={} owned weapons={} -> {}",
				type, crystalPieces, weapons.size(), sb);
		}

		return loadout;
	}

	private static String slotName(int slotIdx)
	{
		for (EquipmentInventorySlot s : EquipmentInventorySlot.values())
		{
			if (s.getSlotIdx() == slotIdx)
			{
				return s.name();
			}
		}
		return String.valueOf(slotIdx);
	}

	/**
	 * Weapons whose value comes from a matching armour set, mapped to the name
	 * prefix of that armour. Without the set they are weak, so they must not
	 * outrank ordinary gear the player owns.
	 */
	private static String requiredSetFor(EquipmentItem item)
	{
		if (item == null || item.getName() == null)
		{
			return null;
		}

		final String name = item.getName().toLowerCase(java.util.Locale.ROOT);
		if (name.startsWith("bow of faerdhinen") || name.startsWith("crystal bow"))
		{
			return "crystal ";
		}
		if (name.startsWith("eclipse atlatl"))
		{
			return "eclipse moon ";
		}
		// The macuahuitl's damage comes from the Blood moon set's second-hit
		// chance. On paper it has 121 crush at 4 ticks, which flatters it badly -
		// without the armour it is an ordinary weapon and must not outrank one.
		if (name.startsWith("dual macuahuitl"))
		{
			return "blood moon ";
		}
		return null;
	}

	private static boolean isCrystalBow(EquipmentItem item)
	{
		return "crystal ".equals(requiredSetFor(item));
	}

	/**
	 * How many of head, body and legs the player owns from a given armour set.
	 */
	private static int countArmourPieces(Map<Integer, List<EquipmentItem>> ownedBySlot,
										 String namePrefix)
	{
		final int[] slots = {
			EquipmentInventorySlot.HEAD.getSlotIdx(),
			EquipmentInventorySlot.BODY.getSlotIdx(),
			EquipmentInventorySlot.LEGS.getSlotIdx(),
		};

		int count = 0;
		for (int slot : slots)
		{
			for (EquipmentItem owned : ownedBySlot.getOrDefault(slot,
				java.util.Collections.emptyList()))
			{
				if (owned.getName() != null
					&& owned.getName().toLowerCase(java.util.Locale.ROOT).startsWith(namePrefix))
				{
					count++;
					break;
				}
			}
		}
		return count;
	}

	/**
	 * Void armour, which is worthless outside its own set.
	 *
	 * <p>Matched on a trailing space so that the Voidwaker - a mace that has
	 * nothing to do with the set - is never caught by it.
	 */
	private static boolean isVoidPiece(EquipmentItem item)
	{
		if (item == null || item.getName() == null)
		{
			return false;
		}

		final String name = item.getName().toLowerCase(java.util.Locale.ROOT);
		return name.startsWith("void ") || name.startsWith("elite void ");
	}

	/**
	 * True only when void is what the player would wear in every one of the four
	 * slots the set occupies.
	 *
	 * <p>Void's entire value is the set bonus, which needs helm, top, robe and
	 * gloves together. Owning the set is not enough to justify wearing part of
	 * it: void gloves next to crystal armour give nothing at all, and void
	 * gloves are poor gloves on their own. So the choice is made once for the
	 * whole set - if the best owned item in any of the four slots is not void,
	 * then void is not what we are wearing, and none of it should appear.
	 */
	private static boolean voidIsTheWholeSet(Map<Integer, List<EquipmentItem>> ownedBySlot)
	{
		final int[] setSlots = {
			EquipmentInventorySlot.HEAD.getSlotIdx(),
			EquipmentInventorySlot.BODY.getSlotIdx(),
			EquipmentInventorySlot.LEGS.getSlotIdx(),
			EquipmentInventorySlot.GLOVES.getSlotIdx(),
		};

		for (int slot : setSlots)
		{
			final List<EquipmentItem> owned = ownedBySlot.getOrDefault(slot,
				java.util.Collections.emptyList());

			// Nothing owned here, so the set cannot be completed.
			if (owned.isEmpty() || !isVoidPiece(owned.get(0)))
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * A Bow of faerdhinen is only worth using in full crystal armour - the set
	 * gives it 30% accuracy and 15% damage. Pairing it with Masori, as a
	 * per-slot ranking naturally would, defeats the point of the weapon.
	 */
	private static void applyCrystalCoherence(Loadout loadout,
											  Map<Integer, List<EquipmentItem>> ownedBySlot,
											  int weaponSlot)
	{
		final EquipmentItem weapon = loadout.get(weaponSlot);
		if (weapon == null || weapon.getName() == null)
		{
			return;
		}

		final String name = weapon.getName().toLowerCase(java.util.Locale.ROOT);
		if (!name.startsWith("bow of faerdhinen") && !name.startsWith("crystal bow"))
		{
			return;
		}

		final int[] slots = {
			EquipmentInventorySlot.HEAD.getSlotIdx(),
			EquipmentInventorySlot.BODY.getSlotIdx(),
			EquipmentInventorySlot.LEGS.getSlotIdx(),
		};

		for (int slot : slots)
		{
			for (EquipmentItem owned : ownedBySlot.getOrDefault(slot,
				java.util.Collections.emptyList()))
			{
				if (owned.getName() != null
					&& owned.getName().toLowerCase(java.util.Locale.ROOT).startsWith("crystal "))
				{
					loadout.put(slot, owned);
					break;
				}
			}
		}
	}

	/** The strength stat that drives max hit for this style. */
	private static int strengthOf(GearStats stats, DamageType type)
	{
		switch (type)
		{
			case RANGED:
				return stats.getRstr();
			case MAGIC:
				// Magic damage rides on the multiplier rather than a flat bonus.
				return (int) Math.round(stats.getDamageMultiplier() * 100);
			default:
				return stats.getStr();
		}
	}

	/** Total defensive bonus, so a purely defensive upgrade still registers. */
	private static int defenceOf(GearStats stats)
	{
		return stats.getDstab() + stats.getDslash() + stats.getDcrush()
			+ stats.getDmagic() + stats.getDrange();
	}

	/** The accuracy stat this style actually rolls with. */
	private static int attackOf(GearStats stats, DamageType type)
	{
		switch (type)
		{
			case STAB:
				return stats.getAstab();
			case SLASH:
				return stats.getAslash();
			case CRUSH:
				return stats.getAcrush();
			case RANGED:
				return stats.getArange();
			default:
				return stats.getAmagic();
		}
	}

	/**
	 * Which melee attack type a weapon is actually for, by whichever of its stab,
	 * slash and crush bonuses is highest. Null for anything that is not a melee
	 * weapon, so ranged and magic lists are left alone.
	 */
	@Nullable
	private static String attackTypeOf(EquipmentItem item)
	{
		if (item == null || item.getStats() == null)
		{
			return null;
		}

		final ItemEquipmentStats s = item.getStats();
		final int stab = s.getAstab();
		final int slash = s.getAslash();
		final int crush = s.getAcrush();

		if (stab <= 0 && slash <= 0 && crush <= 0)
		{
			return null;
		}

		if (stab >= slash && stab >= crush)
		{
			return "stab";
		}
		return slash >= crush ? "slash" : "crush";
	}

	/**
	 * The melee attack type a monster is softest against, or null when it has no
	 * meaningful preference.
	 */
	@Nullable
	private static String weakestAttackType(Monster monster)
	{
		if (monster == null)
		{
			return null;
		}

		final int stab = monster.getDstab();
		final int slash = monster.getDslash();
		final int crush = monster.getDcrush();

		// A flat stat line means no preference worth acting on.
		if (stab == slash && slash == crush)
		{
			return null;
		}

		if (stab <= slash && stab <= crush)
		{
			return "stab";
		}
		return slash <= crush ? "slash" : "crush";
	}

	private static double dpsOf(Loadout loadout, Monster monster, PlayerLevels levels,
								DamageType type, PrayerBoost prayer, int baseSpellMaxHit)
	{
		return DpsCalculator
			.compute(loadout.stats(), levels, monster, type, prayer, baseSpellMaxHit)
			.getDps();
	}

	/**
	 * The version of a listed item that the player owns - the exact item, or an
	 * ornamented/imbued equivalent - or null if they own none of them.
	 */
	@Nullable
	private EquipmentItem ownedVariantOf(int itemId, EquipmentItem exact)
	{
		if (holdings.ownsCanonical(itemId))
		{
			return exact;
		}

		for (int variantId : itemVariants.variantsOf(itemId))
		{
			if (holdings.ownsCanonical(variantId))
			{
				final EquipmentItem variant = index.byId(variantId);
				if (variant != null)
				{
					return variant;
				}
			}
		}

		return null;
	}

	/**
	 * The cheapest affordable upgrade, preferring slots where the player has
	 * nothing at all over slots where they merely lack the top pick.
	 */
	private static SlotStatus pickNextBuy(List<SlotStatus> statuses)
	{
		SlotStatus best = null;

		for (SlotStatus status : statuses)
		{
			if (status.getNextBuy() == null || status.getShortfall() > 0 || status.isHasBest())
			{
				continue;
			}

			if (best == null)
			{
				best = status;
				continue;
			}

			// An empty slot beats a mere upgrade; otherwise take the cheaper one.
			final boolean statusEmpty = status.getOwned() == null;
			final boolean bestEmpty = best.getOwned() == null;

			if (statusEmpty != bestEmpty)
			{
				best = statusEmpty ? status : best;
			}
			else if (status.getCost() < best.getCost())
			{
				best = status;
			}
		}

		return best;
	}
}
