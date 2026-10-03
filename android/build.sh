#!/bin/bash
# Mars VPN 安卓客户端构建脚本（手动 aapt2 流程）
# 背景：本环境 Gradle daemon 无法启动，改用 aapt2/d8/apksigner 手动构建
# 详见 docs/errors/error-002-gradle-daemon.md
set -e
cd "$(dirname "$0")"

export JAVA_HOME=~/jdk17
export PATH=$JAVA_HOME/bin:$PATH
BT=~/android-sdk/build-tools/34.0.0
PLATFORM=~/android-sdk/platforms/android-34/android.jar
OUT=mars-vpn-v1.0.apk

echo "[1/6] aapt2 compile..."
rm -rf build && mkdir -p build/compiled_res build/gen build/classes
$BT/aapt2 compile --dir app/src/main/res -o build/compiled_res/

echo "[2/6] aapt2 link..."
$BT/aapt2 link -o build/base.apk -I $PLATFORM \
  --manifest app/src/main/AndroidManifest.xml \
  --java build/gen build/compiled_res/*.flat

echo "[3/6] javac..."
javac -encoding UTF-8 -source 8 -target 8 -cp $PLATFORM -d build/classes \
  $(find build/gen app/src/main/java -name "*.java") 2>&1 | grep -v bootstrap || true

echo "[4/6] d8 (dex)..."
$BT/d8 --lib $PLATFORM --output build/ $(find build/classes -name "*.class")

echo "[5/6] 打包 classes.dex..."
cp build/base.apk build/unsigned.apk
(cd build && zip -q -j unsigned.apk classes.dex)

echo "[6/6] zipalign + apksigner..."
$BT/zipalign -f 4 build/unsigned.apk build/aligned.apk
[ -f build/debug.keystore ] || keytool -genkeypair -keystore build/debug.keystore \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
$BT/apksigner sign --ks build/debug.keystore --ks-pass pass:android \
  --key-pass pass:android --out $OUT build/aligned.apk
$BT/apksigner verify $OUT

echo "构建成功: $(pwd)/$OUT"
ls -lh $OUT
