# iOS Linting & Formatting

Strict-but-low-noise linting/formatting for the native iOS SwiftUI app. Two
tools, each owning a lane:

| Tool           | Owns                                                        | Config (repo root)   |
| -------------- | ----------------------------------------------------------- | -------------------- |
| **SwiftLint**  | Semantic/style lint: force-unwrap, naming, dead code, TODOs | `.swiftlint.yml`     |
| **SwiftFormat**| Whitespace/layout: indent, wrapping, import order, commas   | `.swiftformat`       |

Both are scoped to `ios/FamilyApp` + `ios/FamilyAppTests` only — `android/`,
`supabase/`, and `maestro/` are never touched. Configs live at the **repo root**
so one copy serves the Xcode build phase, pre-commit, and CI.

---

## Install (one time)

```sh
brew install swiftlint swiftformat pre-commit
pre-commit install          # wires the git hook
```

Everything degrades gracefully if the tools are missing (the Xcode build phase
just prints a warning), so a teammate without them can still build.

---

## Run locally

```sh
# From the repo root.

# Lint (report only — never changes files). Run from the repo root and do NOT pass
# --config, so SwiftLint auto-merges the root .swiftlint.yml with the nested
# ios/FamilyAppTests/.swiftlint.yml override (--config disables nested discovery):
swiftlint lint --strict
swiftformat --lint ios/FamilyApp ios/FamilyAppTests --config .swiftformat

# Autofix (writes files):
swiftformat ios/FamilyApp ios/FamilyAppTests --config .swiftformat   # layout
swiftlint --fix                                                      # safe lint fixes

# Deep analyzer pass (dead code / unused imports — needs a build log):
swiftlint analyze --config .swiftlint.yml
```

> ⚠️ Run the **autofix** commands only when you own the tree — a full
> `swiftformat` pass currently rewrites ~57 of 76 files (the code predates the
> formatter). Do it as one dedicated "format baseline" commit, not mixed into
> feature work.

---

## The four integration points

1. **Xcode build phase** (`ios/project.yml` → `preBuildScripts`): runs
   `swiftlint lint` on every build, **report-only** (`|| true`) so violations show
   up as inline warnings in Xcode during development without interrupting the
   build. The hard enforcement lives in pre-commit + CI. After editing
   `project.yml` you must regenerate:
   ```sh
   cd ios && xcodegen generate
   ```
   *Caveat:* the target sets `ENABLE_USER_SCRIPT_SANDBOXING: YES`; if Xcode's
   sandbox blocks SwiftLint from reading the tree the phase just no-ops — pre-commit
   and CI are the authoritative gates regardless.

2. **Pre-commit** (`.pre-commit-config.yaml`): on `git commit`, runs — on
   **staged Swift files only** — SwiftFormat (autofix) → SwiftLint `--fix` →
   SwiftLint `lint --strict` (blocks the commit on any remaining violation).
   Bypass in a pinch with `git commit --no-verify`.

3. **CI** (`.github/workflows/swift-lint.yml`): on push to `test`/`main`/`master`
   and PRs targeting them. **Hard gate** — `swiftlint lint --strict` (every
   violation, warning or error, fails the job) plus `swiftformat --lint` (any
   formatting diff fails the job). Neither passes `--config`, so SwiftLint merges
   the nested test override.

4. **Analyzer** (optional, manual/CI): `swiftlint analyze` surfaces
   `unused_import` / `unused_declaration`.

---

## The gate is HARD

The tree has been cleaned (autoformatted + all `--strict` violations fixed), so
enforcement is on everywhere:

- **CI** fails on any SwiftLint violation or SwiftFormat diff.
- **Pre-commit** blocks the commit on any remaining violation (bypass in a true
  pinch with `git commit --no-verify`).
- **Xcode build phase** runs `swiftlint lint --strict` on every build and fails
  the build on a violation (see the sandbox caveat in point 1).

Keep it clean: run `swiftformat …` + `swiftlint --fix` before committing, then let
`swiftlint lint --strict` confirm zero. Don't add `disabled_rules` or inline
`swiftlint:disable` to dodge a finding — fix the code (the one sanctioned inline
disable is the UIKit-imposed `didFinishLaunchingWithOptions` signature).

---

## Why these rules (the short version)

- **No force-unwrap / force-cast / force-try** (`force_unwrapping`, etc.) — the
  app is async and crash-averse; make optionality explicit. Relaxed for the
  **test target** (`ios/FamilyAppTests/.swiftlint.yml`), where `x!` in a fixture
  fails loudly and readably.
- **Small units** — `file_length` (warn 500), `function_body_length` (warn 60),
  `type_body_length` (warn 350 / error 700), `cyclomatic_complexity`,
  `nesting` all nudge toward extraction. `ChatViewModel` (~612-line body) is the
  one flagged god-object worth splitting.
- **Clear naming** — `identifier_name` / `type_name` with sensible min/max, plus
  a whitelist of idiomatic short names (`id`, `x`, `y`, `to`, `db`, `lhs`, …).
- **Explicit access control is DISABLED** (`explicit_acl`,
  `explicit_top_level_acl`) — this is an app target, not a library. All 174
  top-level types use implicit `internal` by design; requiring explicit ACL
  would be 500+ pure-noise violations. Intent is already carried by the 500+
  `private` members.
- **Modern concurrency** — `unowned_variable_capture` (prefer `[weak self]`) and
  custom rules discouraging `print(`, `NSLog`, and `DispatchQueue.main.async`
  (all currently zero — guardrails against regression, not a backlog).
- **No dual ownership** — `sorted_imports` and `trailing_comma` are **disabled in
  SwiftLint** because SwiftFormat owns import sorting (`sortImports`) and trailing
  commas (`--commas always`); letting both fix them would cause a tug-of-war.
- **TODO/FIXME** are warnings (custom `todo_without_ticket`).

Every non-default choice has an inline `# why` comment in `.swiftlint.yml` /
`.swiftformat`. Start there when tuning.
