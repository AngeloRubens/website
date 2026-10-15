---
title: "Pi4J V5.0.0: Work in Progress"
date: "2026-10-17"
description: "What is changing towards Pi4J V5.0.0: a big API cleanup, a simpler Context, parallel ports, RISC-V support, better board detection, and a new release flow."
canonical: "https://www.pi4j.com/blog/2026/20261014-pi4j-v5-work-in-progress/"
authors:
  - "frankdelporte"
image: "pi4j-v5.jpg"
categories:
  - "Pi4J"
  - "Raspberry Pi"
  - "Embedded"
related_posts:
  - "the-ffm-api-how-openjdk-changed-the-game-for-native-interactions-and-made-pi4j-better"
  - "first-test-of-java-on-banana-pi-arm-and-risc-v-plus-a-blinking-led-with-pi4j"
  - "pi4j-drivers-simplifying-sensor-and-hardware-integration-in-java"
  - "bringing-raspberry-pi-development-to-quarkus-with-the-quarkus-pi4j-extension"
---

## What is Pi4J?

[Pi4J](https://www.pi4j.com/) is an open-source Java library that gives your Java application access to the hardware pins of a Raspberry Pi and other single-board computers. With it, you can blink an LED, read a button, or talk to sensors, displays, and motor controllers over GPIO, PWM, I2C, SPI, and serial, all from plain Java code and without writing any C. The project started more than ten years ago, is now part of the [Commonhaus Foundation](https://www.pi4j.com/blog/2026/20260227-pi4j-commonhaus/), and is maintained by a small team of volunteers.

Since V2, Pi4J uses a plugin architecture, and with the [FFM API](/today/the-ffm-api-how-openjdk-changed-the-game-for-native-interactions-and-made-pi4j-better/) (Foreign Function & Memory, final since Java 22), the FFM plugin talks directly to the Linux kernel without the native C libraries of earlier versions. That change opened the door to other boards and architectures, like [RISC-V](/today/first-test-of-java-on-banana-pi-arm-and-risc-v-plus-a-blinking-led-with-pi4j/), and to the cleanup described below. On top of the core library, [Pi4J Drivers](/today/pi4j-drivers-simplifying-sensor-and-hardware-integration-in-java/) provides ready-to-use code for common sensors and components, and there is even a [Quarkus extension](/today/bringing-raspberry-pi-development-to-quarkus-with-the-quarkus-pi4j-extension/).

This post was first published on the [Pi4J website](https://www.pi4j.com/blog/2026/20261014-pi4j-v5-work-in-progress/) and gives an overview of what is coming in the next major version.

## Towards V5.0.0

Since the [4.0.2 release](https://github.com/Pi4J/pi4j/releases/tag/4.0.2) in June '26, the `main` branch of the [Pi4J core repository](https://github.com/Pi4J/pi4j) moved to **5.0.0-SNAPSHOT**, and a lot has happened since then: about 300 commits and more than 80 merged pull requests! This post gives an overview of what is changing and who is making it happen.

**There is no release date for V5.0.0 yet.** Several [issues](https://github.com/Pi4J/pi4j/issues) and [pull requests](https://github.com/Pi4J/pi4j/pulls) are still in progress, and we want to get the API right before releasing, as V5 is the moment to clean up things we couldn't change in a minor release. If you want to try it out, the [snapshot builds](https://www.pi4j.com/about/download/) are published automatically for every change on `main`.

## A much leaner core

The biggest theme of V5 is cleanup. Pi4J has grown over many years and a lot of code was no longer used, duplicated, or made things more complicated than needed. A selection of the changes:

- **Platforms are removed** ([#650](https://github.com/Pi4J/pi4j/pull/650)), together with the platform autodetect option ([#688](https://github.com/Pi4J/pi4j/pull/688)).
- **Providers are removed** ([#782](https://github.com/Pi4J/pi4j/pull/782)), turning a plugin into a factory for a `Context`. Minimal stubs remain for the old, prominently documented ways to create IO instances ([#790](https://github.com/Pi4J/pi4j/pull/790)).
- **Properties and name-based IO creation are removed** ([#652](https://github.com/Pi4J/pi4j/pull/652), [#674](https://github.com/Pi4J/pi4j/pull/674)), as well as the binding and group stubs ([#653](https://github.com/Pi4J/pi4j/pull/653)), PWM presets ([#693](https://github.com/Pi4J/pi4j/pull/693)), the deprecated `DeferredDelay` ([#640](https://github.com/Pi4J/pi4j/pull/640)), and other unused metadata and types ([#706](https://github.com/Pi4J/pi4j/pull/706), [#792](https://github.com/Pi4J/pi4j/pull/792)).
- **The old Raspberry Pi plugin implementation and the never-used analog GPIO implementation are removed** ([#664](https://github.com/Pi4J/pi4j/pull/664), [#665](https://github.com/Pi4J/pi4j/pull/665)). The [FFM plugin](https://www.pi4j.com/documentation/providers/ffm/) is the way forward.
- **The internal runtime is merged into `DefaultContext`** ([#660](https://github.com/Pi4J/pi4j/pull/660)), and write access to the IO registry is no longer exposed ([#658](https://github.com/Pi4J/pi4j/pull/658)).
- **Config classes no longer depend on the context** ([#711](https://github.com/Pi4J/pi4j/pull/711) and others), bringing them closer to plain DTOs, and IO instances are getting rid of their `Context` dependency ([#769](https://github.com/Pi4J/pi4j/pull/769), [#791](https://github.com/Pi4J/pi4j/pull/791)).
- **Intermediate base classes are removed**, like `GpioBase` and the provider base classes ([#776](https://github.com/Pi4J/pi4j/pull/776), [#778](https://github.com/Pi4J/pi4j/pull/778)).

## One clear way to create IO instances

Over the years, many different ways to create an IO instance were added. V5 settles on one canonical approach ([#704](https://github.com/Pi4J/pi4j/issues/704), [#728](https://github.com/Pi4J/pi4j/pull/728)): use `<IOTYPE>.newConfigBuilder()` and pass the config to `context.create(...)`. Other creation paths are deprecated (with the version in the annotation) or marked as internal, and the remaining internal calls to them have been removed ([#733](https://github.com/Pi4J/pi4j/pull/733), [#735](https://github.com/Pi4J/pi4j/pull/735), [#738](https://github.com/Pi4J/pi4j/pull/738)). Devices are now registered while they are created ([#734](https://github.com/Pi4J/pi4j/pull/734)).

Shutting down is simplified as well: the confusing combination of `close()` and `shutdownInternal()` is replaced by a single `close()` method ([#768](https://github.com/Pi4J/pi4j/pull/768)), with several fixes for close ordering and robustness ([#677](https://github.com/Pi4J/pi4j/pull/677), [#678](https://github.com/Pi4J/pi4j/pull/678), [#755](https://github.com/Pi4J/pi4j/pull/755), [#780](https://github.com/Pi4J/pi4j/pull/780)).

## Falling back to mocks

A long-standing request ([#619](https://github.com/Pi4J/pi4j/issues/619)) is now possible: run on real hardware when it's available, and automatically fall back to mocks on other systems, for instance on your development PC ([#715](https://github.com/Pi4J/pi4j/pull/715)). Mock providers now have a lower priority, so they no longer take precedence over real providers. The mock drivers themselves were also reworked into a proper test framework, including a GPIO mock driver ([#651](https://github.com/Pi4J/pi4j/pull/651)).

## New and improved IO features

- **Parallel port**: a new `ParallelPort` IO type to read or write multiple GPIOs simultaneously as one value, with direction control at runtime ([#727](https://github.com/Pi4J/pi4j/pull/727), [#740](https://github.com/Pi4J/pi4j/pull/740), [#765](https://github.com/Pi4J/pi4j/pull/765), [#785](https://github.com/Pi4J/pi4j/pull/785)). Event support is in progress in [#788](https://github.com/Pi4J/pi4j/pull/788).
- **PWM**: frequency and duty cycle are now `double` values, so you can use 1.5 Hz or a 33.3% duty cycle ([#644](https://github.com/Pi4J/pi4j/pull/644), [#646](https://github.com/Pi4J/pi4j/pull/646)), a refactored and optimized FFM PWM implementation ([#701](https://github.com/Pi4J/pi4j/pull/701)), and improved documentation ([#759](https://github.com/Pi4J/pi4j/pull/759)).
- **I2C**: implementations now only need to provide `writeThenRead`, as defaults for the other methods are built on top of it ([#744](https://github.com/Pi4J/pi4j/pull/744), [#746](https://github.com/Pi4J/pi4j/pull/746)). `read()` now returns unsigned values ([#753](https://github.com/Pi4J/pi4j/pull/753)), and the limited `FILE` implementation is removed ([#762](https://github.com/Pi4J/pi4j/pull/762)).
- **SPI**: support for chunked transfers ([#666](https://github.com/Pi4J/pi4j/pull/666)).
- **Digital output**: an initial state in the FFM implementation ([#663](https://github.com/Pi4J/pi4j/pull/663)), and `blink`, `pulse`, etc. moved to a `DigitalOutputOperations` helper ([#749](https://github.com/Pi4J/pi4j/pull/749)).
- **GPIO**: the FFM GPIO code is refactored to reduce duplication ([#698](https://github.com/Pi4J/pi4j/pull/698)).

## More boards: RISC-V and better detection

- **RISC-V support** ([#697](https://github.com/Pi4J/pi4j/pull/697)): the FFM provider now asks the OS linker to find native libraries instead of using hardcoded paths per architecture. Read more in [RISC-V Support Landing in Pi4J V5](https://www.pi4j.com/blog/2026/20260720-riscv-support-pi4j-v5/).
- **A `LineHelper` for non-Raspberry Pi boards** ([#694](https://github.com/Pi4J/pi4j/pull/694)), following the experiments on [Banana Pi](https://www.pi4j.com/blog/2026/20260717-java-on-banana-pi-arm-riscv/) and [NVIDIA Jetson Nano](https://www.pi4j.com/blog/2026/20260720-pi4j-nvidia-jetson-nano/).
- **Board detection with revision codes** ([#682](https://github.com/Pi4J/pi4j/pull/682)): Raspberry Pi revision codes are parsed into memory size, processor, and board type, as recommended by Raspberry Pi, so new revisions of existing boards are recognized without code changes. The 16GB Compute Module 5 is also supported now ([#673](https://github.com/Pi4J/pi4j/pull/673)).
- **Friendlier FFM startup**: each provider is registered separately, so a missing Linux group only disables the related provider, and the permission message suggests the correct `usermod -aG` command ([#757](https://github.com/Pi4J/pi4j/pull/757), [#758](https://github.com/Pi4J/pi4j/pull/758)). Newer kernel versions are handled in the builds and tests ([#662](https://github.com/Pi4J/pi4j/pull/662), [#760](https://github.com/Pi4J/pi4j/pull/760)).

## Build, release, and documentation

- **Java 25 and Maven 4** ([#612](https://github.com/Pi4J/pi4j/pull/612)).
- **Automated releases from `main` with JReleaser** ([#685](https://github.com/Pi4J/pi4j/pull/685)), aligned with the [Commonhaus](https://www.pi4j.com/blog/2026/20260227-pi4j-commonhaus/) release requirements and the same approach as [Pi4J Drivers](https://www.pi4j.com/drivers/), plus several fixes to make sure snapshots are actually published ([#770](https://github.com/Pi4J/pi4j/pull/770), [#771](https://github.com/Pi4J/pi4j/pull/771), [#772](https://github.com/Pi4J/pi4j/pull/772)).
- **The `module-info` files are replaced by an `Automatic-Module-Name`** ([#775](https://github.com/Pi4J/pi4j/pull/775)). Whether the FFM plugin should get a named module again is still being discussed in [#795](https://github.com/Pi4J/pi4j/issues/795).
- **Improved JavaDoc** for all public methods ([#667](https://github.com/Pi4J/pi4j/pull/667), [#668](https://github.com/Pi4J/pi4j/pull/668)).
- **Smoke test updates** for the [smoke test PCB](https://www.pi4j.com/blog/2026/20260625-pi4j-smoketest-pcb/) ([#676](https://github.com/Pi4J/pi4j/pull/676), [#783](https://github.com/Pi4J/pi4j/pull/783)).
- **An architecture map**: every pull request now gets a comment showing which components it changes, based on a [groma.md](https://github.com/Pi4J/pi4j/pull/786) architecture map of the project ([#786](https://github.com/Pi4J/pi4j/pull/786)).

## Still in progress

Before V5.0.0 can be released, a number of topics are still open, for example:

- Parallel port events ([#788](https://github.com/Pi4J/pi4j/pull/788), [#789](https://github.com/Pi4J/pi4j/issues/789)).
- Hardware detection with the FFM plugin ([#645](https://github.com/Pi4J/pi4j/pull/645), [#642](https://github.com/Pi4J/pi4j/issues/642)).
- Further config cleanup ([#794](https://github.com/Pi4J/pi4j/pull/794), [#705](https://github.com/Pi4J/pi4j/issues/705), [#714](https://github.com/Pi4J/pi4j/issues/714), [#731](https://github.com/Pi4J/pi4j/issues/731)) and removing the `Describable`/`Identity` complex ([#730](https://github.com/Pi4J/pi4j/issues/730)).
- Removing the remaining `Context` dependency from IO instances ([#764](https://github.com/Pi4J/pi4j/issues/764)).
- CRaC checkpoint/restore support for open IO ([#797](https://github.com/Pi4J/pi4j/issues/797), [#798](https://github.com/Pi4J/pi4j/pull/798)).
- 1-Wire support ([#429](https://github.com/Pi4J/pi4j/issues/429), [#435](https://github.com/Pi4J/pi4j/pull/435)), analog pins ([#699](https://github.com/Pi4J/pi4j/issues/699)), and software PWM in the FFM plugin ([#556](https://github.com/Pi4J/pi4j/issues/556)).
- A possible issue with FFM `DigitalInput` debounce ([#796](https://github.com/Pi4J/pi4j/issues/796)).

As V5 contains breaking API changes, we will also update the documentation and examples on the [Pi4J website](https://www.pi4j.com/) before the release. Feedback is very welcome! Try the snapshot with your project and let us know in the [issues](https://github.com/Pi4J/pi4j/issues) or [discussions](https://github.com/Pi4J/pi4j/discussions) what works and what breaks.

## Thank you

A huge thank you to everyone who contributed code, reviews, ideas, and testing to the upcoming V5:

- [Stefan Haustein (@stefanhaustein)](https://github.com/stefanhaustein), who drove most of the API cleanup.
- [@IAmNickNack](https://github.com/IAmNickNack), for the parallel port, the base class cleanups, and many fixes.
- [Nick Gritsenko (@DigitalSmile)](https://github.com/DigitalSmile), for RISC-V support, the test framework, and the GPIO, PWM, I2C, and SPI improvements in the FFM plugin.
- [Robert von Burg (@eitch)](https://github.com/eitch), for reviewing and merging, and the release preparations.
- [Tom Aarts (@taartspi)](https://github.com/taartspi), for the smoke tests and the I2C and close fixes.
- [Matti Tahvonen (@mstahv)](https://github.com/mstahv), for the FFM startup and I2C improvements.
- [Peter (@pjbosco)](https://github.com/pjbosco), for the revision code parsing and Compute Module 5 support.
- [Fanon Jupkwo (@FanJups)](https://github.com/FanJups) and [Tiza Talla (@kurocifer)](https://github.com/kurocifer), for the migration to Maven 4.
- [Matt Van Horn (@mvanhorn)](https://github.com/mvanhorn), for guarding the deploy workflows in forks.
- [Dariusz Zbyrad (@dariuszzbyrad)](https://github.com/dariuszzbyrad), for the ongoing work on 1-Wire support.
- [Frank Delporte (@FDelporte)](https://github.com/FDelporte), for the release automation, JavaDoc, and build work.

You can follow all the changes on GitHub in the [commit history](https://github.com/Pi4J/pi4j/compare/4.0.2...main).
