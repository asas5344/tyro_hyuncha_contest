#!/bin/sh
# Use Filament 1.66.0 matc, matching gradle/libs.versions.toml.
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${MATC:?Set MATC to the Filament 1.66.0 matc executable}"
for material in camera_feed guidance; do
    "$MATC" -p mobile -a opengl -o "$TASK_ROOT/app/src/main/assets/materials/$material.filamat" "$TASK_ROOT/materials/$material.mat"
done
