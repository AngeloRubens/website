---
title: "Lottie4J 1.2.7: A Performance Release"
date: "2026-10-18"
description: "Lottie4J 1.2.7 focuses on performance: image assets are decoded once and cached, track mattes reuse their buffers between frames, and GC pauses drop by two thirds."
canonical: "https://webtechie.be/post/lottie4j-1.2.7-performance-release/"
authors:
  - "frankdelporte"
image: "manual-comparison.png"
categories:
  - "JavaFX"
related_posts:
  - "lottie4j-1-2-0-dotlottie-support-marker-playback-cropping-and-a-big-speed-boost"
  - "closing-the-visual-gap-between-the-official-lottie-webplayer-and-lottie4j"
  - "lottie4j-meets-lottiefiles"
  - "testing-lottie4j-javafx-animations-in-github-actions-with-javafx-26-headless"
---

A new version of [Lottie4J](https://lottie4j.com/), the Java and JavaFX library to render Lottie animations, is available. [Release 1.2.7](https://lottie4j.com/status/2026/20261014-release-1.2.7/) doesn't bring new features, but focuses fully on performance.

## Why a Performance Release?

While profiling [MelodyMatrix](https://melodymatrix.rocks/) a JavaFX application to "look at music", that uses an animated logo, a few hot spots showed up that had nothing to do with the application itself, but everything with how Lottie4J was rendering each frame. Images were decoded over and over again, and track mattes were allocating new canvases and buffers for every single frame. For a small logo animation that runs continuously, that adds up quickly.

## What Changed

### Image Assets Are Decoded Once

Image assets are now decoded only once per `LottiePlayer` instance. The decoded images are cached and preloaded when the animation is loaded, so no decoding happens anymore while the animation is playing. Assets that fail to load are cached too, which avoids the same warning being logged on every frame.

### Optimized Track Mattes

The off-screen canvases, snapshot images, and pixel buffers used for track mattes are now reused between frames instead of being recreated. On top of that, some pixel sampling that was only used for debug logging has been removed.

## The Results

The profiling data clearly shows the difference:

* **GC pauses** dropped from 531 to 177 within the same 22-second run.
* **PNG decoding** disappeared from the list of hot methods.
* **Matte compositing allocations** went from 30% of all allocations to nearly zero.

## Other Fixes

* The file viewer no longer leaks players and bindings when loading files.
* Shutting down the WebView comparison view is now non-blocking.
* A new profiling harness was added in the `tools/profiling` directory of the repository, so future performance work can be measured in the same way.

## Upgrade

If you're using Lottie4J in your application, upgrading to 1.2.7 is recommended, as you get these improvements without any code changes. All changes can be found in the [comparison between 1.2.6 and 1.2.7](https://github.com/lottie4j/lottie4j/compare/v1.2.6...v1.2.7) on GitHub, and in the [release notes on lottie4j.com](https://lottie4j.com/status/2026/20261014-release-1.2.7/).
