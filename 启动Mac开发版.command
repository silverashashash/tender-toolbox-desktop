#!/bin/sh
set -eu
TOOLBOX_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$TOOLBOX_ROOT/.tools/jdk-17.0.20.1+1/Contents/Home/bin/java" -Dapple.awt.application.name="招标现场工具箱" -jar "$TOOLBOX_ROOT/target/offline-toolbox-0.1.0-SNAPSHOT.jar"
