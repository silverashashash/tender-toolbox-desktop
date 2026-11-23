#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT"
JDK="$ROOT/.tools/jdk-17.0.20.1+1/Contents/Home"
if [ "$(uname -s)" != Darwin ] || [ "$(uname -m)" != arm64 ]; then
  echo '此脚本针对当前 Apple Silicon Mac；Windows 使用单独的构建流程。' >&2
  exit 1
fi
./mvn-local -q package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib
BUILD=$(mktemp -d "$ROOT/target/mac-build-XXXXXX")
mkdir "$BUILD/input"
cp target/offline-toolbox-0.1.0-SNAPSHOT.jar "$BUILD/input/"
cp -R target/lib "$BUILD/input/lib"
"$JDK/bin/jpackage" --type app-image --input "$BUILD/input" \
  --main-jar offline-toolbox-0.1.0-SNAPSHOT.jar --main-class local.tender.App \
  --name TenderToolbox --app-version 1.0.2 \
  --mac-package-identifier local.tender.toolbox --dest "$BUILD/app" \
  --add-modules java.desktop,java.logging,java.xml,java.naming,java.sql,java.management,jdk.crypto.ec,jdk.charsets
"$BUILD/app/TenderToolbox.app/Contents/MacOS/TenderToolbox" --verify-demo "$BUILD/verification"
printf '\nMac 开发验证应用（包含运行环境）：\n%s\n' "$BUILD/app/TenderToolbox.app"
