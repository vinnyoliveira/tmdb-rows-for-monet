# TMDB Rows for Monet — step by step

This guide takes you from zero to rows on your TV in about ten minutes.

## What you need

- An Android TV / Google TV box with **Android 8.0+** and the **Monet Launcher**.
- **Nuvio** or **Stremio** installed (they open the titles; this app does not play anything).
- A free **TMDB** account — it takes two minutes and is what makes the rows accurate.

## 1. Install

Download the latest `tmdb-rows-for-monet-*.apk` from the **Releases** page and install it on the TV
(USB, `adb`, or a file manager). Allow installs from unknown sources when Android asks.

```bash
adb install -r tmdb-rows-for-monet-1.2.apk
```

## 2. First launch: language and key

1. Pick the **app language** (Sistema, Português, English, Español, Français). You can change it
   later in **Settings → App language**.
2. On a phone or computer, open [themoviedb.org](https://www.themoviedb.org/), create a free
   account, go to **Settings → API** and copy the **API Key (v3 auth)**.
3. In the app, focus the key field, press **OK**, paste the key and choose **Validate and start**.

The app never ships a TMDB key: yours stays on the TV.

## 3. Your first row

1. On the home screen, choose **New row**.
2. Keep **TMDB filters** as the source.
3. Pick **Movies** or **Series** and a section (Popular, Trending, Top rated, …).
4. Give it a name or leave it empty to use the automatic one.
5. Look at the **preview** on the right — that is exactly what will be published.
6. Choose **Save and publish**.

The row becomes an Android TV channel. It is not on the launcher yet — that is the next step.

## 4. Put it on the launcher

1. Open **Monet → Personalize**.
2. Add a **media row**.
3. Choose **TMDB Rows for Monet** and then the channel **with the name of your row**.

> Each row is a separate channel. If Monet shows a mix, you picked the app instead of the row —
> choose the row by its name.

## 5. The three ways to fill a row

### TMDB filters

The fastest option. Choose the type, the section and, in **Advanced filters**, genres, rating, year
and ordering.

### My list

Pick titles by hand:

- paste TMDB IDs (`550, 1399`) or a link (`https://www.themoviedb.org/tv/1399-game-of-thrones`);
- or use **Search TMDB** and add results with **Add**.

Useful for "my comfort movies" or a list of novelas.

### Streaming service

Pick one of the main services (Netflix, Prime Video, Disney+, Apple TV, Max, Globoplay…) or use the
**search field** for the rest of the list. The logos come from TMDB.

- **Novelas:** Series · Globoplay · genre **Soap**.
- With Globoplay and the News / Talk / Reality genres you get programmes instead of series.

## 6. Per-row options that matter

- **Section** — on a streaming service you can also pick **Airing today** or **New series** (last 30
  days), the same lists as TMDB's own site, for the service you chose.
- **Ordering** — most popular, best rated, newest or **random**. Random reshuffles on every refresh,
  which is great for a "surprise me" row. Series also get **New episodes**, ordered by the date of the
  latest episode.
- **Artwork** — 16:9 banner (recommended for Monet) or 2:3 poster.
- **Open in** — this row can open in Nuvio or Stremio even if the rest of the app uses the other one.

## 7. The cover card (optional)

Each row can start with a **cover card**: the row name written vertically on a dark strip, over a
random backdrop from the row. Turn it on or off in the editor under **Row name on the first card**.
It only applies to 16:9 banners; a row called *Disney Movies* starts with `DISNEY MOVIES` turned on
its side, like a shelf cover in the launcher.

The other cards are composed on the device: backdrop, a dark scrim, the title (like a watermark) and a
dark band across the bottom with the tags — **rating**, **year** and **genre**. Every card in a row
uses the same type sizes, so nothing jumps around. Two ribbons can appear in the top corner:

- `NEW` (gold) for movies released in the last 15 days;
- `NEW EPISODE` (blue) for series that aired an episode in the last 3 days.

Everything comes from TMDB, so it follows the language you picked. Turn the title, the tags and each
ribbon on or off in **Settings → Cards**.

## 8. Language and posters

- **App language** changes the texts only.
- **Posters and titles** (Settings) changes the language of artwork and titles. Automatic follows the
  device; you can pick any language TMDB supports (about 180).
- **BetterPosters covers** (Settings → Artwork, off by default) uses 2:3 posters from `btttr.cc` with
  the title, genre and rating printed on the image, in the language chosen above. Needs internet; when
  it fails the TMDB poster is used. Only applies to the **Poster 2:3** artwork.
  - **Cons:** it is a third-party service (it can change or go offline), it adds one extra request per
    title when publishing and keeps about 70 KB per poster on the TV.

## 9. Sync and battery

Automatic sync is **off by default**: with it off the app schedules no background work and only
publishes when you press **Refresh**. Turn it on in **Settings → Sync** (6/12/24 h) if you want the
rows to keep themselves fresh.

## 10. Updating the app

When there is a new release, the app shows a popup with the version number and a **Download and
install** button: it downloads the APK, hands it to Android and the system asks you to confirm.
Rows and key stay. You can also install a new APK by hand over the old one (same signature). If you
uninstall, the channels and settings are removed.

## Troubleshooting

**A row is empty.** Open its preview: if it also says "No results with these filters", loosen a
filter (rating/year or one of the genres).

**The row shows other titles.** In Monet you added the app, not the row. Remove it and add the
channel **with the row name**.

**The card opens the wrong app.** Set **Tap on a poster** in Settings, and check the row's
**Open in**.

**Images are missing.** The banners are generated on the device; the first publish downloads the
artwork. Give it a minute and press **Refresh** again.

**The key stopped working.** Generate a new one at TMDB → Settings → API and replace it in Settings.

**Nothing appears in the launcher.** Publish once (the channel only exists after the first publish),
then reopen Monet.

## FAQ

**Is it open source?** Yes — MIT. Anyone can read, change and build it. The only requirement is your
own free TMDB key.

**Does it host or stream anything?** No. It only publishes TMDB metadata; playback happens in Nuvio
or Stremio with your own sources.

**Where are my rows stored?** On the TV, in the app's private storage. No account, no cloud.

**Why 50 items per row?** Android TV channels are not meant for hundreds of entries; 20 is the sweet
spot.

**Can I use it on a phone?** It runs, but the interface is built for a remote.
