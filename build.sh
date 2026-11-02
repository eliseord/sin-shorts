#!/usr/bin/env bash
# Compila el APK sin Gradle, con las herramientas de Android que trae Ubuntu/Debian:
#   sudo apt install openjdk-17-jdk aapt apksigner dalvik-exchange zipalign curl
# Resultado: build/SinShorts.apk
set -euo pipefail
cd "$(dirname "$0")"

# Se compila contra Android 13 (API 33) para poder usar gestos de accesibilidad.
# Si no indicas otro, se descarga una vez a .android-jar/
if [ -z "${ANDROID_JAR:-}" ]; then
  ANDROID_JAR=.android-jar/android-33.jar
  if [ ! -f "$ANDROID_JAR" ]; then
    echo "· Descargando android.jar (API 33)"
    mkdir -p .android-jar
    curl -fsSL -o "$ANDROID_JAR" https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-33/android.jar
  fi
fi
MIN_SDK=26
TARGET_SDK=35
VERSION_CODE=20
VERSION_NAME=2.9
KEYSTORE="${KEYSTORE:-sinshorts.keystore}"
KS_PASS="${KS_PASS:-sinshorts}"
SRC=app/src/main

rm -rf build
mkdir -p build/gen build/classes build/dex

echo "· Recursos"
aapt package -f -m -J build/gen -M "$SRC/AndroidManifest.xml" -S "$SRC/res" -I "$ANDROID_JAR" \
  --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK

echo "· Java"
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 -classpath "$ANDROID_JAR" -d build/classes \
  $(find build/gen "$SRC/java" -name '*.java')

echo "· Dex"
dalvik-exchange --dex --min-sdk-version=$MIN_SDK --output=build/dex/classes.dex build/classes

echo "· Empaquetado"
aapt package -f -M "$SRC/AndroidManifest.xml" -S "$SRC/res" -I "$ANDROID_JAR" \
  --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
  --version-code $VERSION_CODE --version-name $VERSION_NAME \
  -F build/unsigned.apk
(cd build/dex && aapt add ../unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 build/unsigned.apk build/aligned.apk

echo "· Firma"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias sinshorts \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Sin Shorts" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias sinshorts \
  --out build/SinShorts.apk build/aligned.apk
apksigner verify build/SinShorts.apk
echo "Listo: build/SinShorts.apk"
