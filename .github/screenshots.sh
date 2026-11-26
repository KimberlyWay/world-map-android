#!/usr/bin/env bash
# Сценарий для эмулятора: открыть мир, нажать на карту, найти город, сменить вид.
set -ux
mkdir -p shots
shot() { adb exec-out screencap -p > "shots/$1.png"; }
# Нажать на элемент по его id (координаты берутся из разметки текущего экрана).
tap_id() {
  adb shell uiautomator dump /sdcard/ui.xml
  bounds=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep "id/$1\"" | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' | head -1)
  read -r x1 y1 x2 y2 <<< "$bounds"
  adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}

adb logcat -c
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.example.worldmap/.MainActivity
sleep 40
shot 1-world

adb shell input tap 540 1450
sleep 12
shot 2-tap

tap_id searchInput
sleep 2
adb shell input text "Kazan"
sleep 1
adb shell input keyevent 66
sleep 12
shot 3-results

# Если найдено несколько мест, выбрать первое в списке.
adb shell uiautomator dump /sdcard/ui.xml
if adb shell cat /sdcard/ui.xml | grep -q 'android:id/text1'; then
  tap_id text1
  sleep 25
fi
shot 4-city

tap_id closeCardButton
tap_id styleButton
sleep 3
shot 5-styles
# Четвёртый пункт списка — тёмная карта.
adb shell uiautomator dump /sdcard/ui.xml
bounds=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep 'android:id/text1' | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' | sed -n 4p)
read -r x1 y1 x2 y2 <<< "$bounds"
adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
sleep 25
shot 6-dark

tap_id worldButton
sleep 20
shot 7-dark-world

adb logcat -d > shots/logcat.txt
# Приложение должно остаться запущенным.
adb shell pidof com.example.worldmap
