# Freeform Beta — Experimental Branch Documentation

> **You are on the `experimental-beta` branch.** This build is called **"Freeform Beta"** and contains many new features on top of v1.1. Some features are still being tested and may not be perfectly stable on all devices.

---

## 📋 Table of Contents

1. [What Changed Since v1.1?](#-whats-new-since-v11)
2. [How to Set Up & Use Freeform Beta](#-how-to-set-up--use-freeform-beta)
3. [Navigating the App — Tab by Tab](#-navigating-the-app--tab-by-tab)
4. [Recommended Launchers](#-recommended-launchers)
5. [Settings Safety Guide — What NOT to Touch](#-settings-safety-guide--what-not-to-touch)
6. [Known Limitations](#-known-limitations)

---

## 🆕 What's New Since v1.1?

Here's everything that changed from v1.1 (the last stable release) to this experimental-beta build, explained in plain language.

---

### 1. Android 14+ Now Works Properly (Hidden API Fix)

**The Problem in v1.1:** On Android 14 and newer, Google completely blocked the old trick FreeformShell used to access hidden system features. The app would silently fall back to slower shell commands, making everything sluggish — and you wouldn't even know why.

**What's Fixed:** The app now uses the [LSPosed HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) library, which cleanly unlocks all hidden APIs on Android 9 through Android 15+. This means direct, instant system calls work again on modern devices.

**You'll Notice:** Window operations (resize, focus, close) are noticeably faster on Android 14+ devices. No more silent performance degradation.

---

### 2. Clicking a Window Now Properly Gives It Focus (Direct Focus Routing)

**The Problem in v1.1:** When you tapped a window's title bar to bring it to the front, the window would visually appear on top — but the keyboard and touch input might still go to the *wrong* app behind it. This was because Android 14's security blocked the old "fake tap" method FreeformShell used.

**What's Fixed:** The app now directly tells Android's Window Manager "make this the active window" using the system's own `setFocusedRootTask` API (with automatic fallback to `setFocusedTask` on older Android versions). No more fake tap injection.

**You'll Notice:** Tapping a title bar instantly makes that app the keyboard/input target. This is especially obvious when typing — the keyboard now always goes to the right app.

---

### 3. Shizuku Connection is Now Monitored in Real-Time

**The Problem in v1.1:** If Shizuku crashed or was restarted while FreeformShell was running, the app wouldn't know. Everything would silently break until you manually restarted the app.

**What's Fixed:** A new **ShizukuLifecycleManager** watches the Shizuku connection in the background. If Shizuku reconnects, the app automatically re-initializes. If it disconnects, stale connections are cleaned up immediately. The main screen's Shizuku status chip also updates in real-time.

**You'll Notice:** The green "Shizuku" status badge in the app is now live. If you restart Shizuku, the app picks it up automatically — no need to restart FreeformShell.

---

### 4. Direct Binder Calls Replace Shell Commands (Where Possible)

**The Problem in v1.1:** Almost every action (resize, move to front, close, force stop) was done by spawning a shell command. Even with the persistent shell, this added latency and fragility.

**What's Fixed:** The app now uses **direct Binder IPC** (through Shizuku) for the most common actions:

| Action | v1.1 Method | Beta Method |
|---|---|---|
| Resize window | Shell: `cmd activity task resize ...` | Direct: `IActivityTaskManager.resizeTask()` |
| Bring to front | Shell: `cmd activity task movetotop ...` | Direct: `IActivityTaskManager.moveTaskToFront()` |
| Send to back | Shell: `cmd activity task move-to-back ...` | Direct: `IActivityTaskManager.moveTaskToBack()` |
| Close window | Shell: `cmd activity task remove ...` | Direct: `IActivityTaskManager.removeTask()` |
| Force stop app | Shell: `am force-stop ...` | Direct: `IActivityManager.forceStopPackage()` |
| Focus window | Shell: `input tap x y` (fake tap!) | Direct: `setFocusedRootTask()` |

If a direct call fails (e.g., unusual OEM ROM), the app automatically falls back to the persistent shell, and then to one-shot shell as a last resort. **Three layers of safety.**

**You'll Notice:** Everything feels faster and more reliable, especially on Android 14+. Close buttons work instantly. Windows gain focus immediately.

---

### 5. All Reflective Methods Are Now Cached

**Technical but important:** Previously, every time the app needed to call a system method (like `resizeTask`), it would use Java reflection to look up that method from scratch. This lookup is slow.

**What's Fixed:** All reflected `Method` objects are now cached after the first lookup. Subsequent calls skip the lookup entirely.

**You'll Notice:** Slightly faster operations across the board, especially during rapid window movements.

---

### 6. Multi-Display Shell Controls (Per-Display Toggle)

**New Feature:** You can now choose which screens should have Freeform Shell controls active.

- Go to **Settings tab → Active Shell Displays**
- Toggle each connected display ON or OFF
- Disabled displays won't show title bars, resize handles, or any overlays
- Disabled displays also disappear from the target display picker on the Windows tab

**When to use:** If you have a secondary monitor that you're using for a different purpose (like a presentation), you can turn off FreeformShell controls on just that screen.

---

### 7. Per-Display Snapping Sensitivity

**New Feature:** You can now adjust how aggressively windows snap to edges on each display independently.

- Go to **Settings tab → Snapping Sensitivity per Display** (under Workspace Auto-Snap)
- Drag the slider while watching the live overlay guide appear on the target display
- Range: 20dp (very tight, hard to trigger) → 150dp (very generous, easy to trigger)
- Default: 100dp

**When to use:** Phones may want lower sensitivity (to avoid accidental snaps), while large monitors benefit from high sensitivity.

---

### 8. Visual Corner Resize Handles

**New Feature:** Toggle visible rounded handles at window corners for easier touch-based diagonal resizing.

- Go to **Settings tab → Show Visual Corner Handles**
- Toggle globally and per-display
- Note: Diagonal resizing (by dragging corners) always works even with visual handles turned off — they're just a visual aid

---

### 9. Paired Group Resizing

**New Feature:** When two windows are snapped side-by-side (like a left-right split), you can resize them together in perfect unison by dragging their shared edge.

- Go to **Settings tab → Paired Group Resizing**
- Toggle globally and per-display
- When enabled, dragging the splitter handle between two paired/snapped windows resizes both at once

---

### 10. Tiled Layout Swap

**New Feature:** Swap the positions of two snapped/tiled windows by tapping on their bounds representation in the switcher.

- Go to **Settings tab → Tiled Layout Swap**
- Toggle globally and per-display

---

### 11. Launcher & Dock Avoidance

**New Feature:** When your selected launcher's start menu or dock is open, FreeformShell dims all window title bars to 15% opacity so they don't get in the way.

- Go to **Settings tab → Launcher & Dock Avoidance**
- Select your launcher from the auto-detected list, or search for any installed app
- Supports all home launchers + Taskbar (farmerbb)

---

### 12. Drag & Resize Tint Overlay

**New Feature:** A semi-transparent colored tint overlay now covers the window during drag/resize gestures, making it clear which window you're manipulating.

- Go to **Customization tab → Drag & Resize Tint Overlay** toggle
- ON by default

---

### 13. Title Bar Opacity Control

**New Feature:** Adjust the opacity of title bars when they are in their normal (non-interacting) state.

- Go to **Customization tab → Normal Title Bar Opacity** slider
- Range: 30% (very transparent) → 100% (fully opaque)

---

### 14. Pill Shrink Style Picker

**New Feature:** Choose between two different animations for how the title pill shrinks when inactive:

- **Scale Transform** (the classic v1.1 style) — the whole pill shrinks to a smaller version of itself
- **Handle/Bar Resizing** (new) — the pill physically shrinks its width and height down to a tiny bar/handle

- Go to **Customization tab → Pill Auto-Shrink → Shrink Style**
- An animated preview card shows you exactly what each style looks like

---

### 15. Real-time Resize Toggle

**New Feature:** Choose whether windows resize continuously as you drag, or only update when you release your finger.

- Go to **Settings tab → Experimental → Real-time Resize**
- **ON:** Window content updates live as you drag (looks great, but needs a powerful device)
- **OFF:** Only the outline/preview resizes; the actual window snaps to the final size on release (smoother on weaker devices)

---

### 16. Instant Shell Resizing (Disable System Animations)

**New Feature:** Completely disables Android's window resize transition animations system-wide.

- Go to **Settings tab → Experimental → Instant Shell Resizing**
- This sets `window_animation_scale`, `transition_animation_scale`, and `animator_duration_scale` to 0 globally
- Makes all window resizing and snapping 100% instant with zero transition jitter

> ⚠️ **Warning:** This affects ALL apps on your phone, not just FreeformShell. If you turn this on, all app transitions everywhere will be instant (no open/close animations).

---

### 17. Force Close per App

**New Feature:** In the active windows list, you can now mark specific apps for "Force Close" behavior. When you hit Close on a marked app, it uses `forceStopPackage` instead of just removing the task — this fully kills the app process.

- On the **Windows tab**, tap the ⚠️ warning icon next to an app to toggle Force Close mode
- The Close button text changes to "Force Stop" for marked apps
- This setting persists across restarts

---

### 18. App Launch Display Preference

**New Feature:** Choose where the FreeformShell app itself opens:

- **Phone Screen:** Always open on the primary display
- **Secondary Screen:** Always open on the secondary/external display
- **Automatic:** Open wherever Android decides

Go to **Settings tab → Launch Screen Preference**

---

## 🚀 How to Set Up & Use Freeform Beta

### Prerequisites

| Requirement | Why |
|---|---|
| **Android 11 or newer** | Freeform mode APIs are available starting from Android 11 |
| **Shizuku** installed and running | FreeformShell needs elevated permissions to manage windows. [Get Shizuku here](https://shizuku.rikka.app/) |
| **"Display over other apps"** permission granted | Required for the overlay title bars and resize handles |

### First-Time Setup

1. **Install Shizuku** from the Play Store and start it (via ADB or Wireless ADB)
2. **Install Freeform Beta** (this app)
3. **Open the app** — it will ask for two permissions:
   - **Shizuku permission** — tap the Shizuku status chip to grant
   - **Overlay permission** — tap the Overlay status chip; it will open Android settings where you toggle "Display over other apps" ON
4. Both status chips should turn **green** ✅
5. The overlay service starts automatically when both permissions are granted

### Launching an App in Freeform Mode

1. Go to the **Windows** tab (first tab)
2. Tap **"Launch Application"**
3. Search for the app you want
4. Tap it to select → set the window size (defaults to 50% of screen) → tap **"Launch"**
5. The app opens in a floating freeform window with a title bar and resize handles

### Multi-App Launch Queue

1. Tap **"Launch Application"** to open the app picker
2. Instead of tapping an app directly, tap the **+ circle** icon on the right to add it to the queue
3. Add multiple apps to the queue
4. Close the picker — you'll see a "Launch Queue" card showing all queued apps
5. Select your target display, then tap **"Launch All"** — all apps open cascaded

### Window Controls

| Action | How |
|---|---|
| **Move window** | Drag the title bar |
| **Resize from edges** | Drag the left, right, or bottom invisible resize strips |
| **Resize from corners** | Drag from the bottom-left or bottom-right corners |
| **Snap to half screen** | Drag a window to the left or right edge |
| **Snap to quarter screen** | Drag a window to a corner |
| **Maximize** | Drag to the top edge and hold for 4 seconds |
| **Close window** | Tap the red ✕ button on the title bar |
| **Minimize window** | Tap the minimize button on the title bar |
| **Bring to front** | Tap any window's title bar |
| **Unsnap/Undock** | Drag a snapped window away from its edge |

### Workspace Management

1. Arrange your windows the way you like them
2. Tap **"Save Layout to Favorites"** on the Windows tab
3. Your layout is saved including exact positions of all windows
4. You can **Edit**, **Remove**, or **Promote** layouts from the Workspace Manager section
5. Workspace History automatically saves your recent layouts

---

## 📱 Navigating the App — Tab by Tab

| Tab | What It Does |
|---|---|
| **Windows** | Main dashboard: launch apps, see active windows, manage workspaces |
| **Customization** | Visual settings: theme, roundness, opacity, border width, tint overlay, pill shrink style, window shadows |
| **Safe Area** | Per-display dock/taskbar reservation and DPI adjustment |
| **Blacklist** | Toggle which apps should NOT get freeform overlay controls |
| **Settings** | System behavior: display toggles, launch preferences, snap sensitivity, and experimental features |

---

## 🏠 Recommended Launchers

FreeformShell works alongside your regular Android launcher. Here are the best launchers to pair with it for a desktop-like experience:

### 🥇 Best Overall: [Taskbar by farmerbb](https://play.google.com/store/apps/details?id=com.farmerbb.taskbar)

- Adds a desktop-style start menu and taskbar at the bottom of the screen
- Specifically designed for freeform mode on Android
- Free and open-source
- FreeformShell has built-in integration for Taskbar (Launcher & Dock Avoidance detects it automatically)
- **Setup tip:** In FreeformShell's Safe Area tab, set Dock Position to "Bottom" and set the Dock Size to match Taskbar's height (usually ~48-56px × density). This prevents freeform windows from overlapping the taskbar.

### 🥈 Great Alternative: [Nova Launcher](https://play.google.com/store/apps/details?id=com.teslacoilsw.launcher)

- Extremely customizable home screen
- Works well alongside FreeformShell as your home screen
- Supports gestures and can be configured to stay minimal

### 🥉 Also Good: Your Stock Launcher (Pixel Launcher, One UI Home, etc.)

- Works perfectly fine — FreeformShell doesn't require a specific launcher
- You just lose the desktop-style taskbar/start menu experience

### For External Displays: [Taskbar](https://play.google.com/store/apps/details?id=com.farmerbb.taskbar) (again)

- When using FreeformShell with an external monitor, Taskbar is the only reliable way to get a persistent taskbar + app launcher on the secondary screen
- FreeformShell's "Launcher & Dock Avoidance" feature can be bound to Taskbar to automatically dim title bars when the start menu is open

---

## 🔒 Settings Safety Guide — What NOT to Touch

Some settings in the experimental section can cause unexpected behavior if you don't understand what they do. Here's a clear breakdown:

### ✅ Safe to Change (Customize Freely)

| Setting | Location | What It Does |
|---|---|---|
| Theme Mode (Auto/Light/Dark) | Customization / Settings | Changes the app and overlay color scheme |
| Window Roundness | Customization | Adjusts corner radius of window borders |
| Window Opacity | Customization | How see-through the window frame border is |
| Title Bar Opacity | Customization | How transparent the title bar is normally |
| Border Width | Customization | Thickness of the decorative window border |
| Drag & Resize Tint | Customization | Colored overlay during gestures |
| Pill Auto-Shrink & Scale | Customization | Whether inactive title pills shrink, and by how much |
| Pill Shrink Style | Customization | Scale transform vs handle/bar resizing animation |
| Safe Area / Dock Position | Safe Area | Reserve space for a taskbar — just set it to match your taskbar's position |
| Blacklist | Blacklist | Which apps don't get overlays — safe to toggle freely |
| Workspace Auto-Snap | Settings | Whether saved workspaces auto-position apps on launch |
| Launcher & Dock Avoidance | Settings | Dims overlays when launcher is open — fully safe |
| Active Shell Displays | Settings | Which screens get overlays — safe to toggle |
| Show Visual Corner Handles | Settings | Visual-only toggle — doesn't affect functionality |
| Tiled Layout Swap | Settings | Swap snapped window positions — safe |

### ⚠️ Be Careful (Understand Before Changing)

| Setting | Location | Risk Level | What Could Go Wrong |
|---|---|---|---|
| **Snapping Sensitivity** | Settings | 🟡 Medium | Too high = accidental snaps all the time. Too low = hard to snap at all. Stick with 80-120 for most devices. |
| **Pill Mode for Snapped** | Settings → Experimental | 🟡 Medium | Changes how snapped window controls look. Can make controls harder to find if you're not expecting the floating pill style. |
| **Paired Group Resizing** | Settings → Experimental | 🟡 Medium | If two windows are incorrectly detected as "paired," they might resize together unexpectedly. Disable per-display if this happens. |
### 🛑 Don't Touch Unless You Know What You're Doing

| Setting | Location | Risk Level | Why It's Dangerous |
|---|---|---|---|
| **Display Density (DPI) — especially on phone display** | Safe Area | 🔴 High | Changing DPI affects **ALL apps on that display**, not just FreeformShell. On your phone's primary display, setting this too low makes text and buttons microscopic and potentially unusable. Setting it too high can make the UI overflow off-screen. If you mess this up, you may not even be able to navigate back to reset it. Only change DPI on **external/secondary displays** where desktop-mode density makes sense. If things go wrong, use the "Reset to Default" button — or run `wm density reset` via ADB. |
| **Tablet UI Mode** | Settings → Experimental | 🔴 High | Forces desktop-style layouts on apps. Many apps don't support this and will crash, display incorrectly, or show broken UIs. Only enable if you specifically know an app supports tablet mode. |
| **Real-time Resize** | Settings → Experimental | 🔴 High | Makes windows resize live while dragging. On mid-range or lower phones, this will cause **severe lag, frame drops, and overheating**. Only enable on flagship devices with powerful processors. |
| **Instant Shell Resizing** | Settings → Experimental | 🔴 High | **Disables ALL system animations globally** (not just FreeformShell). This means no app open/close animations, no transition effects, nothing — across your entire phone. The effect persists even after uninstalling FreeformShell. You'd need to manually re-enable animations in Developer Options. |
| **Bubble Minimize** | Settings → Experimental | 🔴 High | Marked as "Currently non-functional (Under development)." Turning this on won't do anything useful and may cause unpredictable minimize behavior. |

---

## ⚡ Known Limitations

1. **Android 12 Hover Bug:** There's a framework bug in Android 12 where hover events crash Jetpack Compose. FreeformShell includes a global crash interceptor that silently catches these crashes, so you shouldn't be affected — but if you see brief UI glitches on Android 12, this is why.

2. **OEM ROMs:** Some heavily customized ROMs (MIUI, ColorOS, etc.) may block Shizuku's Binder calls even with proper permissions. If direct calls fail, the app falls back to shell commands automatically, but performance may be slightly lower.

3. **Bubble Minimize:** This feature is listed in the UI but is not functional yet. The toggle exists but does nothing useful.

4. **Window Shadows:** The "Window Shadows" experimental option in the Customization tab adds depth with soft shadows but has a noticeable performance cost. Disable it if you notice lag.

---

> **Version:** Experimental Beta  
> **Branch:** `experimental-beta`  
> **Based on:** v1.1 stable  
> **Compatibility:** Android 11 (API 30) through Android 15+ (API 35+)
