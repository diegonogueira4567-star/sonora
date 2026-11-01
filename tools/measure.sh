#!/bin/bash
# Measures startup, scan, scrolling, memory and CPU of the installed com.sonora.player on the
# connected device. Usage: tools/measure.sh LABEL  (prints a table; raw data in /tmp/sonora-measure-LABEL)
PKG=com.sonora.player; ACT=$PKG/.MainActivity; LABEL=${1:-run}; OUT=/tmp/sonora-measure-$LABEL; mkdir -p $OUT
median() { sort -n | awk '{a[NR]=$1} END {if (NR==0) {print "n/a"} else if (NR%2) {print a[(NR+1)/2]} else {print int((a[NR/2]+a[NR/2+1])/2)}}'; }
cpu_ticks() { adb shell cat /proc/$1/stat | awk '{print $14+$15}'; }
pid() { adb shell pidof $PKG | tr -d '\r'; }
tap_text() { adb shell uiautomator dump /sdcard/m.xml >/dev/null 2>&1; adb shell cat /sdcard/m.xml | python3 -I -c '
import sys,re
x=sys.stdin.read(); q=sys.argv[1]
for m in re.finditer(r"<node [^>]*>", x):
    n=m.group(0); t=re.search(r"text=\"([^\"]*)\"",n).group(1); d=re.search(r"content-desc=\"([^\"]*)\"",n).group(1)
    if q in (t,d):
        b=list(map(int,re.findall(r"\d+",re.search(r"bounds=\"([^\"]*)\"",n).group(1)))); print((b[0]+b[2])//2,(b[1]+b[3])//2); break' "$1" | { read x y; [ -n "$x" ] && adb shell input tap $x $y; }; }

read W H < <(adb shell wm size | tail -1 | grep -oE '[0-9]+x[0-9]+' | tr 'x' ' ')
X=$((W/2)); Y1=$((H*80/100)); Y2=$((H*25/100))
adb shell settings put global window_animation_scale 1; adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard
# --- cold start (process killed) ---
for i in 1 2 3 4 5; do
  adb shell am force-stop $PKG; sleep 1; adb logcat -c
  adb shell am start -W -n $ACT | awk '/TotalTime/ {print $2}' >> $OUT/cold.txt
  sleep 5
  adb logcat -d -s ActivityTaskManager:I | grep -oE 'Fully drawn [^ ]+: \+[0-9a-z]+' | grep -oE '[0-9]+s[0-9]+ms|[0-9]+ms' | head -1 | awk '{ if ($0 ~ /s/) { split($0,a,"s"); gsub("ms","",a[2]); print a[1]*1000+a[2] } else { gsub("ms",""); print } }' >> $OUT/drawn.txt
  adb logcat -d -s SonoraPerf:I | grep -oE 'scan [0-9]+ ms total=[0-9]+' | head -1 >> $OUT/scan.txt
done
# --- hot start (activity in background) ---
for i in 1 2 3 4 5; do
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell am start -W -n $ACT | awk '/TotalTime/ {print $2}' >> $OUT/hot.txt; sleep 2
done
# --- scrolling the songs list ---
tap_text "Biblioteca"; sleep 2; tap_text "Músicas"; sleep 3
adb shell dumpsys gfxinfo $PKG reset >/dev/null
for i in $(seq 1 12); do adb shell input swipe $X $Y1 $X $Y2 150; sleep 0.4; done
for i in $(seq 1 12); do adb shell input swipe $X $Y2 $X $Y1 150; sleep 0.4; done
adb shell dumpsys gfxinfo $PKG > $OUT/gfx.txt
adb shell dumpsys meminfo $PKG > $OUT/mem.txt
# --- CPU while playing in foreground (Home screen with mini player) for 20 s ---
tap_text "Início"; sleep 1; tap_text "Tocar tudo aleatório"; sleep 3
P=$(pid); a=$(cpu_ticks $P); sleep 20; b=$(cpu_ticks $P); echo $((b-a)) > $OUT/cpu_fg_playing.txt
# --- CPU while paused in background for 30 s ---
adb shell cmd media_session dispatch pause; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 3
P=$(pid); a=$(cpu_ticks $P); sleep 30; b=$(cpu_ticks $P); echo $((b-a)) > $OUT/cpu_bg_paused.txt
adb shell am start -n $ACT >/dev/null; sleep 2; adb shell cmd media_session dispatch pause

echo "== $LABEL =="
echo "cold start TotalTime ms (median of 5): $(cat $OUT/cold.txt | median)   all: $(tr '\n' ' ' < $OUT/cold.txt)"
echo "fully drawn ms (median):              $(cat $OUT/drawn.txt | median)   all: $(tr '\n' ' ' < $OUT/drawn.txt)"
echo "scan per cold start:                  $(cut -d' ' -f2 $OUT/scan.txt | tr '\n' ' ') ms  ($(head -1 $OUT/scan.txt | grep -oE 'total=[0-9]+'))"
echo "hot start TotalTime ms (median):      $(cat $OUT/hot.txt | median)   all: $(tr '\n' ' ' < $OUT/hot.txt)"
grep -E 'Total frames rendered|Janky frames:|50th percentile|90th percentile|99th percentile' $OUT/gfx.txt | head -5 | sed 's/^/scroll  /'
grep -E 'TOTAL PSS|TOTAL:' $OUT/mem.txt | head -1 | sed 's/^/memory  /'
echo "CPU ticks (1/100 s) foreground playing 20 s: $(cat $OUT/cpu_fg_playing.txt)"
echo "CPU ticks (1/100 s) background paused 30 s:  $(cat $OUT/cpu_bg_paused.txt)"
