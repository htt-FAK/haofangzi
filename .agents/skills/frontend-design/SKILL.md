---
name: frontend-design
description: Guidance for distinctive, intentional visual design when building new UI or reshaping an existing one. Helps with aesthetic direction, typography, and making choices that don't read as templated defaults.
license: Apache-2.0
origin: https://github.com/anthropics/skills/tree/main/skills/frontend-design
---

# Frontend Design

Approach this as the design lead at a design studio known for giving every client a distinct visual identity that is not mistaken for anyone else's. This client has already rejected proposals that felt cliché or templated, and is paying for a distinctive point of view: make deliberate, opinionated choices about palette, typography, and layout that are specific to this brief, and take aesthetic risk if justified.

## Ground your designs in the subject matter

If the brief does not identify what the product or subject matter is, identify it yourself before designing, and confirm with the client. You can come up with one concrete subject, the design's audience, and the design's primary job, as a proposal. If there's any information in your memory about the client's preferences or context about what they're building, use that as a hint. The subject's industry, subject matter, materials, and vernacular are where distinctive visual choices come from — a design for a real estate & architectural floor-plan evaluation system should draw directly from architectural blueprints, drafting lines, fine grid paper, precision calipers, and lake-city atmosphere. Build with the brief's real content and subject matter throughout.

## Design principles

For web designs, the hero is the first thing viewers will see. Open with the most characteristic thing in the subject's world, in the form that is most appropriate: a headline, an image, an animation, a live demo, an interactive moment, or other treatments. Be deliberate with your choice.

Typography carries the personality of the page. You don't need a different typeface for display or headline text and body content: use one family or two, and if two, make them clearly distinct.

Choose your typefaces deliberately, set a clear type scale with intentional weights, widths, and spacing. When type is used as a headline or visual element, use the type treatment itself as an active part of the design, not a neutral delivery vehicle for the content.

Visual structure is information. Structural devices like subtle borders, elevation shadows, architectural hairline dividers, tags, and tabular numbers encode useful information about the content rather than decorate it.

Use motion sparingly and deliberately, only to draw attention or reward interaction:
- Micro-interactions on hover (soft lift, shadow bloom, accent ring)
- Smooth accordion expansions and state transitions
- Clean glassmorphism backdrop filters for chrome elements (topbar, floating badges)

## Process: plan, review against the brief, build, critique

Avoid AI design defaults:
1. Muddy low-contrast grey backgrounds that feel unwashed.
2. Identical rigid cards with no depth, no hierarchy, and clumsy button groups.
3. Plain default form inputs without focus rings or smooth transitions.
4. Harsh raw text headers without atmospheric context or visual weight.

Work in deliberate passes:
1. Refine the Design System tokens: elevate colors (Deep Star Lake Indigo `#133E54`, Vibrant Blueprint `#216E94`, Warm Blueprint Paper `#F8F9FA`, Crisp Surface `#FFFFFF`, Refined Slate `#64748B`), smooth radii, and multi-layered elevation shadows.
2. Craft the Product Chrome (`App.vue`): floating blurred topbar, brand SVG crest, sleek navigation pills with active pill indicator, refined footer.
3. Overhaul the Primary Experience (`HomeView.vue`):
   - High-impact architectural hero header with quick stats & project badge.
   - Polished filter controls with interactive state, quick tags, and clean clear actions.
   - Architectural card showcase: crisp blueprint canvas preview, glassmorphic grade tags, clear hierarchy (title, spec tags, price accent, and distinct primary/secondary CTAs).
4. Polish Details & Evaluation (`HouseTypeDetailView.vue`, `EvaluateView.vue`, `theme.css`).
