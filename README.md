# MP3 Player for Android

A local + online music player built with Kotlin, Jetpack Compose and Media3 (ExoPlayer).
Free with ads (AdMob), with a one-time in-app purchase that removes ads forever.

## What it does

| Area | Features |
|---|---|
| Local library | Every song on the phone and SD card (MP3 at any bitrate, CBR/VBR, plus M4A, AAC, FLAC, OGG, OPUS, WAV). Tabs for Songs, Albums, Artists and Folders, instant search, shuffle all. |
| Playback | Background playback with notification and lock-screen controls, headset/Bluetooth/car buttons, gapless playback, shuffle, repeat one/all, seek bar, queue (play next, add, remove, jump). Pauses for calls and when headphones are unplugged. |
| Musicolet-style extras | Resumes the last queue and position after restart, equalizer with presets (saved), sleep timer. |
| Online | Search and stream free, legally licensed music from [Jamendo](https://www.jamendo.com) (600k+ Creative Commons tracks). Popular-this-week list. Download tracks whose license allows it into `Music/MP3 Player`, after which they appear in the Library. |
| Money | AdMob adaptive banner (with Google's GDPR consent form), one-time "Remove ads" purchase via Play Billing. Paid users never load the ads SDK. |

### Why Jamendo and not YouTube or "any song"
Google Play removes apps that download or stream copyrighted music from sources like YouTube
without a license, and the developer account can be banned. Jamendo's artists publish under
Creative Commons, so streaming is allowed and downloads are allowed per track (the app only shows
"Download" when the track permits it).

**Check before launch:** Jamendo's API is free for non-commercial use; an ad-supported app may
need their commercial agreement (devportal.jamendo.com, or email them). If that does not work
out, the Internet Archive's audio collection (archive.org advancedsearch API) is a free fallback;
`JamendoRepository` is the only file to swap.

## Project layout

```
app/src/main/java/com/vikash/mp3player/
  MainActivity.kt            entry point; starts billing and ad consent
  MusicApp.kt                application class / dependency container
  data/
    Song.kt                  track model (local or online)
    LocalMusicRepository.kt  MediaStore scan of the phone's music
    JamendoRepository.kt     online search + stream URLs
    SongDownloader.kt        downloads via Android DownloadManager
  playback/
    PlaybackService.kt       MediaSessionService that owns ExoPlayer
    PlayerConnection.kt      UI-side MediaController, state flow, queue save/restore
    EqualizerController.kt   platform equalizer, saved settings
    SleepTimer.kt
  monetization/
    BillingManager.kt        one-time "remove_ads" purchase
    Ads.kt                   consent + AdMob init + BannerAd composable
  ui/                        Compose screens (Library, Online, Now playing/Queue, Settings)
```

If you think in Go terms: `data/` is your repository layer, `PlaybackService` is a long-running
worker the OS keeps alive, `PlayerConnection` is its client, and `StateFlow` is a typed channel
that always holds the latest value.

## Run it

1. Install [Android Studio](https://developer.android.com/studio) (it bundles the JDK and Android SDK).
2. File > Open > this `mp3-player` folder. Let Gradle sync (first sync downloads dependencies).
3. Copy `local.properties.example` to `local.properties` (Android Studio already created one with
   `sdk.dir`; just add the lines) and set `JAMENDO_CLIENT_ID` (free key from https://devportal.jamendo.com).
   Without it the app works fully offline and the Online tab says it is off.
4. Plug in a phone with USB debugging on, or start an emulator, and press Run.
   Put a few MP3s on the phone (or emulator `Music/` folder) to see the library.

Debug builds always show Google's **test** ads, so you can't accidentally click your own real ads
(which gets AdMob accounts banned).

If Android Studio offers to upgrade the Gradle plugin or library versions, accepting is fine;
versions live in `gradle/libs.versions.toml`.

## Monetization setup

1. **Google Play developer account**: one-time $25 at https://play.google.com/console.
2. **AdMob**: create an account at https://admob.google.com, add an Android app, create a
   *Banner* ad unit. Put the IDs in `local.properties` as `ADMOB_APP_ID` and `ADMOB_BANNER_ID`
   (only release builds use them). Link AdMob to the Play app once it is published.
3. **Remove ads product**: in Play Console > your app > Monetize > Products > In-app products,
   create a one-time product with ID `remove_ads` and a price (e.g. ₹99–149 / $1.99–2.99).
   Products only appear after you upload a build that includes billing to a testing track.
   Add yourself as a license tester (Play Console > Settings > License testing) to buy without being charged.
4. **Privacy policy + Data safety**: AdMob collects the advertising ID and device data, so Play
   requires a privacy policy URL and an accurate Data safety form (declare "Device or other IDs"
   and "App interactions", shared with Google for advertising). The consent form is already wired.

Optional, later: verify purchases server-side. A small Go Lambda behind API Gateway can call the
Google Play Developer API (`purchases.products.get`) with the purchase token; today the app trusts
Play's client-side result, which is normal for a low-price unlock.

## Release

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Build > Generate Signed App Bundle (create and back up an upload keystore; never commit it).
3. Upload the `.aab` to Internal testing first, then Production. Target SDK 35 meets Play's current requirement.
4. Store listing: title, short description, screenshots, a 512px icon and a feature graphic.
   Replace the placeholder icon via right-click `res` > New > Image Asset.

## Ideas for v2
Playlists and favourites, home-screen widget, lyrics (LRC files), tag editor, crossfade,
Android Auto browsing (PlaybackService can become a `MediaLibraryService`), swipe-to-play-next.
