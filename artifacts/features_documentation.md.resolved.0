# FreeformShell: Technical Features & ADB Command Documentation

Welcome to the official technical documentation for **FreeformShell**—a lightweight, high-performance desktop-style window controller for Android. 

FreeformShell enables a premium multitasking desktop environment on Android devices (phones and external displays) by leveraging high-privilege system controls via **Shizuku** and **Android Shell (ADB) command structures**, all without requiring root permissions.

---

## 🏛️ System Architecture Overview

The application is built on a highly optimized, decoupled design to ensure maximum battery efficiency, fluid visual responses, and premium aesthetics:

1. **Dashboard & Console (Jetpack Compose)**: A beautiful dashboard for display management, workspace management, launching, blacklisting, and customized appearance settings.
2. **Foreground Window Monitor Service (`FreeformOverlayService`)**: A persistent lightweight background service that tracks system task states using speed-optimized shell lookups.
3. **Decoupled 5-Window Overlay Controller (`DragResizeOverlay`)**: A lightweight window controller built with native Android Views (avoiding heavy Jetpack Compose overlay overhead). It uses `WindowManager` with standard drawing masks, interactive resize borders, and fluid drag handles.
4. **Shizuku Shell Engine (`ShellExecutor`)**: Programmatically executes low-level shell commands to control system windows.

---

## 🚀 Core Features & Window Controls

### 1. Advanced Window Snapping & Quadrants
Dragging a window title bar near any screen edge triggers a semi-transparent visual snap guide. Releasing the window snaps it instantly to predefined screen regions:
* **Fullscreen / Maximize**: Toggles a window between its previous floating bounds and the display's safe boundary.
* **Left & Right Split**: Splits the display vertically, snapping two applications side-by-side.
* **Top & Bottom Stack**: Splits the display horizontally, stacking two applications.
* **4-Quadrant Snapping (Corner Snapping)**: Allows snapping windows to the Top-Left, Top-Right, Bottom-Left, or Bottom-Right quarters of the screen.

### 2. Multi-Display & Safe Area Dock Margins
Designed to support primary displays and external monitors (desktop mode):
* **Safe Area Reservations**: Users can define a safe boundary (None, Top, Bottom, Left, or Right) and specify its size in pixels. This leaves space for custom taskbars, navigation docks, or system panels.
* **Status Bar Clamping**: Windows are restricted from sliding underneath the system status bar or notch, protecting active window buttons from becoming unreachable.
* **Launch Screen Syncing**: If the main dashboard is opened on an external monitor, it automatically restarts itself on the configured display.

### 3. Dynamic App-Themed Branding
To create a premium aesthetic, FreeformShell features content-aware coloring:
* **Icon Color Extraction**: Automatically extracts the major dominant/vibrant color from an application's launcher icon using **Palette API** analysis.
* **Color Normalization**: Normalizes color saturation and lightness in HSL space, preventing pure white/near-white or muddy colors from reducing text legibility.
* **Unfocused Dimming & Blending**: Active/focused window frames and title bars display the app's vibrant branding. Unfocused windows blend the color with the dark/light theme (90% surface mix) to remain clean and unobtrusive.

### 4. Interactive Split Snapping & Splitter Handles
When two docked apps are snapped flush against each other, FreeformShell bridges them:
* **Symmetric Resize splitters**: Draws a sleek, interactive splitter handle capsule in the contact gap.
* **Simultaneous Resizing**: Dragging the splitter handle resizes *both* adjacent applications symmetrically in real-time, preventing gaps or overlaps.
* **Dock Propagation**: Snapping a window next to an already docked window automatically snaps it flush to fit the remaining split screen.

### 5. Real-Time Resizing vs. Smooth Bounds Preview
Users can choose their preferred resizing behavior:
* **Real-time Resize (Performance Mode)**: Continually resizing the Android task as you drag the overlay border ( debounced to 150ms).
* **Lag-free Outline Preview (Smooth Mode)**: Renders a smooth, semi-transparent boundary outline as you drag the window border. The underlying Android task is only resized once via shell on touch release (`UP` event), achieving 60fps on lower-end devices.

