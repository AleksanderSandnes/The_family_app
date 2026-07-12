---
name: The Family App
description: One shared home for the family — Liquid Glass surfaces over a calm ambient canvas, on Android and iOS.
colors:
  heirloom-indigo: "#5457E8"
  heirloom-indigo-bright: "#6366F1"
  heirloom-indigo-deep: "#4338CA"
  violet: "#7C3AED"
  interactive-accent: "#4F55E6"
  interactive-accent-dark: "#A5ABFF"
  ambient-base-light: "#EFF1F8"
  ambient-base-dark: "#0B0D16"
  ink: "#16192A"
  ink-dark: "#ECEEF8"
  text-secondary: "#5F6780"
  text-secondary-dark: "#98A0BC"
  caption: "#8B92AC"
  caption-dark: "#7C84A3"
  destructive: "#E11D48"
  live-green: "#10B981"
  warning-amber: "#F59E0B"
  danger-rose: "#F43F5E"
  accent-pink: "#EC4899"
  accent-teal: "#14B8A6"
typography:
  display:
    fontFamily: "system sans (Roboto / SF Pro)"
    fontSize: "34sp"
    fontWeight: 700
    lineHeight: 1.18
    letterSpacing: "-0.5sp"
  headline:
    fontFamily: "system sans (Roboto / SF Pro)"
    fontSize: "28sp"
    fontWeight: 700
    lineHeight: 1.21
    letterSpacing: "-0.4sp"
  title:
    fontFamily: "system sans (Roboto / SF Pro)"
    fontSize: "17sp"
    fontWeight: 600
    lineHeight: 1.35
  body:
    fontFamily: "system sans (Roboto / SF Pro)"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "system sans (Roboto / SF Pro)"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: 1.29
    letterSpacing: "0.1sp"
rounded:
  badge: "12dp"
  small: "14dp"
  field: "16dp"
  button: "18dp"
  card: "20dp"
  overview-card: "22dp"
  big-card: "26dp"
  sheet: "28dp"
  tab-bar: "33dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "20dp"
  xxl: "24dp"
  xxxl: "32dp"
  screen-edge: "20dp"
  card-padding: "18dp"
components:
  button-primary:
    backgroundColor: "{colors.heirloom-indigo}"
    textColor: "#FFFFFF"
    rounded: "{rounded.button}"
    padding: "14dp 24dp"
  card-glass:
    rounded: "{rounded.card}"
    padding: "{spacing.card-padding}"
  input-field:
    rounded: "{rounded.field}"
    padding: "14dp 16dp"
  tab-bar:
    rounded: "{rounded.tab-bar}"
  badge-feature:
    rounded: "{rounded.badge}"
---

# Design System: The Family App

## 1. Overview

**Creative North Star: "The Glass House"**

The Family App is a warm family home seen through calm glass. Every screen is built from translucent, softly blurred surfaces floating over an ambient canvas — three radial washes of violet (top-right), indigo (left), and teal (bottom) over a near-white or near-black base. The glass is the identity: content sits on it, chrome floats as it, and completed things fade out of it. The mood is warm, premium, calm — a household that has its act together, never a dashboard and never a toy.

The system ships identically on two native platforms: Jetpack Compose (with Haze blur, `Glass.kt`) and SwiftUI (`Glass.swift`), sharing the same hex values, radii, spacing, and channel of restraint. Brand lives in the material and the light, not in loud color: large surfaces are always neutral glass, and hue appears only in small, meaningful doses. This system explicitly rejects the kids'-app cartoon look, the generic Material 3 scaffold, and gray corporate utilitarianism.

**Key Characteristics:**
- Translucent glass surfaces over a three-wash ambient canvas, in both light and dark
- One quiet interactive accent (Heirloom Indigo); feature hues confined to badges and dots
- Generous, differentiated corner radii (12–33dp) as the main shape voice
- Platform system type (Roboto / SF Pro) on a tight, iOS-flavored scale
- Depth by material — blur and translucency first, soft ambient shadow second
- Graceful degradation: below Android API 31 glass becomes a translucent solid and the UI stays legible

## 2. Colors

A restrained palette: neutral glass carries the surface, Heirloom Indigo carries interaction, and a small family of feature accents provides wayfinding in badge-sized doses.

