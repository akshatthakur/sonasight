# SonaSight

A film camera for Android. Photos and video are developed live through original film stocks, with golden ratio framing guides.

## Shooting

- **Scenes** along the bottom dial set up the camera for what's in front of you: Streets, Temples, Nightlife, Lanterns, Buildings, People, Markets, Rain, Golden Hour, Nature, Food, Noir and Vista. Each picks a film, lens, frame and guide.
- **Film** -- tap the film name in the viewfinder to change stock. The look is applied to the viewfinder, stills and video alike.
- **Guides** -- PHI grid, golden spiral and golden triangles. TURN rotates the spiral or flips the triangles. LEVEL shows a horizon line that turns gold, with a tick, when the camera is straight.
- **Lens** -- 24, 28, 35 and 50 (35mm equivalent).
- **Exposure** -- drag the ruler, -2 to +2 in thirds.
- **Frame** -- 3:2, 4:3, 1:1, 16:9 and 65:24 panoramic for stills. Video is 16:9.

Photos are saved to Pictures/SonaSight and videos to Movies/SonaSight.

## Films

| Film | Character |
|---|---|
| MERIDIAN 100 | Clean daylight slide |
| KILN 50 | Dense, saturated slide |
| LEDGER 200 | Muted documentary colour, hard shadows |
| ARCADE 400 | Everyday negative, teal shadows |
| REEL 250 | Flat cinema stock |
| AMBER 160 | Warm and nostalgic |
| PALE 160 | Soft portrait negative |
| HALO 800T | Tungsten night stock with red halation |
| SILVER SKIP 200 | Bleach bypass |
| INK 100 | Fine black and white |
| SOOT 1600 | Pushed black and white |

## Install

Every push to `main` builds an APK and attaches it to the [latest release](../../releases/tag/latest).

To keep one signing key across builds, add repository secrets `SS_KEYSTORE_B64` (base64 of the keystore), `SS_STORE_PASSWORD`, and optionally `SS_KEY_ALIAS` (defaults to `sonasight`).

---

Created by Akshat Thakur.
