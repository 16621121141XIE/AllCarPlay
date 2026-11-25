#!/bin/bash
# 把 app/libs/icar-sdk.dex（ICCOA CarLink SDK）追加进 debug APK 的 classesN.dex，
# 再 zipalign + 用 debug keystore 重签，产物放到工程根 AllCarPlay-<版本>.apk。
set -e
cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-$HOME/android-toolchain/sdk}"
BT="$SDK/build-tools/35.0.0"
APK_IN="app/build/outputs/apk/debug/app-debug.apk"
DEX="app/libs/icar-sdk.dex"
VER=$(grep '^allcarplayVersionName=' gradle.properties | cut -d= -f2)
APK_OUT="../../AllCarPlay-${VER}.apk"

[ -f "$DEX" ] || { echo "缺少 $DEX"; exit 1; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp "$APK_IN" "$WORK/in.apk"

# 找下一个 classesN.dex 序号（原有 22 个 dex → classes23.dex）
N=$(unzip -l "$WORK/in.apk" | sed -nE 's/.*(^| )classes([0-9]+)\.dex$/\2/p' | sort -n | tail -1)
[ -z "$N" ] && N=1
N=$((N + 1))
NAME="classes${N}.dex"
cp "$DEX" "$WORK/$NAME"
(cd "$WORK" && zip -q -X in.apk "$NAME")

"$BT/zipalign" -p -f 4 "$WORK/in.apk" "$WORK/aligned.apk"
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
    --key-pass pass:android --ks-key-alias androiddebugkey --out "$WORK/signed.apk" "$WORK/aligned.apk"
"$BT/apksigner" verify "$WORK/signed.apk"
cp "$WORK/signed.apk" "$APK_OUT"
echo "已打包 $APK_OUT（含 $NAME，$(stat -f%z "$APK_OUT") 字节）"
