# Sponsored cards refresh (Ads + Garage) — Android handoff

UI only. No backend, rules or data changes. iOS reference: `ios/main` commit `f1d3a48`
(`BulletinBoardView.swift`, `BulletinAd+Presentation.swift`, `SponsoredListingComponents.swift`,
`SponsoredContentView.swift`, `SponsoredGarageShopProfileView.swift`, `MyBulletinAdsView.swift`).

Data and payment rules for garage sales: `IOS_ANDROID_GARAGE_SALES.md`. Ads board data: `IOS_ANDROID_ADS_BOARD.md`.

---

## 1. Ads cards alternate bold / soft

Number cards in display order **across the whole list** (do not restart at each section:
Urgent, Today, This week, Earlier, Resolved).

- Even position (0, 2, 4…) → **bold**
- Odd position (1, 3, 5…) → **soft**
- My Ads screen: same rule by its own list position.
- Post-ad preview: always **bold**.

### Bold card

Diagonal gradient, top-left → bottom-right. Pick colours in this order:

1. Resolved → Found colours
2. Document post: lost → Lost colours, found → Found colours
3. Otherwise by type

| Type | Start (RGB 0–1) | End (RGB 0–1) |
|---|---|---|
| Ad | 0.98, 0.47, 0.20 (orange) | 0.91, 0.26, 0.47 (pink) |
| Lost | 0.93, 0.27, 0.29 (red) | 0.80, 0.16, 0.42 (rose) |
| Found / Resolved | 0.13, 0.66, 0.40 (green) | 0.05, 0.56, 0.58 (teal) |
| Notice | 0.20, 0.50, 0.96 (blue) | 0.36, 0.33, 0.90 (indigo) |
| Document | 0.38, 0.35, 0.92 (indigo) | 0.60, 0.30, 0.88 (purple) |

Do not darken the type colour with black: orange turns brown.

| Element | Bold style |
|---|---|
| Title | white |
| Snippet, posted time, "Yours", contact icons | white 80% |
| Place label | white |
| Icon tile (44dp, radius 12) | white 18% background, white icon |
| Badge | white pill, text in the gradient end colour |
| Photo (60dp, radius 10) | 1dp white 35% border |
| Divider | white 20%, 0.5dp |
| Share icon | white |
| Expires soon (≤ 2 days) | bold white text in a white 25% pill (8dp × 3dp padding) |
| Expiry otherwise | white 80% |
| Border | 1dp white 12%; urgent Lost: 1.5dp white 50% |
| Shadow | gradient end colour 30%, blur 10, y 5 |

### Soft card

| Element | Soft style |
|---|---|
| Background | normal card surface + type colour at 7% on top |
| Border | 1dp type colour 22%; urgent Lost: 1.5dp red 45% |
| Text | normal primary / secondary (title grey + strikethrough when resolved) |
| Icon tile, badge | unchanged (type colour 14% tile, existing badge) |
| Expires soon | orange text |
| Shadow | none |

Both: corner radius 18dp, padding 14dp.

---

## 2. Garage sale tile (same size everywhere)

Shown in a 2-column grid, 10dp spacing both ways. Every tile has the same height.

1. **Cover** — fixed 112dp high, full width, cropped.
   - First photo, or a gradient of the sale-type colour (75% → 40%) with the type icon in white (30sp).
   - Top-left date chip: 38×38dp, radius 9, translucent/blurred background, 8dp inset.
     Month short uppercase (9sp heavy, type colour; grey if ended) above day number (17sp bold rounded).
     Hidden when the sale has no date.
   - Top-right, only if saved: white bookmark icon in a black 45% circle, 6dp inset.
2. **Status line** — always exactly 1 line.
   - Timing short label ("Open now", "Sat"…), bold caption, timing colour.
   - Live: green text with a 6dp green dot.
   - No timing: "Posted 3 days ago" (or "Garage sale"), grey.
3. **Title** — semibold, `minLines = 2, maxLines = 2`. Reserving 2 lines is what keeps tiles equal.
   Grey when ended.
4. **Detail line** — "Type · City, State · 2.3 km" (distance only with Near me on), caption grey, 1 line.

Text area padding 10dp, spacing 5dp. Card surface, radius 16.
Border: live → 1.5dp green 55%; otherwise 0.5dp hairline. Ended → whole tile 65% opacity.

---

## 3. Screen organisation

### Garage tab (Sponsored hub)

Top to bottom:

1. Page header + search field
2. Shops / Discover segmented switch
3. Day chips: All, Today, This weekend, Upcoming, Saved
4. One grey summary line (hidden when empty):
   - Shops: "8 sales from 3 sellers · soonest first"
   - Discover: "N photos from sales near you · tap one for details"
5. Content directly on the page background (remove the outer box around the section)

Remove the "Open full Garage screen" button from this tab; the full screen stays reachable from the
Sponsored overview.

### Shop card (Shops view)

Padding 14dp, radius 20, light surface, hairline border. Cards stacked with 14dp spacing.

1. Shop header (avatar, name, subtitle) → opens shop profile
2. 2×2 grid of the shop's next 4 sales (tiles, soonest first) → tap opens sale detail
3. Only if more than 4 sales: "View all N sales →" bar (green text, green 10% background, radius 12)
   → opens shop profile

Replaces the old 6-photo strip and the single "Next up" card.

### Shop profile

New order:

1. Header
2. Open / closed status
3. Search
4. **Sales** — title + count + "Soonest first", then the 2-column tile grid (no surrounding box).
   Empty: grey message in a 100dp translucent box.
5. Shop Gallery (square photo grid, unchanged)
6. Payment note (moved to the bottom)

### Unchanged

- Discover stays a square photo grid.
- The old horizontal sale card is only used for the preview in the post-a-sale flow.
