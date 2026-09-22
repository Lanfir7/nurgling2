# Publishing a prepared release

`ant release` is run locally once to prepare and commit `release/`, including
the legacy version manifest and retained player-news history. Publishing does
not run Ant and does not change `build.num`.

Pushing a committed `release/ver` to `master` automatically publishes that
prepared release. It uses the push commit and reads its exact version from
`release/ver`; it never runs Ant or changes `build.num`. The manual **Publish
Existing Release to Latest** workflow remains available from `master` when an
explicit full 40-character release SHA and exact version are needed. Both paths
accept only a SHA that is already an ancestor of `master`, verify every manifest
hash, and reject a version older than the currently published channel.

When `MANIFEST_SIGNING_KEY` is configured, publishing also downloads the pinned
cross-platform updater assets, verifies their `SHA256SUMS`, and adds a signed
`manifest.json` for Windows, macOS and Linux. This augments the already prepared
release without rebuilding it or changing its version. The running legacy
`nurgling_launcher.jar` stays available for old installations but is excluded
from both update manifests. Without the signing key, publishing remains on the
verified legacy path.

Updater binaries currently come from the public releases of
`aleksandrsvoboda/nurgling-release`; `RELEASE_REPO` remains
`Lanfir7/nurgling-release`. Promotion verifies both manifests when the signed
one is present.

After publishing `latest`, the workflow fast-forwards an existing `stable`
channel to the same verified commit. **Promote Verified Latest to Stable** is
kept for manual retry from `master`, with the full commit SHA currently at
`nurgling-release/latest`. Both paths stop on divergence.

`master` is mirrored to the legacy `next` branch by a separate fast-forward-only
workflow, keeping existing update feeds available while the migration is in
place. A divergent `next` branch is never overwritten.

The cross-repository `RELEASE_REPO_SSH_KEY` secret is a dedicated write deploy
key for `Lanfir7/nurgling-release`. If it is absent or invalid, publish the
prepared artifacts locally with an authenticated checkout, repair the scoped
repository secret through repository administration, and rerun the manual
workflow. Do not copy a personal access token into Actions YAML, workflow
inputs, logs, or commits.
