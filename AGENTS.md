# Player-facing release notes

For every completed player-visible change, add a concise bilingual note in
`changes/` in the same change. Follow `docs/player-release-notes.md` and the
existing JSON example. Describe the actual player benefit and where to find it;
do not expose class names, commit logs or implementation details.

Group related small fixes. Internal-only refactors need no player note. Never
announce unfinished work or unrelated dirty changes. Keep published note IDs
immutable; corrections need a new note. Validate with
`python tools/release_notes.py --check` before completion.

`ant release` packages notes for that exact release and updates the retained
`src/nurgling/news/releases.json` snapshot. Ordinary builds always include that
history without adding new draft notes. Never clear history after reading it.
Preserve the previous release's `release-notes.json` when
preparing a manual release. Do not publish or push without user authorization.

## Project release workflow

When Denis asks to release, publish, ship, or commit all current work and send
it, use the locally installed `nurgling-project-release` skill. This personal
skill is kept outside the repository. Invoking that
workflow authorizes its two commits, `ant release`, and final push.