### Primary
- **Heirloom Indigo** (#5457E8, bright variant #6366F1, deep #4338CA): the brand's voice. Primary buttons, active states, links, the indigo ambient wash. As `interactive-accent` (#4F55E6) it is the single interactive tint; in dark theme it brightens to #A5ABFF so glyphs and text stay legible on ink.

### Secondary
- **Violet** (#7C3AED): the brand's second note. The top-right ambient wash, the far end of the sanctioned brand gradient (#5457E8 → #7C3AED), wishlist and family feature accents, secondary containers (#EDE4FF light / #4C1D95 dark).

### Tertiary
- **Feature accents** — one identity hue per room of the house, used only in icon badges and calendar dots: Shopping/Chat indigo (#6366F1), Meals amber (#F59E0B), Calendar live-green (#10B981), Birthdays pink (#EC4899), Wishlists violet (#8B5CF6), Map teal (#14B8A6).
- **Status** — success #10B981, warning #F59E0B, danger #F43F5E, destructive rows and irreversible actions #E11D48.

### Neutral
- **Ambient bases** (#EFF1F8 light / #0B0D16 dark): the canvas under the washes; never seen bare across a whole screen.
- **Ink ramp (text)** — primary #16192A / #ECEEF8, secondary #5F6780 / #98A0BC, caption #8B92AC / #7C84A3. Secondary text uses Slate600-strength values because Slate500 misses WCAG AA on the light canvas; caption-dark and the status text colors (live-green text #047857, week-amber text #92400E) were darkened/lifted in the 2026-07 polish pass for the same reason.
- **Dark surfaces** — cards #141A2A, nested containers #1E2638, borders #2A3349.

### Named Rules
**The Badge Rule.** Feature accents live only in icon badges and calendar event dots — never on large surfaces. A screen's identity comes from its glass, not from flooding it with its feature hue.

**The One Gradient Rule.** The brand gradient (#5457E8 → #7C3AED) is the only sanctioned gradient and may appear only on identity surfaces: hero headers, outgoing chat bubbles, the brand primary CTA, and the Home family banner. Never as a generic card or background fill.

## 3. Typography

**Display Font:** System sans — Roboto on Android, SF Pro on iOS
**Body Font:** Same family; the system speaks in one voice

**Character:** Confident and plain-spoken. The brand is carried by glass and light, not by a display face; type stays quiet, slightly tight at large sizes, and always the platform's own.

### Hierarchy
- **Display** (Bold 700, 34sp / 40, −0.5 tracking): screen heroes and large titles only.
- **Headline** (Bold 700, 28sp / 34, −0.4; medium 23sp, small SemiBold 19sp): section heads and card heroes.
- **Title** (SemiBold 600, 17sp / 23; medium 15sp): card titles, list-row primaries — the workhorse.
- **Body** (Regular 400, 16sp / 24; medium 14sp / 20): content and descriptions.
- **Label** (SemiBold 600, 14sp / 18, +0.1; medium 500, 12sp, +0.4): buttons, chips, captions, meta.

### Named Rules
**The One Voice Rule.** One family, weights 400–700, nothing else. No display faces, no mono, no decorative type. Emphasis is weight and size, never a second font.

**The sp Rule.** All type in sp (Android) / Dynamic Type-relative (iOS) so it follows the user's system text size. Hard-coded pixel sizes are prohibited.

## 4. Elevation

Depth is conveyed by material, not by shadow. A surface reads as "above" the canvas because it blurs what is behind it (Haze `regular` for cards, `thin` for chrome); the soft shadow underneath (ambient tint #141A3C at 12% alpha, 22% when accent-tinted) is a finish, never the structure. Every glass surface carries a 1.5dp hairline — 10% white in dark, 10% ink in light — so cards stay distinguishable when the wash behind them is near-white. Two elevation steps exist: resting (2dp) and raised (6dp, chrome/FAB/sheets). Below API 31 the blur is replaced by a translucent solid fill (94% white / 92% ink surface) and the same hairline, so depth survives without RenderEffect.

### Named Rules
**The Ghost Rule.** Completed and empty things recede by losing their material: a ghost surface has no blur, no shadow, a faint translucent fill, and a dashed 1dp hairline. Done items don't get louder decoration — they get less.

**The No Structural Shadow Rule.** If removing the shadow makes the layout unreadable, the surface is wrong. Fix the material, not the shadow.

## 5. Components

Soft and assured: generous radii, gentle glass, confident type — calm but never mushy.

### Buttons
- **Shape:** softly rounded (18dp radius, `Radius.button`)
- **Primary:** the brand CTA carries the sanctioned brand gradient (#5457E8 → #7C3AED) with white label text (SemiBold 14sp); standard primaries use Heirloom Indigo fill
- **Pressed / Disabled:** press dims the surface, never scales wildly; disabled drops to the ghost treatment, not gray-out
- **Destructive:** #E11D48 text or fill; reserved for irreversible actions

### Cards / Containers
- **Corner Style:** a deliberate ladder — 20dp workhorse cards (`glassCard`), 22dp overview cards, 26dp hero/family cards, 28dp sheets
- **Background:** glass — blurred ambient wash behind a translucent material; accent-tinted glass (16% tint alpha) marks selected/active surfaces
- **Shadow Strategy:** soft ambient only (see Elevation); 1.5dp hairline edge always
- **Internal Padding:** 18dp (`Spacing.cardPadding`), 12dp gaps between cards

### Inputs / Fields
- **Style:** glass or surface fill, 16dp radius (`Radius.field`), 14–16dp internal padding
- **Focus:** interactive-accent (#4F55E6 / #A5ABFF dark) border or tint shift — one accent, quietly
- **Error:** danger rose (#F43F5E) message and outline; never a red fill

### Navigation
- **Floating glass tab bar** (33dp radius, `glassBar`): blurs both the ambient wash and the content scrolling beneath it; active tab glyph in accent (#4F55E6 light / #C9CDFF dark). Bottom tabs crossfade; detail screens slide horizontally (300ms slide, 200ms fade).
- **Segmented controls:** glass track (14dp radius) with an 11dp-radius floating thumb.

### Feature Badge (signature)
The app's wayfinding atom: a 12–14dp-radius rounded square with a translucent feature-accent fill and the feature's icon stroked in its accent (darker in light theme, brighter in dark). Home-grid tiles, list headers, and calendar dots all derive from it. This is the only place feature color is allowed to live.

### Ghost Surface (signature)
Dashed 1dp hairline, faint translucent fill, no shadow, no blur. Used for completed shopping items, unplanned meal days, and empty slots — the visual language of "done or not yet".

## 6. Do's and Don'ts

### Do:
- **Do** build every screen inside `AmbientBackground` and compose surfaces from the glass helpers (`glassCard`, `glassChrome`, `glassBar`, `ghostSurface`) — never hand-roll a blur or a card style.
- **Do** use `MaterialTheme.colorScheme.*` / semantic tokens for all color, and `Spacing` / `Radius` tokens for all metrics; 20dp screen edges, 18dp card padding, 4-pt grid.
- **Do** keep secondary text at #5F6780 (light) / #98A0BC (dark) or stronger — Slate500-strength grays fail WCAG AA on the light canvas.
- **Do** design and test both themes and both platforms; the hex values, radii, and channel names are shared contracts (`Glass.kt` ↔ `Glass.swift`) — change them in pairs.
- **Do** verify every glass surface is legible in the API < 31 fallback (translucent solid, no blur).

### Don't:
- **Don't** let it read as a **kids' app / cartoon** — no mascots, no bubbly primary-color chrome; adults must feel this is a serious, premium tool.
- **Don't** let it read as a **generic Material template** — a stock M3 scaffold with default surfaces and tonal chips is off-brand; the glass identity must be present on every screen.
- **Don't** let it read as a **corporate productivity tool** — no dense enterprise chrome, no gray utilitarianism, no urgency theater.
- **Don't** put feature accents on large surfaces (the Badge Rule) or add any gradient beyond the sanctioned brand gradient (the One Gradient Rule).
- **Don't** use structural shadows, opaque hard-edged cards, or a second font family.
- **Don't** hardcode colors, dp paddings, or px font sizes at call sites — if it isn't a token, it doesn't ship.
