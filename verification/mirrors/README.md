# Latest mirror assembly verification

LATEST actual Android captures: `driver-layout-left-assembly-final.jpg` and `driver-layout-right-assembly-final.jpg`. The `isolatedleft` / `isolatedright` assembly-final images remove parked cars to verify the contour without neighboring occlusion. All other image suffixes are historical intermediate versions.

User requested identical-looking left/right mirrors from the driver's seat, with the right physically turned farther inward. Both glass and housing now share a 16:9 superellipse contour and the same driver-facing yaw transform. The whole assembly is moved 0.18 m rearward / 0.08 m upward from the old authored landmark to clear the A-pillar and door. Old housings and mounts are removed only in own-car driver variants. Approved source car GLBs, body, hood, roof, windshield and physical profiles are preserved.

Generation: `python3 tools/generate_driver_mirrors.py`. Creates `_driver.glb` variants, `_mirror_stalks.glb` and `driver_mirror.glb`. Run this after regenerating source car models. Runtime housing and stalks use layer 8, visible to the driver but excluded from mirror feeds to prevent self-occlusion. Video surfaces stay on layer 2. Minimap moves below the focused mirror.

Verification: actual home/parking left-right-front test + empty-adjacent-bay contour test passed. 70 unit tests passed, including driver-facing normal/mount checks on all four car profiles. Source body/hood/roof/windshield buffers are byte-identical in all driver variants. Actual image review used Grandeur; this does not claim all four updated mirror models have been visually approved on Android.

---

# Side mirror verification

Actual Pleos emulator captures, not offline renders. Launch mode: standalone terminal, `-gpu host`.

Changes:
- Side feed 256x160 -> 768x432, FXAA, linear texture sampling.
- Rounded glass mask fitted inside housing; shared dimensions 0.20 x 0.1125 m.
- Driver side-look zoom preserves the original ~32% screen-height framing using actual glass size.
- Feed looks 22 degrees outward, 14 degrees downward on left / 18 on right. Own body stays along the inner edge.
- Render-target UV coordinates handled separately from bitmap uploads; automatic UV flipping explicitly controlled.
- Already tone-mapped camera color is converted back before the main view tone-maps it again, avoiding washed-out mirrors.
- Selected side feed requested at 20 Hz; front view retains 10 Hz. These are update limits, not measured sustained FPS guarantees.

Reference for texture conventions and inverse tone mapping: https://google.github.io/filament/Materials.md.html

The `resolution` and `calibrated` images are intermediate states, NOT final results. `corrected` images show the orientation/color fix before the final zoom adjustment.

Final captures: `driver-layout-left-final.jpg`, `driver-layout-right-final.jpg`, and `driver-layout-returned-final.jpg`. These are actual Android emulator screenshots. The focused mirror is approximately one third of the viewport height. The left image shows the boundary line / own side; the right shows the adjacent rear vehicle / bay line / own side. Actual home->parking->left->right->front instrumentation test passed; 68 unit tests passed.
