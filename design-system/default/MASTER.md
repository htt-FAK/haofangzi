# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** 肇庆好房子
**Updated:** 2026-09-21
**Category:** Real Estate listing + evaluation product
**Reference:** Airbnb listing cards + 贝壳找房 information hierarchy + Linear product chrome
**Identity:** Architectural floor-plan drawings (not photos, not teal SaaS gradients)
**Design Dials:** Variance 5/10 | Motion 3/10 | Density 5/10

---

## Global Rules

### Color Palette

| Role | Hex | CSS Variable |
|------|-----|--------------|
| Primary (星湖暮蓝) | `#215E7A` | `--hf-primary` |
| On Primary | `#FFFFFF` | `--hf-on-primary` |
| Ink | `#141414` | `--hf-ink` |
| Background (图纸纸面) | `#F3F2EE` | `--hf-canvas` |
| Surface | `#FFFFFF` | `--hf-surface` |
| Border | `#E4E2DA` | `--hf-border` |
| Good | `#1B7A4A` | `--hf-good` |
| Destructive | `#B42318` | `--hf-bad` |

**Do not use:** Tailwind mint `#F0FDFA` / teal-700 hero gradients / Cinzel+Josefin / price-in-red (淘宝/贝壳廉价感).

### Typography

- **UI / Body:** PingFang SC · Hiragino Sans GB · Noto Sans SC · Microsoft YaHei · system-ui
- **Display:** same family, 700, tracking -0.03em to -0.04em
- **Numbers:** `font-variant-numeric: tabular-nums`
- **Do not use:** Cinzel, Josefin Sans, Inter as brand fonts

### Spacing

8px rhythm. Page padding 28/32. Card grid gap 22–28. Cover radius 16px.

### Motion

150–300ms ease-out. Hover: cover `translateY(-2px)` + shadow. Respect `prefers-reduced-motion`.

---

## Page Pattern

**Listing-first product** (not Enterprise Gateway landing).

1. Quiet page header (kicker + large title + lead). No colored hero banner.
2. Chip filters, not a dense Element form card.
3. Floor-plan cover as the listing photo (Airbnb rhythm).
4. One primary CTA per card (评估); 对比 is text.
5. Product chrome: sticky white/blur bar, text nav with ink underline.

### Anti-Patterns

- Emoji as icons
- Teal gradient marketing heroes
- Three equal feature chips in a banner
- Ghost outline buttons as the only secondary
- Identical placeholder thumbs on every listing
- `font-weight: 650` (use 600/700)
