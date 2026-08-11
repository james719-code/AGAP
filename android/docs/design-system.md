# AGAP Android design-system rules

## Emergency types

Incident type labels, colors, and icons must come from `ui.theme.EmergencyType`.
Do not add per-screen `when` mappings or inline incident-type colors. Agency
directory types are a separate taxonomy and may use their own mapping.

## Layout and surfaces

- Use `AgapSpacing` for layout gaps and padding.
- Use `AgapShape` for corners.
- Use `Modifier.glassCard()` for the standard elevated-card treatment instead
  of recreating its background, border, and clipping behavior.

## Icons

Use `Icons.Outlined` by default. Filled icons are reserved for a deliberate
active or selected state, such as the active bottom-navigation item.

## Review checklist

For every new or substantially changed screen, confirm that it reuses:

1. `EmergencyType` for incident presentation;
2. `AgapSpacing`, `AgapShape`, and `glassCard()` for common layout/surfaces;
3. outlined icons unless the element is actively selected.

## Automated guard

`./gradlew :app:verifyDesignSystem` runs as part of `:app:check`. It rejects
direct ARGB literals in screen source and raw spacing/shape values that have
an `AgapSpacing` or `AgapShape` equivalent. The small legacy baseline lives in
`app/build.gradle.kts`; keep it empty whenever possible and document any
exception before adding one.
