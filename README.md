# SonaSight

a film camera for Android. what you see in the viewfinder is already developed, so photos
and video come out looking like film straight away, with nothing to edit after.

I made it for shooting while travelling. you pick what you're pointing at (a street, a temple,
lanterns at night) and it sets up the film, lens and framing for that. there are golden ratio
guides to help you place things in the frame, which is where the sona in the name comes from.

## installing

1. on your Android phone, open this link:
   **https://github.com/akshatthakur/sonasight/releases/latest/download/SonaSight.apk**
2. when the download finishes, tap the file to open it.
3. if your phone says it can't install apps from this source, tap *settings*, turn on
   *allow from this source*, then go back.
4. tap *install*, then *open*.

SonaSight asks for the camera, and for the microphone so videos have sound.

to update, open the same link and install again.

to open straight into stills or motion, press and hold the SonaSight icon and pick one.

## scenes

the dial along the bottom has a scene for most things you'd shoot. picking one sets the film,
lens, frame shape, guide and exposure. you can change any of those afterwards.

| scene | film | lens |
| --- | --- | --- |
| STREETS | LEDGER 200 | 28 |
| TEMPLES | AMBER 160 | 35 |
| NIGHTLIFE | HALO 800T | 35 |
| LANTERNS | HALO 800T, warmed up, with more glow | 50 |
| BUILDINGS | REEL 250, with more contrast | 24 |
| PEOPLE | PALE 160 | 50 |
| MARKETS | ARCADE 400 | 35 |
| RAIN | REEL 250, cooler, with a little glow | 35 |
| GOLDEN HOUR | AMBER 160 | 35 |
| NATURE | KILN 50 | 24 |
| FOOD | KILN 50, square frame | 50 |
| NOIR | SOOT 1600 | 35 |
| VISTA | MERIDIAN 100, 65:24 panorama | 28 |

## films

tap the film name in the bottom corner of the viewfinder to change it. every film is applied
to the viewfinder, stills and video in the same way.

| film | what it looks like |
| --- | --- |
| MERIDIAN 100 | clean daylight colour |
| KILN 50 | dense and saturated, with deep greens and skies |
| LEDGER 200 | muted colour with hard shadows, good for documentary |
| ARCADE 400 | everyday negative with teal shadows |
| REEL 250 | flat, like cinema film, with lots of room in the shadows |
| AMBER 160 | warm, with honey highlights |
| PALE 160 | soft and gentle on skin |
| HALO 800T | tungsten night film. bright lights get a red glow around them |
| SILVER SKIP 200 | bleach bypass: metallic and low on colour |
| INK 100 | fine black and white |
| SOOT 1600 | gritty black and white with heavy grain |

### meisho

meisho means famous places. these films are named after places in Japan, and each one is
tuned to what that place is known for. they sit in their own section at the bottom of the
film list.

| film | made for |
| --- | --- |
| SHIBUYA 渋谷 | night crossings and glass towers. teal shadows, warm signs, crisp edges |
| SHINJUKU 新宿 | neon in the rain. electric blues against hot reds, with a strong glow |
| ASAKUSA 浅草 | temple gates and paper lanterns. vermilion, with cyan-green shadows |
| SHIMOKITA 下北 | everyday streets. soft, faded and a little nostalgic |
| KYOTO 京都 | forests and temples. deep emerald, maple red, quiet shadows |
| OSAKA 大阪 | neon and canal reflections. loud, warm and saturated |
| NARA 奈良 | parks, deer and morning light. soft and warm |
| KAMAKURA 鎌倉 | sea, shrines and grey skies. muted and calm |
| ATAMI 熱海 | an old onsen town by a hazy sea. faded amber, like a postcard |
| HAKONE 箱根 | mist and mountains. muted greens and a cool blue air |
| OTARU 小樽 | snow, canals and gas lamps. silver light and warm brick |
| KANAZAWA 金沢 | gardens, old streets and gold leaf. rich moss and gilt |

