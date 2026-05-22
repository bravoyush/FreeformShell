# MESSAGE 1 — paste this first
# -----------------------------------------------

# 🚀 Freeform Beta — Experimental Update

### ⚡ Core Engine
- **Android 14+ fixed** — LSPosed HiddenApiBypass replaces broken double-reflection
- **Direct Binder IPC** — resize, focus, close, force-stop skip shell commands entirely now
- **Proper window focus** — `setFocusedRootTask` replaces fake tap injection. Keyboard goes to the right app
- **Shizuku auto-reconnect** — no more manual restart if Shizuku dies
- **Cached reflection** — all method lookups cached after first call

### 🪟 Window Features
- **Paired Group Resizing** — drag shared edge to resize two snapped windows together
- **Tiled Layout Swap** — swap snapped window positions
- **Visual Corner Handles** — rounded touch targets at corners
- **Diagonal corner resize** — drag bottom corners
- **Force Close per app** — ⚠️ icon marks apps for full kill on close

### 🎨 Customization
- Title Bar Opacity slider (30%–100%)
- Drag & Resize tint overlay toggle
- Pill Shrink Style picker (Scale vs Handle/Bar) with preview
- Window Shadows toggle (experimental)


# MESSAGE 2 — paste this second
# -----------------------------------------------

### ⚙️ New Settings
- **Turn off Freeform on specific screens** — if you have multiple displays, you can now disable window controls on any screen you don't want managed
- **Per-display snap sensitivity** — 20dp to 150dp per screen
- **Launcher & Dock Avoidance** — dims title bars when launcher/dock is open
- **App launch display** — choose phone, secondary, or auto
- **Real-time Resize** — live content resize while dragging (flagship only)
- **Instant Shell Resizing** — disables ALL system animations globally

### ⚠️ Don't Touch These
- **DPI on phone display** — affects ALL apps, can brick your UI. Only change DPI on external displays. Fix: `wm density reset` via ADB
- **Instant Shell Resizing** — kills animations system-wide, persists after uninstall
- **Real-time Resize** — lags hard on mid-range devices
- **Bubble Minimize** — non-functional, under development
- **Tablet UI Mode** — crashes apps that don't support tablet layout

> `experimental-beta` · Based on v1.1 · Android 11–15+ · Requires Shizuku

📥 **Download:** https://drive.google.com/file/d/1AeyrrEn4xDWkM0QX766zS5231yEbdxru/view?usp=drive_link
