# 💠 TizenTube Cobalt

<p align="center">
    <img width="700px" src=".github/assets/TizenTube_Cobalt-Official_Banner.png">
    <br>
</p>

**TizenTube Cobalt** is an app based on [Cobalt](https://cobalt.dev) that enhances your favourite streaming website viewing experience by removing ads, adding [SponsorBlock](https://sponsor.ajay.app/) support, and providing useful features like video speed control.

<details>
<summary><strong>What is Cobalt?</strong></summary>

Cobalt is a lightweight, cross-platform application container and runtime for HTML5-based apps, originally developed by Google for embedded and resource-constrained devices (like smart TVs, set-top boxes, and game consoles). It implements a subset of the W3C HTML5 standard and runs web apps with high performance on a wide range of hardware.

</details>

## ✨ Features

- 🛑 **Ad Blocker**: Enjoy your favourite streaming website without interruptions from ads.
- ❗ **SponsorBlock Support**: Automatically skip sponsored segments in videos.
- ⏭️ **Video Speed Control**: Adjust playback speed to your preference.
- 🔺 **[DeArrow](https://dearrow.ajay.app/) Support**: Remove clickbait and misleading video titles.
- 🎮 **Handheld Support**: Fits phones and handhelds (tested on the AYN Thor's 1920x1080 top screen), with touch and gamepad input.
- ➕ **More to come!** Request features via [issues](https://github.com/reisxd/TizenTube/issues/new).

## 🎮 Handhelds and Phones

The app's UI is authored for a 16:9 television. On a handheld it adapts automatically — televisions
are detected and keep their existing behaviour, so nothing below changes how the app runs on a TV.

- **Display**: the window is locked to landscape, runs in sticky immersive fullscreen, and draws
  into the display cutout, so system bars and notches no longer eat into the UI. The device scale
  factor is derived from the panel instead of being pinned to 1, mapping the display's short edge
  onto the UI's authored 720px height — a 1920x1080 handheld gets a 1280x720 layout at full size
  rather than a TV layout shrunk to a third of its intended size.
- **Touch**: tapping activates whatever is under your finger, dragging moves the focus like a D-pad
  (one step per ~48dp of travel), and a two-finger tap goes back.
- **Controllers**: A selects, B goes back, X and Start play/pause, Y and Select open the menu, the
  shoulder buttons scrub, and the analog sticks act as a D-pad with auto-repeat.

Both input behaviours can be overridden with activity meta-data in the manifest:
`cobalt.TOUCH_NAVIGATION` (`HYBRID`, `DPAD`, `NATIVE` or `OFF`) and `cobalt.GAMEPAD_AS_REMOTE`
(`true` or `false`).

## ⬇️ Download

Get the latest release for your platform:

[**Download Latest Release**](https://github.com/reisxd/TizenTubeCobalt/releases/latest)

AFTVNews code: `6366500`

For a better experience, preferably use TizenTube Cobalt on a [**Google TV certified device.**](https://www.androidtv-guide.com/)

## ❔ How to Install

1. Download the latest release from the link above.
2. Sideload or install the app on your device (using a file manager, ADB, or platform-specific method).
3. Open the app and enjoy an enhanced streaming experience!

## ℹ️ Community & Support

- [Discord Server](https://discord.gg/m2P7v8Y2qR)
- [Telegram Channel](https://t.me/tizentubecobaltofficial)
- [Matrix Space](https://matrix.to/#/!BLE5ubNYktI30e8K0j:matrix.6513006.xyz)
- [Report Issues / Request Features](https://github.com/reisxd/TizenTube/issues)
