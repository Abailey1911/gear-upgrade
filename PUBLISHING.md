# Publishing to the RuneLite Plugin Hub

Step by step, from a machine with nothing set up. Everything in this repository is
already prepared: two commits, a passing build, the manifest properties, the licence
and the root icon. What follows needs your GitHub account, which is why it is yours
to run.

Run every command from **PowerShell**, in this directory:

```
C:\Users\Alexb\OneDrive\Desktop\Claude\gear-upgrade
```

---

## Step 1 — Install the GitHub CLI

```powershell
winget install --id GitHub.cli
```

**Close PowerShell and open a new window afterwards.** The installer adds `gh` to
your PATH, and an already-open window will not see it. Check it worked:

```powershell
gh --version
```

---

## Step 2 — Sign in to GitHub

```powershell
gh auth login
```

You will be asked four questions. Answer with the arrow keys and Enter:

| Question | Answer |
| --- | --- |
| Where do you use GitHub? | **GitHub.com** |
| Preferred protocol for Git operations? | **HTTPS** |
| Authenticate Git with your GitHub credentials? | **Yes** |
| How would you like to authenticate? | **Login with a web browser** |

It prints a one-time code like `A1B2-C3D4`. Copy it, press Enter, and your browser
opens to <https://github.com/login/device>. Paste the code and click **Authorize**.

You type your GitHub password into GitHub's own site here — never into this terminal,
and never anywhere else.

Confirm it worked:

```powershell
gh auth status
```

---

## Step 3 — Decide what email is attached to your commits

Your commits are currently authored as `alex.bailey1911@gmail.com`. Once pushed,
that address is **permanently public and scrapable**. GitHub gives you a free alias
instead.

To use it, first find your username from step 2, then:

```powershell
git config user.email "YOUR-USERNAME@users.noreply.github.com"
git rebase --root --exec "git commit --amend --no-edit --reset-author"
```

Skip this if you do not mind the real address being public. Do it **now** if you do
— it is trivial before pushing and a genuine mess afterwards.

---

## Step 4 — Create the repository and push

One command creates it, wires up the remote and pushes both commits:

```powershell
gh repo create gear-upgrade --public --source=. --remote=origin --push
```

It must be **public** — the hub cannot build from a private repository.

Check it looks right in the browser:

```powershell
gh repo view --web
```

---

## Step 5 — Read the commit hash

The hub pins one exact commit, not a branch:

```powershell
git rev-parse HEAD
```

Copy the full 40 characters. If you did step 3, the hash **changed** from what it was
before — always read it fresh, never reuse an older one.

---

## Step 6 — Fork the plugin hub

```powershell
cd ..
gh repo fork runelite/plugin-hub --clone
cd plugin-hub
git checkout -b add-gear-upgrade
```

---

## Step 7 — Add the manifest

One file, named exactly `gear-upgrade` with **no file extension**, in the `plugins`
folder. The filename becomes the plugin's internal name, so the spelling matters.

```powershell
@"
repository=https://github.com/YOUR-USERNAME/gear-upgrade.git
commit=THE-40-CHARACTER-HASH
"@ | Out-File -FilePath plugins\gear-upgrade -Encoding ascii -NoNewline
```

Check it:

```powershell
Get-Content plugins\gear-upgrade
```

Two lines, your username, your hash. Nothing else.

---

## Step 8 — Open the pull request

```powershell
git add plugins/gear-upgrade
git commit -m "Add Gear Upgrade"
git push -u origin add-gear-upgrade
gh pr create --repo runelite/plugin-hub --title "Add Gear Upgrade" --fill
```

It prints the pull request URL. That is your submission.

---

## What happens next

A maintainer reviews the plugin. Expect it to take days, not hours.

**If CI fails**, the fix goes in *your* plugin repository, not the hub PR:

1. Fix the problem in `gear-upgrade`, commit and push
2. `git rev-parse HEAD` for the new hash
3. Edit `plugins/gear-upgrade` in the hub PR with that new hash
4. Commit and push the PR branch

The hub builds the exact commit named in the manifest, so a fix is invisible until
the hash moves. This trips up most first submissions.

**Questions a reviewer may raise**, and the honest answers:

- *Reflection?* No. The `java.lang.reflect.Type` imports in `BisRepository` and
  `MonsterRepository` are Gson's `TypeToken` generic parameter, not reflective access.
- *Network calls?* None. All data is bundled; equipment stats and Grand Exchange
  prices come from RuneLite's own `ItemManager`.
- *Is this a fight simulator?* No — the hub rejects those. This compares gear you
  already own against a reference table.
- *Licence?* Code is BSD 2-Clause. The bundled JSON is OSRS Wiki derived and stays
  under CC BY-NC-SA 3.0, attributed in the README rather than relicensed. This is the
  most likely thing to be asked about.
