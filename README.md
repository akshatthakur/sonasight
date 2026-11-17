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

## the controls

words in caps are the labels in the app.

| control | what it does |
| --- | --- |
| **PHI / SPIRAL / TRI / OFF** | the framing guide. PHI splits the frame at the golden ratio, SPIRAL draws a golden spiral, and TRI draws golden triangles. tap to go to the next one. |
| **TURN** | moves the spiral to another corner, or flips the triangles. |
| **LEVEL** | a horizon line that turns gold, with a small tick you can feel, when the camera is straight. |
| **3:2** | the frame shape for stills: 3:2, 4:3, 1:1, 16:9 or 65:24. video is always 16:9. |
| **24 / 28 / 35 / 50** | the lens, as a 35mm equivalent. |
| **exposure ruler** | drag it left or right to make the picture brighter or darker, from -2 to +2 in thirds. |
| **FLIP** | switches to the front camera. |
| **STILL / MOTION** | photos or video. |
| **tap the viewfinder** | focuses there. |
| **the square by the shutter** | opens the last thing you shot. |

photos are saved to `Pictures/SonaSight` and videos to `Movies/SonaSight`.

## code

```
app/src/main/java/com/sonasight/app/
  MainActivity.kt    starts the camera and asks for permissions
  CameraRig.kt       binds the camera, takes photos, records video, saves to the gallery
  FilmProcessor.kt   runs every camera frame through the film shader on the GPU
  FilmProgram.kt     the film shader: tone, colour, grain, glow and vignette
  Film.kt            the films and scenes
  Prefs.kt           remembers your settings
  ui/CameraScreen.kt the screen and its controls
  ui/Overlays.kt     the golden ratio guides
```

---

made by Akshat Thakur.
