# TMDB Rows for Monet

A **plugin for the Monet Launcher**: it turns TMDB into rows of movies and series.
Open source (MIT). That is all it does — no player, no launcher.

![TMDB Rows for Monet](docs/hero.jpg)

| | |
| --- | --- |
| **App home** — your rows | **Editor** — create a row |
| ![Home](docs/home.jpg) | ![Editor](docs/editor.jpg) |
| **In Monet** — rows ready to watch | **The first card is the cover** |
| ![Rows in Monet](docs/monet.jpg) | ![Category banner](docs/category.jpg) |
| **Loading state** | |
| ![Skeleton](docs/skeleton.jpg) | |

## How it works

1. Install the APK and paste your free [TMDB](https://www.themoviedb.org/settings/api) key.
2. Create a row: TMDB filters, **My list** (IDs or search) or a **streaming service**.
3. **Save and publish**, then add it in **Monet → Personalize → media row → TMDB Rows for Monet → the
   row name**.

Full guide: [English](docs/tutorial.md) · [Português](docs/tutorial.pt-BR.md).

## Rows

- **TMDB filters** — trending, popular, top rated, in theaters, on air, new series, discover.
- **My list** — TMDB IDs or links, or search by title.
- **Streaming service** — the main services, with a search field for the rest of the list, plus
  **Airing today** and **New series** for the chosen service.
- **Per row** — an optional cover card with the row name, ordering (including random), artwork
  (16:9 banner or 2:3 poster, optionally with BetterPosters covers) and player (Nuvio/Stremio).
- **Cards** — composed on the device: backdrop, title logo and tags (rating, year, genre, NEW) in the
  chosen language.

## Install

Download `tmdb-rows-for-monet-*.apk` from [Releases](../../releases) and install on an Android TV
(8.0+):

```bash
adb install -r tmdb-rows-for-monet-1.0.apk
```

## Build

```bash
./gradlew :app:assembleRelease   # ~1.6 MB, R8
./gradlew :app:testDebugUnitTest
```

Signing: `release.keystore` + `keystore.properties` (both git-ignored); without them the release uses
the debug key. Back up the keystore — future APKs must keep the same signature.

## Notes

- Built and tested with **Monet**; the cards open **Nuvio** or **Stremio**.
- Everything stays on the device; the key only goes to `api.themoviedb.org`.
- Up to 50 items per row.

## License

[MIT](LICENSE). Uses the TMDB API; not endorsed or certified by TMDB.