the looks draw on Fujifilm's Classic Neg. and CineStill 800T recipes that people shoot
in Japan, popular Tokyo night presets, and the softer, paler palette that a lot of
photographers describe there.

## the controls

words in caps are the labels in the app.

| control | what it does |
| --- | --- |
| **PHI / SPIRAL / TRI / OFF** | the framing guide. PHI splits the frame at the golden ratio, SPIRAL draws a golden spiral, and TRI draws golden triangles. tap to go to the next one. |
| **TURN** | moves the spiral to another corner, or flips the triangles. |
| **LEVEL** | a horizon line that turns gold, with a small tick you can feel, when the camera is straight. |
| **TUNE** | sliders on top of the film: strength, warmth, grain, glow and vignette. SAVE AS RECIPE keeps the film and sliders together under a name, and your recipes show up at the top of the film list. long-press a recipe there to delete it. |
| **3:2** | the frame shape for stills: 3:2, 4:3, 1:1, 16:9 or 65:24. video is always 16:9. |
| **24 / 28 / 35 / 50** | the lens, as a 35mm equivalent. if your phone has an ultra-wide, a wider number shows up first. pinch the viewfinder to zoom anywhere in between. |
| **exposure ruler** | drag it left or right to make the picture brighter or darker, from -2 to +2 in thirds. |
| **TIMER** | 3 or 10 seconds before the shot. press the shutter again to cancel. |
| **TORCH** | turns the light on for dark places. |
| **FLIP** | switches to the front camera. |
| **•••** | date stamp, roll, location, highlight warning, and editing something from your gallery. |
| **volume keys** | take a picture, or start and stop a video. |
| **STILL / MOTION** | photos or video. |
| **tap the viewfinder** | focuses there. |
| **the square by the shutter** | opens the last thing you shot in the editor. |

photos are saved to `Pictures/SonaSight` and videos to `Movies/SonaSight`.

## the menu

- **date stamp** puts a gold date in the corner of stills, like old film cameras did.
- **roll** loads 24 or 36 exposures. you can't see what you've shot until the roll is full,
  and then it develops into your gallery all at once. *develop now* sends what's on the roll
  early.
- **location** saves where each photo and video was taken. along with the film, scene and
  lens, it's written into the photo's details.
- **highlight warning** draws gold stripes over the brightest parts when they're losing
  detail. it only shows in the viewfinder, never in what you shoot.

## editing

open the editor from the square by the shutter, or from *••• > edit* for anything in your
gallery. it works on photos and videos, and saving always makes a new copy, so the original
stays as it was.

- **FILM** picks a scene or any film, including MEISHO and your recipes. press and hold the
  picture to see the original.
- **TUNE** has the same sliders as the camera, plus exposure.
- **CROP** picks a frame shape, with the golden ratio guides on top. drag the picture to
  place it.
- **TRIM** (videos) sets where the clip starts and ends.

## code

```
app/src/main/java/com/sonasight/app/
  MainActivity.kt    starts the camera and asks for permissions
  CameraRig.kt       binds the camera, takes photos, records video, saves to the gallery
  FilmProcessor.kt   runs every camera frame through the film shader on the GPU
  FilmProgram.kt     the film shader: tone, colour, grain, glow and vignette
  FilmGlEffect.kt    the same shader as a video effect, for editing videos
  Film.kt            the films, MEISHO films, scenes and recipes
  Media.kt           saving, photo details, date stamp and rolls
  Imaging.kt         cropping, scaling and decoding photos
  LocationTracker.kt where you are, when location is on
  Prefs.kt           remembers your settings
  ui/CameraScreen.kt the camera screen and its controls
  ui/EditorScreen.kt the editor
  ui/Common.kt       colours, type, keys and sliders
  ui/Overlays.kt     the golden ratio guides
```

---

currently free to use since I have a day job.
