# LiYangChem Fork

This repository is a personal fork of the upstream
[BeelGroup/Docear-Desktop](https://github.com/BeelGroup/Docear-Desktop)
maintained by **@LiYangChem** on GitHub.
Upstream contents above are kept verbatim; everything below is fork-specific.

## What this fork adds

The fork carries one focused experiment: replacing the legacy `MetaDataSearchHub`
pipeline in `docear_plugin_bibtex` with a cleaner, Twigmark-style **Metadata
Provider** architecture. Two new public sources — **Crossref** and **DOI** —
are wired into the metadata extractor wizard alongside Google Scholar and Docear.

Changes since the last upstream sync (squashed into 3 commits, see `git log`):

| Commit    | Scope | Files |
|-----------|-------|-------|
| `b6ed1cb` | Bump build numbers / dates to 2026-09-02 (no functional impact) | 5 |
| `4eafd91` | Wire Crossref + DOI sources into `MetaDataExtractorPage` / `MetaDataOptionsPage`; enable them by default; rebuild `docear-metadata-lib-0.0.1.jar` | 5 |
| `c5b407f` | Add new metadata packages `model/`, `io/`, `match/`, `adapter/`, `providers/` plus `MetadataCandidateAggregator` and `docs/` | 27 |

Detailed design notes live in [`docs/`](docs/):

- `docs/metadata-provider-architecture.md` — overall pipeline design.
- `docs/metadata-retrieval-analysis.md` — background research.
- `docs/metadata-provider-phase1.md` / `phase2.md` / `phase3.md` — incremental dev logs.
- `docs/portable-release.md` — release/distribution notes.

## Wiki

The fork maintains a GitHub Wiki with contributor-focused docs that don't
belong in the README:

- [**Build, Runtime and Architecture**](https://github.com/LiYangChem/Desktop/wiki/Build,-Runtime-and-Architecture)
  — required toolchain, runtime requirements, OSGi bundle layering, and a
  module-by-module directory map.
- [Wiki home](https://github.com/LiYangChem/Desktop/wiki) — sidebar + index.

The wiki lives in a separate `Desktop.wiki` git repo and is published via
the standard GitHub Wiki page. To edit it locally:

```
git clone https://github.com/LiYangChem/Desktop.wiki.git
# edit *.md, git commit, git push
```

## Build

The project is built with Apache Ant (Java 8 source level). From the repo root:

```
# full distribution build (matches upstream `ant dist`)
ant dist
```

Build outputs are written under `*/dist`, `*/build`, `freeplane_framework/launcher_build`,
etc. — all of these are covered by the existing `.gitignore`.

Common module-level entry points (run from repo root unless noted):

| Task                          | Command |
|-------------------------------|---------|
| Build everything              | `ant dist` |
| Clean                         | `ant clean` |
| Reformat translations         | `ant format-translation` |
| Build a single plugin         | `ant -f <plugin_dir>/build.xml dist` |
| Build freeplane framework     | `ant -f freeplane_framework/ant/build.xml dist` |

To produce a runnable Docear distribution use `ant dist` and then look under
`freeplane_framework/launcher_build/` (Windows) or `freeplane_framework/build4mac/`
(macOS) for the launcher.

## Keeping in sync with upstream

This fork is configured with two remotes (only `origin` is used for pushes):

- `origin`   → `https://github.com/LiYangChem/Desktop.git`  *(this fork)*
- `upstream` → `https://github.com/BeelGroup/Docear-Desktop.git`  *(canonical source)*

Pull new upstream changes into a working branch, then review and merge — note
that this fork's local history is **squashed** into a single commit
(`3981a30`, the original `master` snapshot) before the new `[Docear-Mod]`
commits, so a straight `git merge upstream/master` will be rejected. Preferred
workflow:

```
# fetch and create a tracking branch from upstream
git fetch upstream master:upstream-master
git checkout -b sync/upstream-$(date +%Y%m%d) upstream-master

# replay this fork's `[Docear-Mod]` commits on top
git cherry-pick b6ed1cb 4eafd91 c5b407f
# resolve any conflicts, then fast-forward `master` if everything builds clean
```

For low-risk merges, do this in a scratch branch first and only fast-forward
`master` after `./ant dist` succeeds.

## Repository layout (fork-specific additions only)

```
docear-desktop/
├── docear_metadata/src/main/java/org/docear/metadata/
│   ├── adapter/      # SearchEngine/Source/Extractor wrappers per provider
│   ├── io/           # JSON / CSL / BibTeX parsing & conversion
│   ├── match/        # title/author fuzzy matching + score aggregation
│   ├── model/        # canonical BibliographicMetadata, MetadataCandidate, MetadataQuery
│   └── providers/    # MetadataProvider interface + Crossref/DOI/GoogleScholar impls
├── docear_plugin_bibtex/src/org/docear/plugin/bibtex/dialogs/
│   └── MetadataCandidateAggregator.java   # score + de-dupe candidates from all sources
└── docs/             # design notes, phase logs, retrieval analysis (see above)
```

Everything else under `docear-desktop/` is unchanged from upstream.

## Contact

Open an issue on this fork or
ping `@LiYangChem` on GitHub. Upstream issues still belong on
[BeelGroup/Docear-Desktop/issues](https://github.com/BeelGroup/Docear-Desktop/issues).

For build setup, runtime requirements, and the OSGi bundle layout, see the
[**Wiki → Build, Runtime and Architecture**](https://github.com/LiYangChem/Desktop/wiki/Build,-Runtime-and-Architecture)
page.
