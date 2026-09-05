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

## UI modernization (2026-09, branch `win11-ui`)

A full pass over the Swing/Flamingo UI layer, adding a Windows 11 style
theming + DPI/font-scaling foundation and fixing a series of long-standing
Flamingo ribbon rendering bugs.

**New UI framework** (`freeplane/src/org/freeplane/core/ui/`):

| Component | Purpose |
|-----------|---------|
| `DocearUiTokens` / `DocearUiMetrics` | Central design tokens (UNIT, GAP, ICON_SIZE, …) and a global `fontScale()` multiplier applied to every scaled UI dimension. |
| `UiFontScale` | Reads `-Ddocear.ui.fontscale=` and persists the user setting; scales every `*.font` UIManager key. |
| `DocearUiDefaults` / `DocearUiInstaller` | Installs fonts, metrics and the ribbon skin at startup; re-applies scaling on runtime UI refreshes. |
| `DocearWin11LookAndFeel` | Windows 11 Fluent-inspired skin (accent `#0067C0`, mica grey `#F3F3F3`, neutral strokes `#D6D6D6`). |
| `RibbonAppearanceDialog` / `TopBarAppearanceAction` | User-facing dialog for ribbon icon size / font scale. |

**Ribbon fixes** (`.../core/ui/ribbon/`):

- `SmoothScalingResizableIcon` (new) — Flamingo's `ImageWrapperIcon` only ever
  scales *down* (`scale > 1.0f` check in `renderImage`), so a 16 px source asked
  for 32 px kept painting a centered 16 px image inside a 32 px box. The
  replacement scales up *and* down with bilinear interpolation. This is also
  why the configured ribbon icon size previously "did nothing".
- `DocearRibbonUI.getTaskbarHeight()` now follows the configured icon size
  (`max(scale(24), iconSize + 8)`) — Flamingo's `TaskbarLayout` otherwise
  clamps every taskbar button to a fixed 24 px row and crops larger icons.
- `FlowOneRowResizePolicy` (new) — `CoreRibbonResizePolicies` has no one-row
  policy for flow bands; the Zoom band (and any future flow band) can now
  request a single row, with graceful degradation to two rows / collapsed.
- `RibbonActionContributorFactory` — strip buttons render with SMALL icon
  dimensions (no more clipped glyphs at higher font scales); popup menu
  buttons are excluded from top-bar icon scaling.
- `MenuBuilder.addMenuItem` wraps menu icons in a fit-to-`ICON_SIZE` scaling
  icon, so hi-res source PNGs (48 px) no longer inflate menu row heights.

**Other UI fixes**: workspace tree row-height overlap (`TreeView`), JabRef
entry-table font ignoring the global scale (`MainTable`), attribute/style
tables, Zoom band layout.

**Redrawn icons**: the six quick-access taskbar icons (new map, encrypt, open,
save, save as, save all) are redrawn in a unified Win11 Fluent style and are
now 48 px sources (down-scaled cleanly for strip/menu renderings, crisp at
1.0x–2.0x font scale). The generator script and before/after preview live in
the working tree's `laf_skin_research/redraw_win11_icons.py` (not committed).

## Wiki

The fork maintains a GitHub Wiki with contributor-focused docs that don't
belong in the README:

- [**Build, Runtime and Architecture**](https://github.com/LiYangChem/Desktop/wiki/Build,-Runtime-and-Architecture)
  — required toolchain, runtime requirements, OSGi bundle layering, and a
  module-by-module directory map.
- [**UI Modernization & Flamingo Internals**](https://github.com/LiYangChem/Desktop/wiki/UI-Modernization-and-Flamingo-Internals)
  — how the Docear UI scaling system works and the Flamingo ribbon/icon
  mechanics discovered while rebuilding it (icon pipeline, taskbar layout,
  band width allocation, resize policies, probe techniques).
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
