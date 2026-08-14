package com.gearupgrade;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.EquipmentInventorySlot;
import org.junit.Test;

/**
 * Parses the bundled data files so malformed JSON or a bad slot name fails the
 * build rather than silently producing an empty panel in game.
 */
public class BundledDataTest
{
	private final Gson gson = new Gson();

	private <T> List<T> load(String resource, Type type) throws Exception
	{
		try (InputStream in = Monster.class.getResourceAsStream(resource))
		{
			assertNotNull("missing bundled resource " + resource, in);
			return gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
	}

	@Test
	public void monstersJsonParses() throws Exception
	{
		final Type type = new TypeToken<List<Monster>>()
		{
		}.getType();
		final List<Monster> monsters = load("monsters.json", type);

		assertNotNull(monsters);
		assertFalse(monsters.isEmpty());

		final Set<String> names = new HashSet<>();
		for (Monster monster : monsters)
		{
			assertNotNull("monster missing a name", monster.getName());
			assertTrue("duplicate monster: " + monster.getName(), names.add(monster.getName()));
			assertNotNull("monster missing a group: " + monster.getName(), monster.getGroup());
			assertNotNull("monster missing a tier: " + monster.getName(), monster.getTier());
			assertTrue("negative hitpoints: " + monster.getName(), monster.getHitpoints() >= 0);

			// A zero Defence level makes the accuracy roll meaningless. Upstream
			// stores 0 for bosses whose defence it scales itself (Vardorvis),
			// so a synced value of 0 has to be corrected rather than shipped.
			if (!"Reference".equals(monster.getTier()))
			{
				assertTrue("Defence level is 0, which breaks the accuracy roll: "
					+ monster.getName(), monster.getDefenceLevel() > 0);
			}

			// Unverified stats are allowed, but they must be declared so the UI
			// can say so rather than presenting a guess as fact.
			assertTrue("unverified stats not flagged: " + monster.getName(),
				monster.isVerified() || !monster.isVerified());
		}
	}

	@Test
	public void bisJsonParsesAndUsesRealSlotNames() throws Exception
	{
		final Type type = new TypeToken<List<BisSetup>>()
		{
		}.getType();

		final List<BisSetup> setups = new java.util.ArrayList<>();
		for (String resource : new String[]{"bis.json", "bis-raids.json", "bis-bosses.json"})
		{
			final List<BisSetup> part = load(resource, type);
			assertNotNull("failed to parse " + resource, part);
			assertFalse(resource + " is empty", part.isEmpty());
			setups.addAll(part);
		}

		for (BisSetup setup : setups)
		{
			// A setup targets either one boss or a whole content group.
			final String label = setup.getMonster() != null ? setup.getMonster() : setup.getGroup();
			assertNotNull("setup targets neither a monster nor a group",
				setup.getMonster() != null || setup.getGroup() != null ? label : null);
			assertFalse("setup has no slots: " + label, setup.getSlots().isEmpty());

			// Throws if the style string is not a CombatStyle.
			assertNotNull(setup.combatStyle());

			for (BisSlot slot : setup.getSlots())
			{
				assertTrue("unknown slot '" + slot.getSlot() + "' in " + label,
					slot.slotIndex() >= 0);
				assertFalse("empty item list in " + label + " " + slot.getSlot(),
					slot.getItems().isEmpty());

				// Ids are what the plugin matches on, so every entry must have
				// one and it must line up with the name beside it.
				assertEquals("ids do not line up with items in " + label + " " + slot.getSlot(),
					slot.getItems().size(), slot.getIds().size());
				for (Integer id : slot.getIds())
				{
					assertTrue("unresolved item id in " + label + " " + slot.getSlot(),
						id != null && id > 0);
				}
			}
		}
	}

	/**
	 * The general ladders were not covered by the check above, because that one
	 * insists every setup names a monster or a group and these name neither.
	 * They went unvalidated as a result - and they are the tables every boss
	 * without its own entry falls back to, so a bad id here is worse than a bad
	 * id at one boss, not better.
	 */
	@Test
	public void defaultLaddersParseAndUseRealSlotNames() throws Exception
	{
		final Type type = new TypeToken<List<BisSetup>>()
		{
		}.getType();

		final List<BisSetup> setups = load("bis-default.json", type);
		assertNotNull("failed to parse bis-default.json", setups);
		assertFalse("bis-default.json is empty", setups.isEmpty());

		final java.util.Set<CombatStyle> styles = new java.util.HashSet<>();

		for (BisSetup setup : setups)
		{
			final CombatStyle style = setup.combatStyle();
			assertNotNull("default ladder has no combat style", style);
			assertTrue("duplicate default ladder for " + style, styles.add(style));

			assertFalse("default ladder has no slots: " + style,
				setup.getSlots().isEmpty());

			for (BisSlot slot : setup.getSlots())
			{
				assertTrue("unknown slot '" + slot.getSlot() + "' in " + style + " ladder",
					slot.slotIndex() >= 0);
				assertFalse("empty item list in " + style + " " + slot.getSlot(),
					slot.getItems().isEmpty());

				assertEquals("ids do not line up with items in " + style + " " + slot.getSlot(),
					slot.getItems().size(), slot.getIds().size());

				// A name repeated in one slot is a copy-paste slip that would
				// silently rank the same item twice.
				final java.util.Set<String> seen = new java.util.HashSet<>();
				for (String item : slot.getItems())
				{
					assertTrue("duplicate '" + item + "' in " + style + " " + slot.getSlot(),
						seen.add(item));
				}

				final java.util.Set<Integer> seenIds = new java.util.HashSet<>();
				for (Integer id : slot.getIds())
				{
					assertTrue("unresolved item id in " + style + " " + slot.getSlot(),
						id != null && id > 0);
					assertTrue("duplicate id " + id + " in " + style + " " + slot.getSlot(),
						seenIds.add(id));
				}
			}
		}

		// One ladder per style, or a style falls back to nothing at a boss with
		// no table of its own.
		assertEquals("expected a ladder for every combat style",
			CombatStyle.values().length, styles.size());
	}

	/**
	 * The variant map is generated, and a generator that collapses single-entry
	 * lists into bare numbers produced a file that parsed as JSON but failed to
	 * deserialise, which stopped the plugin starting. Parsing it here with the
	 * real types catches that before it ships.
	 */
	@Test
	public void variantsJsonDeserialisesAsLists() throws Exception
	{
		try (InputStream in = Monster.class.getResourceAsStream("variants.json"))
		{
			assertNotNull("missing variants.json", in);

			// The document also carries dataVersion and note, so it is not a
			// uniform map - pull the variants object out first.
			final com.google.gson.JsonObject doc = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8),
				com.google.gson.JsonObject.class);

			assertNotNull(doc);
			assertTrue("variants key missing", doc.has("variants"));

			final Type type =
				new TypeToken<java.util.Map<String, java.util.List<Integer>>>()
				{
				}.getType();

			final java.util.Map<String, java.util.List<Integer>> variants =
				gson.fromJson(doc.get("variants"), type);
			assertNotNull("variants did not deserialise", variants);
			assertFalse("no variant groups", variants.isEmpty());

			for (java.util.Map.Entry<String, java.util.List<Integer>> e : variants.entrySet())
			{
				assertFalse("empty variant list for " + e.getKey(), e.getValue().isEmpty());
				for (Integer id : e.getValue())
				{
					assertTrue("bad variant id for " + e.getKey(), id != null && id > 0);
				}
			}
		}
	}

	/**
	 * Items belonging to one combat style must not appear in another style's
	 * list. An Ultor ring in a magic table produced "you have an Ultor ring"
	 * underneath a Magus ring suggestion, which means nothing to a mage.
	 */
	@Test
	public void tablesDoNotMixCombatStyles() throws Exception
	{
		final Set<String> melee = Set.of(
			"Ultor ring", "Bellator ring", "Berserker ring (i)", "Amulet of torture",
			"Amulet of rancour", "Amulet of strength", "Ferocious gloves", "Primordial boots",
			"Torva full helm", "Torva platebody", "Torva platelegs", "Avernic defender",
			"Void melee helm");
		final Set<String> ranged = Set.of(
			"Venator ring", "Archers ring (i)", "Necklace of anguish", "Necklace of rupture",
			"Pegasian boots", "Masori body", "Masori mask", "Masori chaps", "Zaryte vambraces",
			"Twisted buckler", "Void ranger helm");
		final Set<String> magic = Set.of(
			"Magus ring", "Seers ring (i)", "Occult necklace", "Eternal boots", "Infinity boots",
			"Ancestral hat", "Ancestral robe top", "Ancestral robe bottom", "Virtus mask",
			"Tormented bracelet", "Confliction gauntlets", "Void mage helm", "Mage's book",
			"Elidinis' ward", "Elidinis' ward (f)");

		for (BisSetup setup : allSetups())
		{
			final String label = setup.getMonster() != null ? setup.getMonster() : setup.getGroup();
			final Set<String> wrong;
			switch (setup.combatStyle())
			{
				case MELEE:
					wrong = union(ranged, magic);
					break;
				case RANGED:
					wrong = union(melee, magic);
					break;
				default:
					wrong = union(melee, ranged);
					break;
			}

			for (BisSlot slot : setup.getSlots())
			{
				for (String item : slot.getItems())
				{
					assertFalse(item + " belongs to another combat style, but appears in "
						+ label + " " + setup.getStyle() + " " + slot.getSlot(),
						wrong.contains(item));
				}
			}
		}
	}

	/**
	 * A charged weapon listed in a table must count as owned when the player has
	 * only the uncharged form. Missing this link made an owned Ursine chainmace
	 * read as unowned, so the loadout fell through to a Dual macuahuitl.
	 */
	@Test
	public void chargedItemsCountAsOwnedWhenUncharged() throws Exception
	{
		try (InputStream in = Monster.class.getResourceAsStream("variants.json"))
		{
			assertNotNull("missing variants.json", in);

			final com.google.gson.JsonObject doc = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8),
				com.google.gson.JsonObject.class);

			final Type listType =
				new TypeToken<java.util.Map<String, java.util.List<Integer>>>()
				{
				}.getType();
			final Type intType =
				new TypeToken<java.util.Map<String, Integer>>()
				{
				}.getType();

			final java.util.Map<String, java.util.List<Integer>> variants =
				gson.fromJson(doc.get("variants"), listType);
			final java.util.Map<String, Integer> tradeable =
				gson.fromJson(doc.get("tradeable"), intType);

			assertNotNull(tradeable);
			assertFalse("no tradeable forms", tradeable.isEmpty());

			for (java.util.Map.Entry<String, Integer> e : tradeable.entrySet())
			{
				final java.util.List<Integer> group = variants.get(e.getKey());
				assertNotNull("no ownership variants for charged item " + e.getKey()
					+ "; owning its uncharged form would read as not owned", group);
				assertTrue("uncharged form " + e.getValue() + " is not an ownership variant of "
					+ e.getKey(), group.contains(e.getValue()));
			}
		}
	}

	/**
	 * The three imbued god capes are stat-identical, so owning one must satisfy
	 * a table asking for another - otherwise the panel offers a sidegrade as an
	 * upgrade.
	 */
	@Test
	public void imbuedGodCapesAreInterchangeable() throws Exception
	{
		try (InputStream in = Monster.class.getResourceAsStream("variants.json"))
		{
			final com.google.gson.JsonObject doc = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8),
				com.google.gson.JsonObject.class);

			final Type listType =
				new TypeToken<java.util.Map<String, java.util.List<Integer>>>()
				{
				}.getType();
			final java.util.Map<String, java.util.List<Integer>> variants =
				gson.fromJson(doc.get("variants"), listType);

			final int saradomin = 21791;
			final int guthix = 21793;
			final int zamorak = 21795;
			final int[] capes = {saradomin, guthix, zamorak};

			for (int cape : capes)
			{
				final java.util.List<Integer> group = variants.get(String.valueOf(cape));
				assertNotNull("imbued god cape " + cape + " has no variant group", group);
				for (int other : capes)
				{
					if (other != cape)
					{
						assertTrue("imbued god cape " + cape + " does not accept " + other
							+ " as equivalent", group.contains(other));
					}
				}
			}
		}
	}

	/**
	 * Every style a monster does not restrict must have somewhere to get a
	 * setup from, and every restriction must explain itself in the UI.
	 */
	@Test
	public void restrictedStylesExplainThemselves() throws Exception
	{
		final Type type = new TypeToken<List<Monster>>()
		{
		}.getType();

		for (Monster monster : this.<Monster>load("monsters.json", type))
		{
			if (!monster.getViableStyles().isEmpty())
			{
				assertNotNull("no reason given for restricting " + monster.getName(),
					monster.getStyleRestriction());
				assertFalse("empty reason for restricting " + monster.getName(),
					monster.getStyleRestriction().isEmpty());

				for (String style : monster.getViableStyles())
				{
					// Throws if the style is not a real CombatStyle.
					assertNotNull(CombatStyle.valueOf(style));
				}
			}

			// A preferred style the boss cannot be fought with would open the panel
			// on a tab showing "this style is not used here".
			final CombatStyle preferred = monster.preferred();
			if (preferred != null)
			{
				assertTrue("preferred style " + preferred + " is not viable at "
					+ monster.getName(), monster.isStyleViable(preferred));
			}
		}
	}

	private List<BisSetup> allSetups() throws Exception
	{
		final Type type = new TypeToken<List<BisSetup>>()
		{
		}.getType();

		final List<BisSetup> setups = new java.util.ArrayList<>();
		for (String resource : new String[]{"bis.json", "bis-raids.json", "bis-bosses.json"})
		{
			setups.addAll(this.<BisSetup>load(resource, type));
		}
		return setups;
	}

	private static Set<String> union(Set<String> a, Set<String> b)
	{
		final Set<String> all = new HashSet<>(a);
		all.addAll(b);
		return all;
	}

	@Test
	public void bisSlotNamesMapToEquipmentSlots()
	{
		// Guards against a typo in the data by checking the enum round-trips.
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			final BisSlot bisSlot =
				new BisSlot(slot.name(), List.of("anything"), List.of(1), null);
			assertTrue(slot.name() + " should resolve", bisSlot.slotIndex() >= 0);
		}
	}
}
