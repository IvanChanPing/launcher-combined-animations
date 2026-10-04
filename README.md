# Combined Launcher Animations

Nova-style app transitions and iLauncher-style icon animations for integration into any
Android launcher. Connect the animation code to your launcher's icons, layout and lifecycle
through a launcher adapter.

Known issue: selected-icon expansion and return direction need correction.

## Contents

- `runtime/`: Android Java library with a public host API and bundled gesture layout.
- `integration/res/`: optional return-window resources.
- `docs/IMPLEMENTATION.md`: complete ordered flow, timing, coordinates, lifecycle hooks, and test matrix.
- `tools/bootstrap_gradle.py`: pinned Gradle download helper.

Implement `LauncherHost` on your launcher Activity. It supplies the views, artwork and launch
callbacks; the runtime contains no launcher-specific class names, reflection or model fields.
`LauncherScene` accepts your grid coordinates and dock views. No Launcher Phone OS dependency
or replacement of animation classes is needed.

## Integrate into your launcher

1. Copy `runtime/` into the host Gradle project, include `:runtime` in settings, and add
   `implementation(project(":runtime"))` to the host app. The included module uses SDK 36,
   Java 17 and minSdk 24. This repository pins AGP 8.10.1 and Gradle 8.11.1.
2. Implement `LauncherHost` on your Activity. Supply `transitionRoot`, `isTransitionBinding`,
   `captureTransitionScene`, `transitionIconBounds`, `transitionIconDrawable`,
   `launchFromTransition` and `findTransitionTarget`. Artwork bounds are icon-local;
   scene anchors and cell height are root-local pixels. See the [host API guide](docs/HOST_API.md).
3. Call `CombinedTransitionController.install(application)` after Application initialization.
   Forward creation, start, resume, focus, model-ready and configuration callbacks after the
   host's own body. Forward pause, stop and destroy at entry. For `onNewIntent`, call
   `recordHomeIntent(activity, intent)` at entry and `onLauncherNewIntent(activity)` after
   the host finishes processing the intent. See the controller's public callback methods.
4. At the host's safe launch gate, invoke `interceptLaunch(activity, sourceView, intent, item)`.
   A true result means the controller owns this launch. The existing safe launch route must
   remain callable by `launchFromTransition`; do not replace it with a bare `startActivity`.
   The host options provider calls `consumeLaunchOptions(activity, sourceView)` and uses its
   non-null result; otherwise preserve the host's normal options.
5. The AAR includes its gesture-surface layout using standard Android attributes. For a custom
   root with special inset handling, override `createTransitionSurface` and return an unattached
   `NovaGestureSurface` with that root's layout parameters. Return-window XML is optional;
   install it in your launcher theme only if you want the non-gesture fallback.
6. `TransitionDiagnostics` automatically sends transition event
   codes, generation and SDK to the author's collector. It needs INTERNET and
   ACCESS_NETWORK_STATE in the host manifest. No app identity or icon pixels are sent.
7. Test unlock separately from opening and return. Test workspace corners/center, dock,
   folders, adaptive/nonadaptive icons, cancellation, Home button and gesture navigation.
   Shrink-to-icon return uses a transition contract supplied by Android's navigation system.

## Build

With Python 3.11+, JDK 17/21 and Android SDK 36 installed:

```sh
python3 tools/bootstrap_gradle.py
export ANDROID_HOME=/path/to/android-sdk
work/toolchain/gradle-8.11.1/bin/gradle --no-daemon --max-workers=1 :runtime:assembleRelease
```

The output is an AAR library. Optional return-window resources belong in the host app.
See [the implementation guide](docs/IMPLEMENTATION.md) for animation timing and lifecycle details.

## Example integration

The [Launcher Phone OS patch kit](https://github.com/IvanChanPing/launcher-phone-os-combined)
shows one integration, including APK hooks, build scripts and tests. Its version-specific
patcher is separate from this host API; it retains the earlier launcher-specific adapter.

## Source checks

```sh
python3 -m unittest discover -s tests -v
```

The checks cover the host boundary, resources, preserved timing and two unrelated host fixtures.
They do not run Android views. This host API revision has not been Android-compiled.
