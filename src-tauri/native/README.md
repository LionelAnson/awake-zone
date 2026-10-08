# Windows live glass bridge

`live_glass.cpp` is compiled into the Rust application by `cc` when the
`live-capture` feature is enabled on Windows. It uses OS D3D11, Direct2D,
DirectComposition and Windows Graphics Capture, with a transparent WebView
above the composition target. It does not require Windows App SDK.

The capture, crop, Gaussian blur and presentation objects belong to one native
worker thread. FrameArrived only signals an event. The worker drains stale
frames, limits swap-chain latency to one frame, and owns session cleanup.
The HWND is excluded only from this display capture session. No global display
affinity change, image file, network transmission or frame IPC is used.

Desktop mode uses the existing wallpaper path. Reparenting stops the native
renderer first. Hiding closes the capture session; showing recreates it. A
monitor change recreates capture. A size change recreates drawing resources.
The main process reports a runtime capability failure; it does not label API
success as proof of visual output. Cross-monitor spanning windows and physical
display removal have not been validated.

## Fixed projection

`generated/` contains C++/WinRT output from Windows SDK 10.0.26100.0 cppwinrt,
using this machine's Windows.Graphics.winmd. This is needed for the newer
IDisplayGraphicsCaptureSession exclusion API, absent from older projections.
Input SHA-256:
`2CC68B1B1B19FE2A921BAEDAFF99D2A78D5351C507716FE20B61AD5CB808343E`.
The original generation/build command is retained in projection-provenance.json.
Generated Microsoft file headers retain their license notices. Do not regenerate
from a different OS metadata file silently. Runtime support is queried by
interface conversion and exclusion-list readback; compilation alone proves
neither runtime availability nor correct pixels.

Tested host: Windows build 26200, x64. macOS does not compile this bridge.
