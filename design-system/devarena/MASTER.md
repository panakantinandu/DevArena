# DevArena design system: MASTER ("Neubrutalist Arena")

Source of truth for every screen. Chosen from the ui-ux-pro-max skill
(the open-source ui-ux-pro-max design skill) after the first direction was rejected for looking like a generic AI template.

## Why this direction

The first version used dark navy with violet and cyan accents and a glow. That is the default look of AI-generated dashboards, so it was replaced.

Three skill-backed directions were offered (Neubrutalism, Swiss scoreboard, editorial paper), and the owner picked **Neubrutalism**:
- **Style** (`--domain style` → "Neubrutalism"): hard black outlines, flat poster colours, hard offset shadows with no blur, bold type, no gradients.
- **Fonts** (`--domain typography` → "Neubrutalist Bold"): Lexend Mega for headings and Public Sans for body text, plus JetBrains Mono for code.
- **Texture**: a dot-grid section pattern, from the skill's Bauhaus and Neubrutalism notes.

## Themes

Both themes share the same poster accents. A three-way toggle (light, dark, system) lives in the sidebar, the mobile top bar, the auth screens and Settings. The choice is saved in `localStorage` (`devarena-theme`) and applied before first paint by an inline script, so there's no flash of the wrong theme.

| Token | Light (cream paper) | Dark (charcoal) | Use |
| --- | --- | --- | --- |
| `--bg` | `#F4EFE3` | `#171614` | page |
| `--surface-1/2/3` | `#FFFDF7` / `#EFE8D8` / `#E5DCC6` | `#211F1C` / `#2B2925` / `#37342F` | panels, hover, wells |
| `--line` | `#111111` ink | `#F4EFE3` cream | all outlines (2px) |
| `--shadow` | `#111111` | `#F4EFE3` | hard offset shadows |
| `--text` / `--muted` | `#111111` / `#57534A` | `#F4EFE3` / `#B8B1A2` | text |
| `--brand` | `#FFD23F` signal yellow | same | primary fill, active nav, selection |
| `--brand-text` | `#1D4ED8` ink blue (6.1:1) | `#FFD23F` (12:1) | links and accents as text |
| `--live` | `#FF5A36` tomato | same | live, secondary fill, "Join" |
| `--live-text` | `#B42D14` (5.6:1) | `#FF8A6B` | live accents as text |
| success / warning / danger | `#15803D` / `#92600A` / `#C81E1E` | `#4ADE80` / `#FFD23F` / `#FF7070` | status |

Fills such as yellow, tomato and the difficulty tags (easy `#86EFAC`, medium `#FFD23F`, hard `#FF8A70`) always carry **ink text (#111)** in both themes, which gives more than 6:1 contrast everywhere. Difficulty is always written out, never shown by colour alone.

## Typography

| Role | Font | Notes |
| --- | --- | --- |
| Display (h1, big numbers, hero) | Lexend Mega 600–800 | very wide: display sizes only, letter-spacing −0.045em |
| Headings h2–h4, UI, body | Public Sans 400–800 | h2/h3 at 800 weight |
| Code, timers, micro-labels | JetBrains Mono 400–700 | `tabular-nums` for scores |

All fonts are self-hosted with `@fontsource`.

## Shape, depth and motion

- **2px outlines on everything** interactive or containing.
- **Hard shadows**: panels `4px 4px 0` and buttons `3px 3px 0`, in `--shadow`. Arena frames use a yellow `6px` shadow (`.brackets`).
- **Press physics**: buttons lift 1px on hover and snap down 3px (shadow removed) when pressed.
- Radius 6px for controls and 8px for panels. Avatars are round.
- Motion is limited to 100–150ms transforms. Nothing animates during interviews or coding, and everything is disabled under `prefers-reduced-motion`.

## Components

- **Buttons**: `primary` (yellow), `live` (tomato), `secondary` (surface), `ghost`, `danger`. All except ghost are pressable.
- **Inputs**: 2px outline. Focus adds a yellow hard shadow; errors add a red shadow and the message appears below the field.
- **Question options**: outlined rows. Selected = yellow, correct = green with a check icon, wrong = red with an X icon. Screen-reader text states correctness.
- **Navigation**: the active item is a yellow pill with an outline and shadow.
- **Charts and diagrams** read theme variables (`var(--brand)`, `var(--line)` and so on), so they switch with the theme.
- **Monaco** has matching `devarena-light` and `devarena-dark` themes.

## Accessibility checklist

3px focus outline in `--brand-text` · labels on every input · `aria-label` on icon buttons · text contrast at least 4.5:1 (checked per token above) · touch targets at least 44px · the theme toggle is a labelled radio group · `aria-live` on timers and round results · no hover-only affordances.
