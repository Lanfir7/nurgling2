---
name: nurgling-project-release
description: Use when Denis asks to commit all current Nurgling project work and make, publish, ship, send, or push a new client release. This project-only workflow validates every uncommitted change, completes bilingual player notes, creates a source commit, runs ant release, creates a separate release commit, and pushes the current branch to origin.
compatibility: Windows PowerShell, Git, Python 3, Apache Ant, Java, and repository push access.
---

# Nurgling project release

Run this skill only from the repository containing `build.xml`, `build.num`,
`changes/`, and `tools/release_notes.py`. An explicit request to release or ship
authorizes the source commit, `ant release`, the release commit, and the final
push. Continue through the full workflow without asking again unless credentials,
conflicts, failing checks, suspicious secrets, or another real blocker requires
the user.

The release has two commits. The first freezes all current project work. The
second records only the versioned output produced by `ant release`. One final
push sends both commits together.

## 1. Establish the release boundary

1. Confirm the repository root, current branch, `HEAD`, and remotes:

   ```powershell
   git rev-parse --show-toplevel
   git branch --show-current
   git status --short
   git remote -v
   ```

2. Use the current branch. Push to `origin` by default. If `origin` is absent or
   points somewhere unexpected for this repository, stop and report it. Never
   guess another remote and never force-push.
3. Treat every tracked modification, deletion, and untracked file shown by Git
   as part of the requested release. Do not silently leave work behind.
4. Inspect untracked files for credentials, local databases, crash dumps, logs,
   caches, and agent run folders. Files already ignored by Git remain ignored.
   Remove a transient file only when this run created it and it is clearly
   disposable. If an existing untracked file may contain a secret or private
   machine data, stop before staging and name it.
5. Abort on an unfinished merge, rebase, cherry-pick, or detached `HEAD`.

## 2. Complete player-facing notes

Review the complete diff and group player-visible behavior into concise notes in
`changes/YYYY-MM-DD-short-name.json`. Follow `docs/player-release-notes.md`.
Every note needs Russian and English text, the player benefit, and where the
player finds it. Do not announce internal-only work, unfinished behavior, class
names, commit history, or implementation details.

Preserve published IDs. A correction to something already published gets a new
note. Before committing, run:

```powershell
python tools/release_notes.py --check
python -m unittest discover -s tools -p test_release_notes.py
```

Also run `ant test` and any focused tests required by the changed areas. A
failing required check stops the release. Keep the working tree intact and
report the failing command and useful error.

## 3. Create the source commit

Stage the entire reviewed release boundary:

```powershell
git add -A
git status --short
git diff --cached --check
git diff --cached --stat
```

Inspect the staged diff for accidental binaries, secrets, unrelated local data,
and missing player notes. Then run `git commit` with a concise conventional
message describing the main player-facing result. For mixed coordinated work,
use a neutral message such as `feat: prepare client updates` and refine it from
the actual diff. Do not amend an earlier commit.

Record the resulting source commit SHA. Verify `git status --short` is empty
before building the release. New changes appearing here indicate concurrent
work; stop instead of folding them into the generated release commit.

## 4. Build the release

The previous history must exist at `release/release-notes.json`. If the working
tree lacks it, restore the tracked copy from `HEAD`. If no valid previous history
exists, stop rather than publishing a release that loses the in-client archive.

Run exactly:

```powershell
ant release
```

This command validates pending notes, increments `build.num`, writes the version
to `ver`, packages client JARs, updates `release/release-notes.json`, and refreshes
`src/nurgling/news/releases.json` for the client.

If `ant release` fails, do not reset, decrement the build number, or manufacture
artifacts. Report the failure and leave its files available for diagnosis.

## 5. Validate and commit generated output

After a successful build:

```powershell
python tools/release_notes.py --check
python -m unittest discover -s tools -p test_release_notes.py
git status --short
git diff --check
Get-Content -LiteralPath ver
```

Confirm that the release version is new, `build.num` advanced once,
`release/release-notes.json` retained older releases, and
`src/nurgling/news/releases.json` contains the new version without clearing
history. The working tree should now contain only output caused by `ant release`.
Unexpected source changes are a blocker.

Stage and create the second commit:

```powershell
git add -A
git diff --cached --check
git diff --cached --stat
git commit -m "release: <version from ver>"
```

Verify the source commit is immediately followed by the release commit and the
working tree is clean.

## 6. Push both commits

Push the current branch once:

```powershell
git push origin HEAD
```

Do not use `--force`, rewrite history, or automatically rebase after rejection.
If authentication or remote divergence blocks the push, keep both local commits
and report the exact command and error.

## Completion report

Report only:

- released version;
- source commit SHA and release commit SHA;
- checks that passed;
- pushed remote and branch;
- any excluded file or unresolved blocker.

## Quick reference

| Phase | Required result |
|---|---|
| Audit | Every Git status entry reviewed; no secret or interrupted Git operation |
| Notes | Bilingual player notes valid and history preserved |
| Source | All current work in one reviewed source commit |
| Release | `ant release` succeeds and advances the version once |
| Generated | Release output in a separate `release: <version>` commit |
| Delivery | Clean tree and successful `git push origin HEAD` |

## Never do during this workflow

- Do not discard, stash, or reset user work to make the tree look clean.
- Do not publish from a partial staged selection when the request says all work.
- Do not continue after failing tests or a failed `ant release`.
- Do not merge source and generated release output into one commit.
- Do not reuse or erase published release-note IDs.
- Do not force-push.
