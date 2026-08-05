# Material 3 Design System

A code design system extracted from the **Material 3 Design Kit (Community)** Figma file
that was attached to this project. It carries the kit's token collections, its 24px system
icon set, React recreations of the component families in scope, and two adaptive-layout UI
kits built from those components.

> This is a recreation of a **community-maintained Figma kit** describing Material Design 3,
> an open design specification. It is not a company brand kit: the file contains no logo, no
> wordmark, no brand illustrations and no product screens. Nothing has been invented to fill
> those gaps — see **Known gaps** at the end.

## Sources

| Source | What was taken from it |
| --- | --- |
| `Material 3 Design Kit (Community).fig` (attached, mounted read-only) | All tokens, all component specs, all 120 icons. 74 frames were in scope: Styles, Shape, Icons, Avatars, App bars, Buttons, Cards, Checkboxes, Chips, Dialogs, Dividers, Lists, Loading/progress, Menu, Navigation, Radio button, Search, Sheets, Sliders, Snackbar, Switch, Tabs, Text fields, Toolbars, Tooltips. |
| `uploads/Google_Sans/`, `uploads/Google_Sans,Google_Sans_Code/`, `uploads/Flow_Circular/` | Font binaries, self-hosted in `assets/fonts/`. |
| <https://m3.material.io/components> (user-supplied reference) | Used only to confirm role names and to fill the motion tokens the .fig carries no variables for. |

The .fig was mounted as a virtual filesystem rather than a shared Figma URL, so there is no
public link to record. If you need to re-derive anything, re-attach the same file.

---

## Content fundamentals

The kit's own copy — labels, list content, dialog text, annotation blocks — is where the
voice lives. It is plain, short and unbranded.

**Casing.** Sentence case everywhere. Buttons read `Label`, `Save`, `Reset settings`, never
`SAVE` and never `Save Settings`. Only acronyms are capitalised (`GIF`, `PDF`).

