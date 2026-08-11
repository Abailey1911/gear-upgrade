# Gear Upgrade

A RuneLite plugin that answers one question: **what should I buy next?**

It reads your bank, inventory, worn gear, combat levels and coins, then shows the
wiki's best-in-slot setup for a chosen boss, marks what you already own, and ranks
what to buy next against your actual GP.

## Why best-in-slot comes from the wiki, not from a calculator

Ranking equipment by summing its stat bonuses produces confidently wrong answers,
because real best-in-slot is decided by things that never appear in a stat block:

- **Passives** — Osmumten's fang rolls accuracy twice; a Twisted bow scales with the
  target's Magic level; Salve (ei) stacks with a Dragon hunter crossbow.
- **Weapon requirements** — the Corporeal Beast halves damage from anything that is
  not a corpbane weapon on stab.
- **Set bonuses** — crystal armour only does anything with a crystal bow.
- **Ammunition** — javelins do nothing for thrown knives or a Bow of faerdhinen.
- **Non-damage reasons** — Masori (f) is worn at the Corporeal Beast for *magic*
  defence; Wilderness bosses deliberately want cheaper gear you can afford to lose.

So the target setups are curated from the wiki's own recommended-equipment tables,
and the DPS engine is used only to order the affordable steps toward them.

## Data sources and attribution

Monster combat stats are derived from the **Old School RuneScape Wiki**, used under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/), obtained via
the wiki's own DPS calculator dataset:

- <https://github.com/weirdgloop/osrs-dps-calc> — `cdn/json/monsters.json`
- <https://oldschool.runescape.wiki/> — recommended equipment and strategy pages

Best-in-slot tables and the accompanying mechanic notes are hand-curated from the
wiki's strategy pages and carry the same attribution.

Equipment stats and Grand Exchange prices are read at runtime from RuneLite's own
`ItemManager`, so the plugin makes no network requests of its own.

## Licence

The **plugin source code** is BSD 2-Clause — see [LICENSE](LICENSE).

The **bundled data files** under `src/main/resources/com/gearupgrade/` are a separate
matter. They are derived from Old School RuneScape Wiki content and remain under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/), which carries
attribution, non-commercial and share-alike terms that BSD 2-Clause does not. They
are redistributed here under those terms rather than relicensed, and the attribution
above is part of that. Anyone reusing the data — as opposed to the code — takes it
under CC BY-NC-SA 3.0.

Item names, item ids and equipment stats are read from the game at runtime through
RuneLite's `ItemManager` and are not redistributed by this repository.
