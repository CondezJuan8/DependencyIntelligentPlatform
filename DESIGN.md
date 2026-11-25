# Dependency Intelligence — Design System

## Direction

Design the application as an evidence-first engineering and security workspace. Use a restrained, grid-based enterprise interface with compact but readable information. Its signature move is an **evidence trail**: keep the repository, exact package version, source snapshot, and OSV assessment visibly connected wherever the existing data provides them. Do not imply missing links or stronger coverage.

Linear, Vercel, Snyk, and GitHub Advanced Security inform the expected clarity, consistency, and finish. Their layouts, names, logos, and visual identities are not templates.

## Identity and palette

Keep the Dependency Intelligence mark and official product colors. Dark is the initial theme; the existing user and organization preference controls continue to select light, dark, or system appearance.

| Role | Light theme | Dark theme | Use |
| --- | --- | --- | --- |
| Brand navy | `#101827` | `#101827` | Navigation, stable brand surface |
| Primary blue | `#2563EB` | `#2563EB` | Primary actions, selected controls |
| Cyan accent | `#06B6D4` | `#06B6D4` | Active navigation and evidence linkage |
| Page background | `#F8FAFC` | `#0B1220` | Main application canvas |
| Surface | `#FFFFFF` | `#141F30` | Forms, menus, dialogs, work areas |
| Raised surface | `#FFFFFF` | `#19263A` | Overlays and focused regions |
| Muted surface | `#F1F5F9` | `#1C2A40` | Inputs, table hover, secondary grouping |
| Primary text | `#0F172A` | `#E6EDF7` | Headings and body copy |
| Secondary text | `#536784` | `#BAC9DC` | Supporting copy and metadata |
| Subtle text | `#64748B` | `#A6B8CE` | Timestamps and low-priority detail |
| Borders | `#D5E0EF` | `#304157` | Structural separators and controls |

Keep success, warning, high risk, critical, and information colors semantic. In the dark theme use the existing light foreground and dark-tinted surface tokens. State is always named in text; color alone never carries meaning. Source values from `frontend/src/theme/_tokens.scss` and `frontend/src/theme/_appearance.scss`, not component-level hex values.

## Typography

Use the self-hosted IBM Plex Sans family already in the frontend. Do not fetch fonts or add a font package. Use tabular numerals for counts, versions, durations, and IDs.

| Role | Size / weight |
| --- | --- |
| Page title | 26–30 px / 600 |
| Section title | 18–20 px / 600 |
| Subsection title | 15–16 px / 600 |
| Body and form controls | 14–16 px / 400–500 |
| Table and supporting text | 13–14 px / 400 |
| Metadata | 12 px minimum / 400–500 |

Use line height 1.45–1.6 for body text. Keep long prose within 65–75 characters per line. Avoid all caps, decorative tracking, oversized display headings, and low contrast helper text.

## Spacing and shape

Use a 4 px base rhythm: `4, 8, 12, 16, 24, 32, 40`. Page gutters are 24 px on desktop, 16 px on mobile. Keep dense tables aligned and scanable; separate major work areas with spacing and a single surface treatment rather than nested cards.

Use 4 px for controls, 6 px for compact panels, and 8 px for dialogs or larger work areas. Avoid pill-shaped containers except for short semantic status labels. Shadows are reserved for overlays and menus, with a visible offset and soft blur. No decorative gradients, glass effects, or halo shadows.

## Iconography

Keep the local `DiIcon` SVG set. Use a consistent 16 px inline and 20 px navigation/action size with the existing 1.7 px stroke. Icons beside visible labels are decorative to assistive technology; icon-only controls have a localized accessible name and state. Do not use emoji, external icon fonts, or new icon dependencies.

## Reusable components

- **Application shell:** 240 px navigation rail, 64 px top bar, one scrollable main region, responsive navigation drawer on narrow screens.
- **Page heading:** one concise title, short task-oriented description where useful, and at most one primary action.
- **Evidence section:** a single work surface with a clear heading, filters in the scope they affect, a compact result summary, and the existing table/list content.
- **Data table:** readable labels, aligned numeric columns, wrapping for long IDs and paths, clear row hover/focus, and responsive overflow or stacked fields where the existing page supports it.
- **Filter controls:** persistent labels, visible selected values, explicit clear action, and no information hidden behind hover.
- **Status label:** text plus semantic color; do not rely on color alone.
- **Empty state:** explain what is missing and offer the existing next action. Distinguish no repository, no analysis, no findings, and no filtered matches.
- **Loading and errors:** retain the current progress, retry, cancel, and error messages. Never invent a percentage or stage. Announce results through the existing status and alert regions.
- **Dialog:** use only for decisions requiring confirmation or focused entry. Trap and restore focus, close with Escape where safe, and prevent background interaction.

Prefer Angular standalone components, existing Material/CDK primitives, signals, and shared theme tokens. Keep all existing user actions and service/API wiring.

## Interactive states

Every interactive control has rest, hover, keyboard focus, pressed, disabled, and pending states where applicable. Use a 2–3 px high-contrast `:focus-visible` outline with 2 px offset. Use 150–200 ms transitions for color and opacity only. Respect `prefers-reduced-motion`; keep the analysis progress indicator because it communicates active work, but remove ornamental spinning/glowing details.

Forms keep user-entered values after a server error, show actionable inline validation, and retain password manager and paste support. Buttons state what they do. Do not make a disabled action look available or a plain status label look clickable.

## Responsive behavior

Design mobile first and verify at 375, 390, 768, 1024, and 1440 px. The navigation rail becomes a labelled drawer with a visible close affordance. Keep search, Nexo, theme/language, alerts, and profile reachable without horizontal overflow. Tables reflow or scroll within their own labelled region; page content never acquires horizontal scrolling. On short screens, authentication and dialogs may scroll instead of clipping.

## Accessibility

- Target WCAG 2.2 AA text contrast: 4.5:1 for normal text and 3:1 for large text and meaningful UI boundaries.
- Preserve semantic headings, landmarks, table headers, persistent form labels, skip navigation, visible focus, keyboard order, and screen-reader names for icon controls.
- Use at least 44 × 44 px targets for primary touch controls where space permits; keep adjacent targets at least 8 px apart.
- Do not rely on hover, color, or motion to reveal content or state.
- Support browser zoom, text wrapping, reduced motion, and both themes. Test dark and light modes independently.
- When navigation changes the query-string view, keep browser back/forward behavior and expose the active destination with `aria-current`.

## Navigation and information order

Keep the existing `?view=` destinations and role-based visibility. Group the same destinations by task: workspace overview; repository analysis and evidence; organization and account administration. Keep Nexo, notifications, language/theme, and account actions in the global header. Retain direct links between a repository, its analysis, inventory, vulnerability evidence, and reports.

For long workspaces, provide a compact **On this page** navigation to the major sections that are currently available. It is a set of in-page links, not a completion tracker. Keep the links close to the page introduction, use each destination's name so users know what the action opens, provide at least 44 px touch targets, and make keyboard focus and the selected anchor visible. Do not show links to hidden or unavailable sections.

Within a page, order information as: task and current scope; primary action; current status and coverage; filters; results; provenance and technical detail. Show the facts needed for the next decision without forcing an unnecessary click, while leaving deep scanner or advisory detail expandable. Do not add statistics, charts, demo records, repository names, or security claims without real backend evidence.

## Quality review

Review desktop and mobile output, focus order, contrast, empty/loading/error/partial states, and real API behavior. Static compilation is not visual acceptance. A live authenticated review requires an approved administrator bootstrap and real organization/repository data; until then, do not use fixtures or claim those screens passed live visual testing.
