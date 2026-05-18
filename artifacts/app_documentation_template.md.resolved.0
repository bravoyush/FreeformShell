# 📱 [Your App Name] App Documentation

> Brief, one-liner tagline about what your app does (e.g., "FreeformShell is a premium, lightweight desktop window controller for Android, powered by Shizuku.")

---

## 🚀 Getting Started

This section will guide you through setting up **[Your App Name]** for the first time.

### 📋 Prerequisites

Before installing, ensure your environment meets these requirements:

* **Android Version**: Android 10 (API 29) or higher (Android 13+ recommended for advanced display scaling).
* **High-Privilege Shell Provider**: [Shizuku](https://shizuku.rikka.app/) installed and running.
* **System Settings**: "Enable freeform windows" and "Force activities to be resizable" toggled on in developer options.

> [!IMPORTANT]
> **Shizuku** must be actively running in the background before starting the foreground service. Without Shizuku permissions, window resizing and display density operations will fail silently.

### 🛠️ Step-by-Step Setup

Follow these steps to initialize the environment:

1. **Activate Shizuku**: Start the Shizuku manager application and pair it via Wireless Debugging or ADB.
2. **Authorize [Your App Name]**: Open [Your App Name], click the **Request Shizuku Permission** prompt, and grant authorization when prompted by the system dialog.
3. **Grant Draw-Over-Apps Permission**: Toggle the system overlay permission to allow drawing window borders and handles.

![Setup Process Illustration](/absolute/path/to/artifacts/setup_screenshot.png)
*Figure 1: Visual guide highlighting the Shizuku pairing and overlay authorization steps.*

---

## ✨ Core Features

Here is a deep-dive into the primary capabilities of **[Your App Name]**.

### 1. Advanced Window Snapping & Quadrants
Simply drag any floating window to screen edges to snap them. 

* **Maximize/Restore**: Drag to the top edge or double-tap the title bar.
* **Vertical Splits**: Drag to the left or right edges to snap to halves.
* **Corner Snapping**: Snap to any of the four quadrants of the screen for comprehensive multitasking.

#### Visual Walkthrough of Snap States:
````carousel
![Snapping Left Split](/absolute/path/to/artifacts/snap_left.png)
<!-- slide -->
![Symmetric Resize Splitter](/absolute/path/to/artifacts/split_resize.png)
<!-- slide -->
![4-Quadrant Layout](/absolute/path/to/artifacts/quadrant_snap.png)
````

### 2. Live Dynamic Color Branding
Window frames automatically analyze the active app's launcher icon to color-match borders and titles.

* **Focused Window**: Vibrant, rich primary app theme.
* **Unfocused Window**: Neutral theme-appropriate color blended with system surfaces (90% surface mix) to avoid distracting focus.

---

## 🛠️ Configuration Settings

Configure these options inside the App Settings dashboard to adapt layouts to your needs:

| Option Category | Setting Name | Recommended Configuration | Description |
| :--- | :--- | :--- | :--- |
| **Window Frame** | Window Roundness | `16dp` | Adjusts overlay corner rounding radius. |
| **Window Frame** | Border Width | `4dp` | Configures thickness of interactive borders. |
| **Workspace** | Auto-Snap | `Enabled` | Automatically snaps launcher-saved bounds on boot. |
| **Window Frame** | Pill Mode | `Enabled` | Hides titlebars on docked apps, replacing with sleek center pills. |
| **Performance** | Real-time Resize | `Disabled (lower-end)` / `Enabled (high-end)` | Debounced active resize commands vs. visual outline guides. |

---

## 🔍 Troubleshooting & FAQs

Find solutions to common issues below.

### ❓ Shizuku Connection Lost
> [!WARNING]
> If Shizuku loses connection (e.g., due to device reboot or background service death), window borders will turn gray and interactive resizing will freeze.

* **Solution**: Re-open the Shizuku Manager app and click **Start**. Ensure Wireless Debugging is enabled under Developer Options if starting wirelessly.

### ❓ Window Resizing Fails or Glitches
Some applications are hard-coded to reject resize configurations.

1. Go to **Developer Options** in System Settings.
2. Scroll to the bottom and ensure **Force activities to be resizable** is toggled **ON**.
3. Reboot your device to apply the system change.

### ❓ Overlays Not Showing Above Specific Apps
Some system apps (e.g., keyboard panels, security prompts) block third-party overlays.

> [!TIP]
> Use the **App Blacklist** tab in the dashboard to disable overlays for specific packages to prevent conflict or keyboard focus dropouts.

---

*Need more help? Join our developers on [GitHub](https://github.com/yourprofile/yourapp) or create an issue report.*
