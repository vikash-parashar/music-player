#!/usr/bin/env bash
# Smoke test for the R8 build on an emulator: launch, open Car mode with autoplay, check a song plays.
set -u
APK=$(ls Ampli-*-r8.apk | head -1)
PKG=com.vikash.ampli
adb install -r "$APK"
adb shell mkdir -p /sdcard/Music
adb push ampli_test_song.wav /sdcard/Music/ampli_test_song.wav
adb shell content call --method scan_volume --uri content://media --arg external_primary || true
sleep 5
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO || true
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -W -n $PKG/com.vikash.mp3player.MainActivity
sleep 12
adb shell am start -W -a com.vikash.ampli.action.CAR_MODE -p $PKG
sleep 15
adb logcat -d > logcat.txt || true
adb shell dumpsys media_session > session.txt || true
adb exec-out screencap -p > screen.png || true
if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "::error::App crashed"; grep -A40 "FATAL EXCEPTION" logcat.txt; exit 1
fi
if ! adb shell pidof $PKG >/dev/null; then
  echo "::error::App is not running"; tail -200 logcat.txt; exit 1
fi
if ! grep -Eq "state=(PLAYING\(3\)|3)[,)]" session.txt; then
  echo "::error::Song did not start playing"; grep -B2 -A14 "$PKG" session.txt | head -80; exit 1
fi
echo "R8 build launched, opened Car mode and is playing."