### 6. Title Pill Mode for Snapped Windows
To maximize visible screen estate when windows are split or maximized:
* **Capsule Conversion**: Hides the full-width horizontal title bar and converts it into a small floating "pill" centered at the top.
* **Pill Auto-Size**: Content-aware calculation adjusts the pill width depending on the icon and text length (clamped to 40% of window width on desktops, 90% on phones).
* **Hover Interaction**: Hovering a mouse pointer or touching the pill temporarily expands it back to a full title bar.
* **Pill Auto-Shrink & Scale (Experimental)**: Unfocused pills automatically scale down (30% to 90% range) and dim to 40% opacity after 3 seconds of inactivity to minimize distraction.

### 7. Modern Window Minimize (Bubble & Side-Dock Mode)
Two options are provided when minimizing a window:
* **Side-Dock Mode**: Scales and docks the window to the far right side of the screen (260px width, 340px height) as a mini-view.
* **Bubble Minimize Mode (Experimental)**: Minimizes the application completely to the background (moving task to back) and shows a floating interactive circular bubble icon representing the app, allowing quick restore on tap.

### 8. Dynamic Clipping & Occlusion Masking
To prevent overlay elements of overlapping windows from leaking into each other:
* **Z-Order Clipping**: Unfocused window borders are dynamically masked and clipped where a top-most foreground window overlaps them.
* **Clipping Implementation**: Uses `canvas.clipOutPath` (on API 26+) or `canvas.clipOutRect` to mask out occluding rectangles, maintaining pixel-perfect window layering.

### 9. Workspace Layout Manager & Widgets
* **Layout Capture**: Capture all active freeform window locations and bounds on any display.
* **Workspace History**: Stores the last 8 unique active window configurations, sorted by recents with time indicators.
* **Layout Editor**: Allows detailed editing of each coordinate (left, top, right, bottom) of apps in a workspace, deleting individual apps, or assigning custom configurations.
* **Favorites Snap**: Save a favorite workspace layout to the dashboard.
* **App Widget**: Place a scrollable launcher widget on the home screen containing Favorite layouts and History, featuring quick-restore buttons for Phone or External monitor, and a "Force Rescue" trigger.

---

## 🛠️ Complete Android ADB/Shell Command Reference

The core power of **FreeformShell** is driven by executing high-privilege shell commands using Shizuku's binder system. Below is a comprehensive guide to every command used:

| Feature / Objective | ADB Shell Command Line | Code Invocation |
| :--- | :--- | :--- |
| **Enable App Resizing** | `cmd activity set-resizable 1` | Force-enables resizable configurations across the system (needed for older apps). |
| **Resize Task** | `cmd activity task resize <taskId> <left> <top> <right> <bottom>` | Instantly resizes and positions any active task to specified coordinates. |
| **Bring Task to Front** | `cmd activity task movetotop <taskId>` <br> *AND* <br> `am start-activity --task <taskId>` | Focuses and brings an active background task to the absolute foreground. |
| **Minimize App** | `cmd activity task move-to-back <taskId>` | Moves an active task to the background (used in Bubble minimize). |
| **Force Relaunch Freeform** | `am start-activity --task <taskId> --windowingMode 5 -n <component> --activity-brought-to-front` | Relauches a background task forcing Android's native freeform window mode (`windowingMode 5`). |
| **Launch Freeform (Target Display)**| `am start-activity --display <displayId> --windowingMode 5 --activity-brought-to-front -n <component>` | Launches a fresh app instance directly in freeform mode on a target display (e.g., external monitor). |
| **Reorder and Force Freeform** | `am start -n <activity> --windowingMode 5 --activity-reorder-to-front` | Reorders an existing task to the front and forces freeform configuration. |
| **Force Stop App** | `am force-stop <packageName>` | Force kills the target package. |
| **Remove Task** | `cmd activity task remove <taskId>` | Removes the target task from the Android Recents/Task history. |
| **Dump Display Configs** | `dumpsys display` | Queries physical/virtual display listings (names, IDs, real dimensions, DPIs). |
| **Change Display Density (DPI)** | `wm density <densityVal> -d <displayId>` | Alters the display density/scaling of a target monitor to enable desktop scaling. |
| **Reset Display DPI** | `wm density reset -d <displayId>` | Restores a target monitor back to its physical/default DPI scaling. |
| **Monitor Task States** | `dumpsys activity activities \| grep -E "Task\|bounds\|mBounds\|mode\|windowingMode\|visible\|mVisible\|mResumed\|state=RESUMED\|Display\|displayId\|ACTIVITY\|ActivityRecord\|realActivity\|A=\|packageName"` | A speed-optimized dumpsys pipeline to parse visible freeform tasks, coordinates, active windowing modes, and focused displays. |

