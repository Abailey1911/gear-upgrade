# Publishing to the RuneLite Plugin Hub

Everything in the repository is ready. What remains needs your GitHub account, so
it is written out here rather than done for you.

## 1. Create the public repository

The hub only accepts public repositories. Create one named `gear-upgrade`, then:

```bash
git remote add origin https://github.com/<your-username>/gear-upgrade.git
git branch -M master
git push -u origin master
```

## 2. Get the commit hash

The manifest pins an exact commit, not a branch:

```bash
git rev-parse HEAD
```

At the time of writing that is `2cefd43d2e1d583847a7e58a1a41a37607576bca`, but it
changes with every commit you push, so read it again after the final push.

## 3. Fork the plugin hub and add the manifest

Fork <https://github.com/runelite/plugin-hub>, then create a file named
`plugins/gear-upgrade` — no extension — containing exactly two lines:

```
repository=https://github.com/<your-username>/gear-upgrade.git
commit=<the 40-character hash from step 2>
```

## 4. Open the pull request

Open it against `runelite/plugin-hub`. If CI fails, push a fix to *your* plugin
repository, then update the `commit=` line in the PR to the new hash. The hub build
checks out that exact commit, so a fix is not picked up until the hash moves.

## What a reviewer will look at

- **No reflection, native code, process execution or object deserialisation.** This
  plugin uses none. The `java.lang.reflect.Type` imports in `BisRepository` and
  `MonsterRepository` are Gson's `TypeToken` generic parameter, not reflective
  access — worth being able to say out loud if asked.
- **No third-party network calls.** All data is bundled; equipment stats and prices
  come from RuneLite's own `ItemManager`. Plugins that fetch from outside services
  need an opt-in warning, and this one does not.
- **No content simulation.** The hub rejects boss fight simulators. This compares
  gear you own against a reference table; it does not simulate encounters.
- **Licence.** Code is BSD 2-Clause. The bundled data is CC BY-NC-SA 3.0 from the
  OSRS Wiki and is redistributed under those terms, with attribution in the README.
  This split is the one thing most likely to draw a question, so it is stated
  plainly rather than buried.

## Before you push

- [ ] `./gradlew build` passes (18 tests)
- [ ] `runelite-plugin.properties` — check `author` is the name you want public
- [ ] Decide whether `alex.bailey1911@gmail.com` should be the commit author email
      on a public repository, or whether to use a GitHub `noreply` address instead
