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

## Step 1 — Nothing to install

Git for Windows is already here, and it ships with **Git Credential Manager**, which
is already configured (`credential.helper = manager`). The first time you push, it
opens a browser window and signs you in. No GitHub CLI needed.

You type your GitHub password into GitHub's own site — never into this terminal, and
never anywhere else.

---

## Step 2 — Create the empty repository on GitHub

In a browser, go to <https://github.com/new> and fill in:

| Field | Value |
| --- | --- |
| Repository name | `gear-upgrade` |
| Visibility | **Public** — the hub cannot build a private repo |
| Add a README file | **leave unticked** |
| Add .gitignore | **None** |
| Choose a licence | **None** |

Those last three matter. Ticking any of them creates a commit on GitHub that your
local history does not have, and the push in step 4 is then rejected as a conflict.
You already have a README, a `.gitignore` and a LICENSE locally.

Click **Create repository**. Leave the page open — it shows your repository URL.

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

## Step 4 — Push

Point your local repository at the one you just created, then push:

```powershell
git remote add origin https://github.com/YOUR-USERNAME/gear-upgrade.git
git push -u origin master
```

On the push, a browser window opens asking you to sign in to GitHub. Choose
**Sign in with your browser**, authorise, and the push completes on its own. This
happens once — the credential is stored in Windows Credential Manager afterwards.

Refresh the GitHub page. Your files should be there.

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

In a browser, go to <https://github.com/runelite/plugin-hub> and click **Fork** at
the top right, then **Create fork**. You now have your own copy.

---

## Step 7 — Add the manifest, in the browser

No cloning needed — GitHub can create the file directly.

1. In **your fork**, click into the `plugins` folder
2. **Add file** → **Create new file** (the button is top right)
3. Name the file exactly `gear-upgrade` — **no file extension**, no `.txt`. The
   filename becomes the plugin's internal name, so the spelling matters
4. Paste exactly two lines into the body:

   ```
   repository=https://github.com/YOUR-USERNAME/gear-upgrade.git
   commit=THE-40-CHARACTER-HASH
   ```

5. Click **Commit changes...**
6. Choose **Create a new branch for this commit and start a pull request**
7. Click **Propose changes**

---

## Step 8 — Open the pull request

The previous step lands you on the pull request form. Check that it reads:

> base repository: **runelite/plugin-hub** base: **master** ← head repository:
> **YOUR-USERNAME/plugin-hub**

If the base says your own fork instead of `runelite/plugin-hub`, change it — a PR
against your own fork goes nowhere.

Click **Create pull request**. That is your submission.

---

## What happens next

A maintainer reviews the plugin. Expect it to take days, not hours.

**If CI fails**, the fix goes in *your* plugin repository, not the hub PR:

1. Fix the problem in `gear-upgrade`, commit and `git push`
2. `git rev-parse HEAD` for the new hash
3. In your plugin-hub fork, open `plugins/gear-upgrade`, click the pencil icon,
   replace the `commit=` line with the new hash, and commit to the same branch

The pull request updates itself. You do not open a second one.

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
