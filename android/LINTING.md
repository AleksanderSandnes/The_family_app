# Android linting & formatting

The Android module mirrors the iOS lint gate (`../.swiftlint.yml` + `../.github/workflows/swift-lint.yml`)
with the Kotlin/Android equivalents. Three layers, all enforced in CI:

| Concern | Tool | Config | Owns |
|---|---|---|---|
| Formatting | **Spotless** (ktlint `1.3.1`) | `app/build.gradle` `spotless {}`, `../.editorconfig` | whitespace, import order, indentation, trailing commas |
| Static analysis | **detekt** `1.23.8` | `config/detekt/detekt.yml` + `config/detekt/detekt-baseline.xml` | complexity, naming, correctness smells (`maxIssues: 0`) |
| Android correctness | **Android lint** | `app/build.gradle` `android.lint {}` | resource/API/manifest issues |

Formatting is owned by Spotless/ktlint; detekt's formatting ruleset is deliberately **not** used
(same split as SwiftFormat vs SwiftLint on iOS).

## Everyday commands (run from `android/`)

```bash
./gradlew spotlessApply        # auto-fix Kotlin formatting
./gradlew spotlessCheck        # verify formatting (CI gate — never writes)
./gradlew detekt               # static analysis (maxIssues: 0)
./gradlew lintDebug            # Android lint
./gradlew testDebugUnitTest    # JVM unit tests
```

`./gradlew detektBaseline` regenerates `config/detekt/detekt-baseline.xml` to absorb pre-existing
debt. The baseline is currently empty — the tree is clean; keep it that way and only baseline
genuine legacy debt, never new code.

## Conventions / known ktlint quirks

- **`@Composable` PascalCase functions**: ktlint's `standard:function-naming` rule *crashes*
  (throws `AssertionError`) on them in this version. Files that declare Composables start with
  `@file:Suppress("ktlint:standard:function-naming")` before the `package` line. detekt's
  `FunctionNaming` is already configured to ignore `@Composable`.
- **File-header comments** that aren't attached to a declaration must use `/* … */`, not a
  KDoc `/** … */` — a dangling KDoc crashes ktlint's `no-consecutive-comments`/kdoc rules.
- **Magic numbers**: detekt ignores them inside `@Composable` and in property declarations. For a
  small static lookup table or bit-mask in a plain function, use a scoped
  `@Suppress("MagicNumber")` with a one-line justification, or name the value `private const val`.

## CI & pre-commit

- **CI**: `../.github/workflows/android-lint.yml` runs `spotlessCheck → detekt → lintDebug →
  testDebugUnitTest` on pushes/PRs to `test`/`main`/`master` that touch `android/**`. All are hard
  gates. (Mirrors `swift-lint.yml`.)
- **Pre-commit**: `../.pre-commit-config.yaml` adds `spotless-kotlin` (autofix) and `detekt-kotlin`
  (gate) hooks alongside the Swift hooks. Install with `pre-commit install`.
