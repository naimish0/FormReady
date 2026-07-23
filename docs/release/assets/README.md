# Release asset inventory

## Production source and rendered files

| Asset | Source | Render | Required check |
|---|---|---|---|
| Google Play icon | `source/play-icon-512.svg` | `rendered/play-icon-512.png` | 512×512 PNG, full square artwork, no pre-rounded mask |
| Feature graphic | `source/feature-graphic-1024x500.svg` | `rendered/feature-graphic-1024x500.jpg` | 1024×500 JPEG, final product identity only |
| Adaptive/round/monochrome launcher | `app/src/main/res/drawable` and `mipmap-*` | Android resources | Inspect Play-generated launcher masks |
| Splash | `app/src/main/res/drawable/ic_splash.xml` | Android resource | Inspect light/dark startup |

Run `tools/render-release-assets.sh` on macOS with Google Chrome and `sips` to reproduce the Play
icon and feature graphic. The graphics intentionally reuse the shipped document/checkmark vector;
they do not include government, exam, employer, passport, visa or “official” branding.

Rendered graphics are store assets, not screenshots. Actual screenshots must show the final app UI
and must be captured with the synthetic fixture plan.