---

## ⚙️ Dashboard Preferences & Options Explained

Here is an analysis of all settings configurable inside `MainActivity` across its five tab sections:

### 📺 1. Windows (Dashboard)
* **Target Display**: Select which display is active for launch and snaps (displays name, size, shapes).
* **Launch Queue**: Queue multiple applications and launch them collectively. Applications cascade downward with cascading bounds offsets (80px x 80px).
* **Active Windows**: Real-time listing of active freeform tasks. Provides "Close" triggers (force kill + task remove) and toggles for app overlays.
* **Workspace Manager**: Save, delete, edit, or set favorite workspace templates. Allows adjusting window boundary coordinates.

### 🎨 2. Theme (Customization)
* **Theme Mode**: Toggles dashboard between **Light**, **Dark**, or **System Auto** themes.
* **Window Roundness**: Configures corner rounding radii (0dp to 40dp) on overlay frames and title bars.
* **Window Opacity**: Slider (50 to 255) to customize transparency/alpha level of floating window borders.
* **Border Width**: Configures the thickness of window borders (0dp to 12dp).
* **Window Shadows (Experimental)**: Toggles high-elevation hardware-accelerated shadows on floating windows (adds visual depth but increases GPU overhead).

### 🛡️ 3. Safe Area
* **Safe Area Dock**: Reserve spaces (None, Top, Bottom, Left, or Right) for docks/panels. Setting dock sizes shows a semi-transparent blue guide overlay on the target display.
* **Display Density (DPI)**: Toggles display density scaling (160 to 600 DPI) per display. Includes "Reset to Default" shortcut.

### 🚫 4. Blacklist
* **App Blacklist**: A complete search list of all installed packages. Users can toggle a switch to blacklist any application. Blacklisted apps will not have overlay boundaries attached, preventing issues with system launchers, launchers, or overlays.

### ⚙️ 5. App Settings
* **App Launch Display**: Direct app to launch on **Phone**, **Secondary Display**, or **Automatic**.
* **Workspace Auto-Snap**: Toggles if workspaces auto-snap applications to saved bounds on launch, or just open them in normal freeform mode.
* **Tablet UI Mode (Experimental)**: Adds a "Tablet UI" snap menu option, forcing the snapped window to exactly 600dp wide (Android's natural responsive breakpoint to trigger rich tablet UI/desktop views in browsers/apps).
* **Pill Mode for Snapped**: Floating pill mode for split/maximized windows to maximize screen estate.
* **Real-time Resize**: Toggles real-time ADB task resizing versus bounds preview guide.
* **Bubble Minimize**: Toggles between bubble-minimize state or docked state.
* **Pill Auto-Shrink & Scale (Global / Per-display)**: Toggles automatic scaling down (30% to 90% slider) and dimming of inactive/unfocused window title pills.

---

> [!NOTE]
> FreeformShell's use of **Shizuku** allows executing these high-privilege window adjustments without root permissions. It connects directly with Android's underlying Window Manager service, delivering a native-feeling, fully-featured desktop experience.
