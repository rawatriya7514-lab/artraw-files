# Artfiles Graphite — Design System

## Direction

Artfiles is a premium, classic file manager: calm graphite surfaces, warm paper
light, restrained motion and low-chroma jewel accents. No pink, no neon glow,
no high-saturation gradients.

## Colour

| Role | Light | Dark |
| --- | --- | --- |
| Canvas | `#F5F7FA` | `#0F1319` |
| Surface | `#FFFFFF` | `#151B23` |
| Elevated | `#F0F4F8` | `#1B232D` |
| Primary (soft crimson) | `#A94B47` | `#D98A82` |
| Secondary (dusty blue) | `#3F6FA8` | `#7FA9DD` |
| Success (sage) | `#3F8166` | `#86C7A5` |
| Warning (soft amber) | `#9A6B1F` | `#D9B36C` |
| Text primary | `#1B2430` | `#E8EDF5` |
| Text secondary | `#55627A` | `#A6B2C3` |

All tokens live in `res/values/aurora_colors.xml` (light) and
`res/values-night/aurora_colors.xml` (dark). Legacy names are aliases in
`res/values/colors.xml`, so older screens stay theme-aware without rewrites.

## Theme modes

`BaseActivity` reads `theme_mode` (`system`, `dark`, `light`) and overrides the
UI mode through `createConfigurationContext`. `values-night` then resolves the
dark palette and dark platform theme. System mode follows the device and
recreates the activity on configuration change.

## Typography

System sans-serif only, with deliberate scale contrast:

- Display 34sp light, tight tracking
- Headline 26sp bold
- Title 19sp medium/bold
- Body 14sp with generous line height
- Caption 12sp secondary
- Label 10sp bold, 0.14 tracking, uppercase

Widget styles live in `res/values/styles.xml`.

## Surfaces & shape

- 18dp cards, 14dp buttons and rows, 26dp floating dock
- Hairline outline (`aurora_outline`) instead of bright borders
- Soft one-step shadows; no double neon rims
- Category badges use a neutral surface plus a category-coloured hairline

## Motion

`MotionUtils` centralises motion and honours the in-app switch plus the system
animator duration scale:

- Entrance: 300ms fade + 8–12dp rise, capped 24ms stagger
- Press: 0.94–0.985 scale down, 110ms; release 160ms
- Pop: 120ms + 160ms for newly active navigation icons
- Activity transitions: 260ms fade/2% rise; dialogs 210ms scale from 96%

Motion is decorative only: no looping content animation, and every effect has a
static fallback.

## Navigation

Four-item floating dock: Home, Browse, Tools (Toolbox hub), Settings. The active
item uses the primary-container pill, primary text and a small icon pop. The
Toolbox groups maintenance tools (analyzer, empty folders, junk, shredder,
vault, checksum, recycle bin, device monitor, Wi-Fi transfer).

## Accessibility

- Body text meets WCAG AA against surfaces in both themes
- Touch targets ≥ 44–48dp
- Text remains readable when motion is disabled
- System-bar icon contrast follows the resolved theme
