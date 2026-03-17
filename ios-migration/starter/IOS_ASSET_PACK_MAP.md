# iOS Asset Pack Map (Ready-To-Drop)

This asset catalog scaffold is in:
- `Assets.xcassets`

Code now expects these image set names:

| Asset Name | Used For | Android Source Hint |
|---|---|---|
| `brand_app_logo` | Login hero logo | `app/src/main/res/drawable/app_logo.xml` |
| `brand_company_mark` | Header/brand mark | `app/src/main/res/drawable/app_logo.xml` |
| `brand_avatar_placeholder` | Profile fallback avatar | `app/src/main/res/drawable/ic_logo_placeholder.xml` |
| `tab_events` | Volunteer tab icon | `material`/custom export |
| `tab_jobs` | Volunteer tab icon | `material`/custom export |
| `tab_activity` | Volunteer tab icon | `material`/custom export |
| `tab_wallet` | Wallet tab icon | `material`/custom export |
| `tab_community` | Community tab icon | `material`/custom export |
| `tab_tools` | Tools tab icon | `material`/custom export |
| `tab_profile` | Profile tab icon | `material`/custom export |
| `tab_hosted` | Organizer hosted tab icon | `material`/custom export |
| `tab_applications` | Applications tab icon | `material`/custom export |
| `tab_posted_jobs` | Employer posted jobs tab icon | `material`/custom export |
| `tab_dashboard` | Admin dashboard tab icon | `material`/custom export |
| `tab_controls` | Admin controls tab icon | `material`/custom export |
| `tab_support` | Admin support tab icon | `material`/custom export |
| `hub_mindloom` | Community row icon | `material`/custom export |
| `hub_gallery` | Community row icon | `material`/custom export |
| `hub_marketplace` | Community row icon | `material`/custom export |
| `hub_sponsored` | Community row icon | `material`/custom export |

## Exact File List To Add

Place these files in their image sets in Xcode (`1x`, `2x`, `3x`):

1. `brand_app_logo.png`
2. `brand_company_mark.png`
3. `brand_avatar_placeholder.png`
4. `tab_events.png`
5. `tab_jobs.png`
6. `tab_activity.png`
7. `tab_wallet.png`
8. `tab_community.png`
9. `tab_tools.png`
10. `tab_profile.png`
11. `tab_hosted.png`
12. `tab_applications.png`
13. `tab_posted_jobs.png`
14. `tab_dashboard.png`
15. `tab_controls.png`
16. `tab_support.png`
17. `hub_mindloom.png`
18. `hub_gallery.png`
19. `hub_marketplace.png`
20. `hub_sponsored.png`

App icon files to add in `AppIcon.appiconset`:

1. `icon_20@2x.png` (40x40)
2. `icon_20@3x.png` (60x60)
3. `icon_29@2x.png` (58x58)
4. `icon_29@3x.png` (87x87)
5. `icon_40@2x.png` (80x80)
6. `icon_40@3x.png` (120x120)
7. `icon_60@2x.png` (120x120)
8. `icon_60@3x.png` (180x180)
9. `icon_ipad_20@1x.png` (20x20)
10. `icon_ipad_20@2x.png` (40x40)
11. `icon_ipad_29@1x.png` (29x29)
12. `icon_ipad_29@2x.png` (58x58)
13. `icon_ipad_40@1x.png` (40x40)
14. `icon_ipad_40@2x.png` (80x80)
15. `icon_ipad_76@1x.png` (76x76)
16. `icon_ipad_76@2x.png` (152x152)
17. `icon_ipad_83_5@2x.png` (167x167)
18. `icon_app_store_1024.png` (1024x1024)

Notes:
- The app falls back to SF Symbols if a custom image is not present.
- Use transparent PNGs for tab/hub icons and square logos.
