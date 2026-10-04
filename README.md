# Combined Launcher Animations

Animation runtime source and integration instructions, separate from the
[Launcher Phone OS patch kit](https://github.com/IvanChanPing/launcher-phone-os-combined).

## Status — 2026-10-04

Experimental. This is the existing implementation, not a newly repaired version.
The combined launcher compiled on 2026-10-03, but the user reported missing selected-icon
expansion and incorrect downward motion. Nova visual parity is unresolved. The user accepted
the iLauncher grid effect; that does not establish correctness of the other animation paths.
No APK, vendor decompile, signing key, or private credential is included.

## What is included

- `runtime/`: Android Java library, nine source classes, no third-party runtime dependencies.
- `integration/res/`: return-window resources and the gesture-surface layout.
- `docs/IMPLEMENTATION.md`: complete ordered flow, timing, coordinates, lifecycle hooks, and test matrix.
- `tools/bootstrap_gradle.py`: pinned Gradle download helper.

The code is NOT a standalone launcher or a generic one-line animation dependency. `LauncherAccess`
is the adapter for Launcher Phone OS 1.4.1. Its reflected classes, methods, model fields, artwork,
and container assumptions must be mapped before use in a different launcher.

## Use it in the supported launcher

Use the [launcher patch kit](https://github.com/IvanChanPing/launcher-phone-os-combined),
which contains the exact hook installer, source tests, input hash checks, and build/sign instructions.
Do not install this library as an app. The original base and both required splits are supplied
by the user and are not included here.

## Integrate into another source-built launcher

1. Copy `runtime/` into the host Gradle project, include `:runtime` in settings, and add
   `implementation(project(":runtime"))` to the host app. The included module uses SDK 36,
   Java 17 and minSdk 24. This repository pins AGP 8.10.1 and Gradle 8.11.1.
2. Adapt `LauncherAccess` to the host's actual launcher class, safe-launch method, root,
   binding-ready state, visible page/dock/folder, icon drawable, model and user-profile data.
   Preserve its coordinate mapping and read the ordered flow before inserting hooks.
3. Call `CombinedTransitionController.install(application)` after Application initialization.
   Forward creation, start, resume, focus, model-ready and configuration callbacks after the
   host's own body. Forward pause, stop and destroy at entry. For `onNewIntent`, call
   `recordHomeIntent(activity, intent)` at entry and `onLauncherNewIntent(activity)` after
   the host finishes processing the intent. See the controller's public callback methods.
4. At the host's safe launch gate, invoke `interceptLaunch(activity, sourceView, intent, item)`.
   A true result means the controller owns this launch. The existing safe launch route must
   remain callable by `LauncherAccess.replay`; do not replace it with a bare `startActivity`.
   The host options provider calls `consumeLaunchOptions(activity, sourceView)` and uses its
   non-null result; otherwise preserve the host's normal options.
5. Copy `integration/res/` into the host's resources. The surface layout expects the host's
   `layout_ignoreInsets` attribute and native drag-layer layout parameters; map these for
   your host. The launcher patch kit demonstrates the window-style selector installation.
6. Review `TransitionDiagnostics` before distribution: it automatically sends bounded event
   codes, generation and SDK to the author's collector. It needs INTERNET and
   ACCESS_NETWORK_STATE in the host manifest. No app identity or icon pixels are sent.
7. Test unlock separately from opening and return. Test workspace corners/center, dock,
   folders, adaptive/nonadaptive icons, cancellation, Home button and gesture navigation.
   A missing system contract must not be described as a successful shrink-to-icon result.

## Build the library only

With Python 3.11+, JDK 17/21 and Android SDK 36 installed:

```sh
python3 tools/bootstrap_gradle.py
export ANDROID_HOME=/path/to/android-sdk
work/toolchain/gradle-8.11.1/bin/gradle --no-daemon --max-workers=1 :runtime:assembleRelease
```

This produces a library AAR, not an installable launcher. Integration resources still belong
in the host app. The full launcher build and regression tests live in the linked patch kit.
Read [the implementation guide](docs/IMPLEMENTATION.md) for exact owners and known limitations.
