# Play Store listings (US, Germany, France, Spain)

Text for four extra store languages, in the folder layout fastlane uses:
`<language>/title.txt` (30 chars max), `short_description.txt` (80), `full_description.txt` (4000).

The India listing (en-IN, hi-IN) stays as it is in Play Console; these folders do not touch it.

## Add them

Either:

1. **Automatic**: GitHub, Actions, "Update Play listings", Run workflow. It uses the same
   `PLAY_SERVICE_ACCOUNT_JSON` secret as the build upload and sends only these four languages' text
   (no screenshots, no APK). Then press "Publish changes" in Play Console if managed publishing is on.
2. **By hand**: Play Console, Grow users, Store presence, Main store listing, Manage translations,
   add English (United States), German, French, Spanish (Spain), and paste each file.

## Screenshots

Play reuses the default screenshots for every language unless a language has its own. The current
ones show Hindi song names and Hinglish search, so for these languages upload screenshots with
English song titles (any copyright-free demo songs) and no Hindi text: record player, headphone
presets, Car mode, Android Auto, Last.fm card. Put them per language in Play Console, or add
`<language>/images/phoneScreenshots/1.png ...` here and run the workflow with screenshots on.
