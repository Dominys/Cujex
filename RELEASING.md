# Releasing Cujex

This is the maintainer process for cutting a release. Publishing is **fully
tag-driven**: `main` always carries a `-SNAPSHOT` version, and pushing a
`vX.Y.Z` tag builds, signs, and publishes the libraries to Maven Central and
opens the matching GitHub Release.

Only `cujex-core`, `cujex-kafka`, and the parent POM are published. The
`examples/` and `cujex-coverage` modules are build/demo only and never leave the
reactor.

## Versioning rule (SemVer on the public API)

Cujex versions its **public API**: the published modules' public types, step
definitions, JEXL functions, and configuration properties. Pick the next number
by the nature of the change, not by effort:

| Bump | When | Example |
|------|------|---------|
| **PATCH** (`1.0.0 → 1.0.1`) | Backward-compatible bug fixes only. No new API. | Fix a JEXL edge case. |
| **MINOR** (`1.0.0 → 1.1.0`) | Backward-compatible new features. Existing suites keep compiling and passing. | Add a new step or JEXL function. |
| **MAJOR** (`1.0.0 → 2.0.0`) | Any breaking change to the public API. | Rename/remove a public type, change a step signature, drop a property. |

`-SNAPSHOT` on `main` is never published; it only marks the in-development line.

## Day-to-day (every PR)

Add a bullet under the `## [Unreleased]` section of
[`CHANGELOG.md`](CHANGELOG.md) describing the change for users, grouped under
`Added` / `Changed` / `Fixed` / `Removed` (see
[Keep a Changelog](https://keepachangelog.com)). This is the single source of
truth for release notes — the GitHub Release is generated from it.

## Cutting a release

1. **Finalize the changelog.** On `main`, move the `## [Unreleased]` entries into
   a new dated section and reopen an empty `[Unreleased]`:

   ```markdown
   ## [Unreleased]

   ## [1.1.0] - 2026-11-15
   ...moved entries...
   ```

   Update the link references at the bottom:

   ```markdown
   [Unreleased]: https://github.com/Dominys/Cujex/compare/v1.1.0...HEAD
   [1.1.0]: https://github.com/Dominys/Cujex/releases/tag/v1.1.0
   ```

   Commit and push to `main`.

2. **Tag the release.** The tag name is the exact version (no `-SNAPSHOT`), and
   must be `vX.Y.Z` — the workflow refuses anything else:

   ```bash
   git checkout main && git pull
   git tag v1.1.0
   git push origin v1.1.0
   ```

3. **Let the `Release` workflow run.** On the tag push it:
   - stamps the reactor to `1.1.0` (build only — not committed back to `main`),
   - builds, signs (GPG), and publishes `cujex-core` + `cujex-kafka` + parent to
     the Central Portal with `autoPublish=true`,
   - creates the GitHub Release for the tag using the matching `CHANGELOG.md`
     section as the notes.

4. **Verify on Maven Central** once the Central Portal deployment flips from
   *Publishing* to *Published* (a few minutes; search indexing can lag longer):

   ```bash
   curl -sI https://repo1.maven.org/maven2/io/github/dominys/cujex-core/1.1.0/cujex-core-1.1.0.pom | head -1
   ```

   A `200` means it is live. Repeat for `cujex-kafka` and `cujex-parent`.

5. **Reopen `main` for development.** Bump every module to the next snapshot:

   ```bash
   ./mvnw versions:set -DnewVersion=1.2.0-SNAPSHOT -DprocessAllModules=true -DgenerateBackupPoms=false
   git commit -am "Open 1.2.0-SNAPSHOT for development"
   git push origin main
   ```

## Required repository secrets

The `Release` workflow needs these secrets (Settings → Secrets and variables →
Actions):

| Secret | Purpose |
|--------|---------|
| `CENTRAL_USERNAME` | Central Portal token username. |
| `CENTRAL_PASSWORD` | Central Portal token password. |
| `GPG_PRIVATE_KEY` | ASCII-armored private key used to sign artifacts. |
| `GPG_PASSPHRASE` | Passphrase for that key. |

## Notes & gotchas

- **Never publish from a local machine.** The tag workflow is the only release
  path; local `deploy` would bypass signing/version checks.
- **A tag is immutable once published.** With `autoPublish=true`, a bad tag is a
  real release. Double-check the changelog and version before pushing the tag.
- **Only `vX.Y.Z` tags release.** Other tags (e.g. `vnext`, `v1.2`) are rejected
  by the workflow guard before anything is published.
- **`main` is never committed back by the release.** The concrete version exists
  only in the tagged build; `main` stays on `-SNAPSHOT`.