**Person.** Second person for instruction, no first person. The kit's annotation copy reads
like documentation addressed to a designer: *"Layout Breakpoints help you start working with
Adaptive design in mind. Setup your prefered layout using the properties panel and then
detach the instance to get started."* Product copy avoids person entirely — `Message
archived`, not `We archived your message`.

**Length.** One line per idea. Dialog bodies are a sentence, sometimes two. Snackbar labels
are 2–5 words. Supporting text under a text field is a clause, not a sentence with a period
if it is shorter than four words.

**Punctuation.** No terminal period on labels, headlines, list headlines or supporting text
fragments. Full sentences in dialog and tooltip bodies do take periods.

**Verbs.** Actions are imperative verbs — `Archive`, `Snooze`, `Undo`, `Translate`, `Reset`.
Destructive actions are named literally (`Delete`), never softened (`Remove forever`).

**Questions.** Confirmations are phrased as a question in the headline with the consequence in
the body: *"Archive this thread?" / "It moves out of your inbox but stays searchable."*

**Numbers.** Bare numerals, tabular where they align — `24`, `10:24`, `60%`. Counts sit in
trailing badges with no label.

**Emoji.** None. The kit uses no emoji anywhere, and neither should anything built on it.
Unicode appears only for keyboard shortcuts in menus (`⌘R`) and nothing else.

**Vibe.** Neutral, quiet, systematic. Copy describes state and offers actions; it never
celebrates, never apologises at length, never uses exclamation marks.

---

## Visual foundations

### Colour

One accent family (a desaturated purple, `rgb(103,80,164)`) plus a secondary and a tertiary
role, and exactly **one** semantic colour: error (`rgb(179,38,30)`). There is no success,
warning or info colour — the kit expresses those through content, not colour.

The system is role-based, not palette-based. You never pick "purple 600"; you pick
`--m3-primary` or `--m3-secondary-container` and the theme supplies the value. Every
container role has a matching `on-` role for its content, and the pair is guaranteed legible.

Surfaces are a **five-step tonal ladder** rather than shadows: `surface-container-lowest`
(white) → `low` → `default` → `high` → `highest` (`rgb(230,224,233)`). Depth is expressed by
moving up the ladder first, and only reaching for elevation when the surface must visibly
float. Every surface tone carries a faint purple tint — pure grey never appears.

The token file ships **32 theme scopes** from the kit: light, dark, three contrast levels of
each, and 15 accent themes (monochrome, pink, rose, red, orange, yellow, chartreuse, green,
teal, cyan, blue, indigo, purple) in light and dark. Switch with
`:root[data-theme="dark"]` / `.dark`, or `:root[data-mode="teal-lt"]`.

### Type

Roboto is both the Brand and the Plain font in this kit's Font theme — there is no display
face. The scale is 15 roles in five families of three:

| Family | Sizes | Where |
| --- | --- | --- |
| Display | 57 / 45 / 36 | Hero numbers, marketing. Rare in product. |
| Headline | 32 / 28 / 24 | Screen and dialog headlines |
| Title | 22 / 16 / 14 | App bars, card and list headlines |
| Body | 16 / 14 / 12 | All running text |
| Label | 14 / 12 / 11 | Every interactive label |

Weights are only Regular (400) and Medium (500); an emphasised tier maps to SemiBold (600).
Nothing is bold. Tracking is **positive on small text** (+0.5 on body-large, +0.4 on
body-small, +0.5 on label-small) and slightly negative only on display-large (−0.25) — the
opposite of the usual instinct, and it is what makes small M3 text readable.

Google Sans, Google Sans Code and Flow Circular were supplied as files and are self-hosted;
the kit uses Google Sans Text for its own annotation labels, which is not available, so
annotations fall back to Google Sans.

### Shape

Ten corner steps: 0, 4, 8, 12, 16, 20, 28, 32, 48, full. Assignments are consistent —
text fields 4, cards 12, sheets/dialogs 28, buttons and chips full or the square step for
their size. Shape is a **state**, not just a style: buttons morph to a smaller radius while
pressed (40px round button → 8px square while held), which is the kit's most distinctive
motion detail.

### Elevation & shadow

Six levels, each a **pair** of shadows: a tight 30%-black key shadow plus a wider
15%-black ambient shadow. No coloured shadows, no glows, no inner shadows anywhere. Most
surfaces sit at level 0 and rely on the surface ladder; level 1 is cards, 2 menus, 3
dialogs/FABs/snackbars, 4–5 reserved.

### State layers

Interaction is expressed by an overlay of the **content** colour over the container, never by
a different fill: hover 8%, focus 10%, press 10%, drag 16%. Disabled is 38% opacity content
on a 12% container. This is why `.m3-sl` in `components/m3-components.css` paints
`currentColor` at low opacity rather than hard-coding hover colours — a filled button's hover
is white-over-purple, an outlined button's is purple-over-transparent, from one rule.

### Motion

The .fig carries no motion variables, so `tokens/motion.css` uses the M3 spec values.
Entering elements use `emphasized-decelerate` (`cubic-bezier(0.05,0.7,0.1,1)`) over 400ms;
exits use `emphasized-accelerate` over 200ms; state changes are 200ms `standard`
(`cubic-bezier(0.2,0,0,1)`). No bounce, no spring overshoot, no scale-on-press — pressed
feedback is a radius change and a state layer, nothing more.

### Layout

Window size classes drive everything: Compact 0–599dp, Medium 600–839dp, Expanded
840–1199dp, Large 1200–1599dp, Extra-large 1600+dp. Margins are 16dp compact, 24dp above
that, with a 24dp gap between panes. Navigation moves with the class: bottom
`NavigationBar` compact → `NavigationRail` medium → `NavigationDrawer` expanded. Minimum
touch target is 48dp, which is why a 40px button sits inside a 48px hit container.

### Backgrounds, transparency, imagery

Flat surface colours only — no gradients, no textures, no patterns, no full-bleed background
images. Transparency appears in exactly three places: state layers, the 32% scrim behind
modals, and the 38%/12% disabled treatment. No blur or frosted glass. Imagery, where a design
supplies it, is used as full-bleed card media at the card's own corner radius; the kit itself
ships only sample photos.

### Cards, specifically

Three kinds. **Elevated**: `surface-container-low`, elevation 1, no border, 12px radius,
lifting to elevation 2 on hover. **Filled**: `surface-container-highest`, no shadow, no
border. **Outlined**: `surface`, a 1px `outline-variant` hairline, no shadow. Body padding is
16px, internal stack gap 8px. Never combine a border with a shadow.

### Hover and press, summarised

Hover = 8% content-colour overlay (+ one elevation step on elevated surfaces).
Press = 10% overlay **and** a corner-radius morph to the smaller step. Focus = 10% overlay
plus a 3px primary outline offset 2px. Nothing scales, nothing translates.

---

## Iconography

The kit uses **Material Symbols** on a 24px grid, filled and outlined weights of the same
glyph. 120 of them are extracted verbatim from the .fig as SVG path data in
`components/icons/m3-icon-data.js` and rendered by the `Icon` component. Nothing was drawn
by hand and nothing was substituted from a CDN icon set.

- **Format:** inline SVG paths, `currentColor` fill, 24×24 viewBox. Recolour with CSS `color`.
- **Sizes in use:** 18px inside chips, 20px inside small buttons, 24px everywhere else,
  32/36/40px in large buttons and FABs.
- **Pairs:** many glyphs come outlined *and* filled (`Star`/`StarFilled`,
  `Bookmark`/`BookmarkFilled`, `Folder`/`FolderFilled`, `PlayArrow`/`PlayArrowFilled`).
  Convention: outlined = unselected, filled = selected/active.
- **No icon font.** The kit references a `Google Symbols` font for four glyphs, but the
  binary is not in the file, so all icons ship as path data instead.
- **No emoji, no unicode pictographs.** The only non-alphanumeric characters are keyboard
  shortcut symbols in menu rows.
- Full name list: `components/icons/Icon.d.ts`.

---

## Index

Root files:

- `styles.css` — the entry point consumers link. `@import` lines only.
- `readme.md` — this file.
- `SKILL.md` — Agent Skills front-matter wrapper.
- `thumbnail.html` — homepage tile.

`tokens/`

- `fonts.css` — `@font-face` for the supplied faces; Google Fonts import for Roboto / Roboto Mono.
- `fig-tokens.css` — 309 Figma Variables across 32 theme scopes, generated from the .fig.
- `aliases.css` — `--m3-*` semantic names over the raw variable names; corner steps resolved to px.
- `typography.css` — the 15 type-scale classes plus the body/link reset.
- `elevation.css`, `motion.css`, `spacing.css`.

`assets/fonts/` — Google Sans (upright + italic variable), Google Sans Code, Flow Circular.

`components/`

- `m3-components.css` — the shared component stylesheet all components reference.
- `icons/` — **Icon**
- `buttons/` — **Button**, **ToggleButton**, **IconButton**, **Fab**, **FabMenu**, **SplitButton**, **ButtonGroup**
- `selection/` — **Checkbox**, **RadioButton**, **Switch**, **Slider**, **Chip**
- `inputs/` — **TextField**, **SearchBar** (with **SearchView**), **DatePicker**, **TimePicker**
- `containment/` — **Card**, **Dialog**, **Divider**, **ListItem** (with **List**), **Menu** (with **MenuItem**), **Sheet**
- `navigation/` — **TopAppBar** (with **BottomAppBar**), **NavigationBar**, **NavigationRail**, **NavigationDrawer**, **Tabs**, **Toolbar**
- `feedback/` — **Snackbar**, **Tooltip**, **Badge**, **LinearProgress**, **CircularProgress**, **LoadingIndicator**
- `media/` — **Avatar**, **Carousel**

Each directory holds `<Name>.jsx`, `<Name>.d.ts`, `<Name>.prompt.md` and one `@dsCard` HTML.

`guidelines/` — 18 foundation specimen cards (colour roles, type families, shape, elevation,
state layers, spacing, motion).

`ui_kits/`

- `compact/` — mail in the Compact size class: search, filter chips, list, FAB, bottom
  navigation, with a click-through thread view, bottom sheet, confirm dialog and snackbar.
- `expanded/` — mail in the Expanded size class: persistent drawer, two-pane list-detail,
  tabs, anchored menu, floating toolbar, reply composer.

---

## Coverage and intentional consolidation

The .fig enumerates **813 component "families"** because Figma counts every variant set
separately — `.Building Blocks/Icon button - togglable/Large/Outline/Selected` and its 39
siblings are one React component with `size`, `variant` and `selected` props. Every family in
the 74 scoped frames is represented, collapsed into the 36 components listed above. **778 of the 813 Figma families are intentionally skipped** — deliberately, and for these reasons:

- **`.Building Blocks/*` sets** — internal Figma scaffolding (state layers, focus rings,
  handles, track segments, thumbnails) that exist so the kit's variants can compose. They are
  implementation detail, not API; their behaviour lives inside the components.
- **Figma-file scaffolding pages** — Table of contents, Getting started, Utilities (status
  bars, on-screen keyboards, slot placeholders) and Examples (layout-breakpoint diagrams).
  These document or navigate the Figma file itself; they are not product components.

Badges, Carousel and Date & time pickers sat outside the original 74-frame scope but *are*
real product families, so they were built anyway: **Badge**, **Carousel**, **DatePicker**,
**TimePicker**.
- **Deprecated sets** — the three `?Deprecated?` Button / FAB / Icon button sets.

### Intentional additions

- **`Icon`** — a wrapper over the extracted glyph data. The .fig has 120 separate icon
  components; one component with a `name` prop is the only sane API for a set that size.
- **`m3-components.css`** — a stylesheet, not a component. It exists so `:hover`, `:focus-visible`
  and `:active` state layers can be real CSS states instead of React hover tracking.

## Known gaps

- **No logo or wordmark.** The .fig contains none, so none was created; the project thumbnail
  renders the letters "M3" in Roboto rather than a mark.
- **No product screens.** The kit's `/Examples` page holds layout-breakpoint diagrams, not
  finished app views. The UI kits therefore recreate the kit's own *size-class layout rules*
  with the kit's own sample mail content, rather than reproducing a screen that does not exist.
- **Roboto is loaded from Google Fonts**, not shipped — the .fig has no Roboto binary. If you
  need offline/self-hosted Roboto, supply the files and swap the `@import` in `tokens/fonts.css`.
- **Google Sans Text** (the face the kit uses for its own annotations) was not supplied; those
  labels fall back to Google Sans.
- **Motion tokens** come from the public M3 spec, since the .fig defines no motion variables.
- **`VolumeUp`** was in the source icon set but had no decodable geometry, so it is absent
  from `Icon`. All other 121 glyphs are present.
