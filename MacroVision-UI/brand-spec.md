# Brand Spec — MacroVision MVP

## Visual Posture: Strict Utility
- **Philosophy**: Efficiency over elegance. High information density, bold typographic hierarchy, and sharp functional contrast.
- **Hierarchy**: Primary actions use high-saturation Emerald. Secondary data uses Deep Slate on White.
- **Radii**: `rounded-2xl` (16px) for cards and containers.
- **Shadows**: `shadow-sm` for depth; no heavy blurs.

## Tokens
- **--bg**: `oklch(100% 0 0)` (#FFFFFF)
- **--surface**: `oklch(98% 0.005 250)` (#F8FAFC)
- **--fg**: `oklch(19% 0.04 260)` (#0F172A)
- **--muted**: `oklch(55% 0.03 260)` (#64748B)
- **--border**: `oklch(92% 0.01 260)` (#E2E8F0)
- **--accent**: `oklch(70% 0.18 160)` (#10B981)

## Typography
- **Display**: Inter Bold / SF Pro Display Bold (System Sans)
- **Body**: Inter Regular / SF Pro Text (System Sans)
- **Mono**: JetBrains Mono (for precise gram/macro values)

## Layout Rules
- **Spacing**: Tailwind `space-y-4`, `p-6` as base.
- **Hit Targets**: 44px minimum for all touch points.
- **Density**: Performance-tier (show grams and % alongside totals).
