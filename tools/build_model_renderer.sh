#!/bin/sh
set -eu
: "${FILAMENT_SDK:?Set FILAMENT_SDK to the extracted Filament 1.66.0 macOS SDK}"
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$TASK_ROOT/verification/bin"
TASK_SDK=$(xcrun --show-sdk-path)
clang++ -isysroot "$TASK_SDK" -isystem "$TASK_SDK/usr/include/c++/v1" -std=c++17 -O2 -I "$FILAMENT_SDK/include" "$TASK_ROOT/tools/render_vehicle.cpp" -L "$FILAMENT_SDK/lib/arm64" -lgltfio_core -luberarchive -lktxreader -limage -lfilament -lbackend -lshaders -lfilabridge -lfilaflat -lutils -lgeometry -lmeshoptimizer -lbasis_transcoder -ldracodec -luberzlib -lzstd -lbluevk -lbluegl -lvkshaders -lsmol-v -labseil -framework Metal -framework Foundation -framework Cocoa -framework CoreVideo -framework QuartzCore -framework IOKit -o "$TASK_ROOT/verification/bin/render_vehicle"
