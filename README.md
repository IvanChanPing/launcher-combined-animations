# Combined Launcher Animations

Nova-style app transitions and iLauncher-style icon animations for integration into any
Android launcher. Connect the animation code to your launcher's icons, layout and lifecycle
through a launcher adapter.

Known issue: selected-icon expansion and return direction need correction.

## Contents

- `runtime/`: Android Java library, nine source classes, no third-party runtime dependencies.
- `integration/res/`: return-window resources and the gesture-surface layout.
- `docs/IMPLEMENTATION.md`: complete ordered flow, timing, coordinates, lifecycle hooks, and test matrix.
- `tools/bootstrap_gradle.py`: pinned Gradle download helper.

`LauncherAccess` connects the animation code to the host launcher. The included adapter uses
Launcher Phone OS 1.4.1 as an example; replace its class, method and field mappings for your
launcher. Launcher Phone OS is not a required dependency.

## Integrate into your launcher

1. Copy `runtime/` into the host Gradle project, include `:runtime` in settings, and add
   `implementation(project(":runtime"))` to the host app. The included module uses SDK 36,
   Java 17 and minSdk 24. This repository pins AGP 8.10.1 and Gradle 8.11.1.
2. Implement `LauncherAccess` for your launcher's class, safe-launch method, root,
   binding-ready state, visible page/dock/folder, icon drawable, model and user-profile data.
   Map icon bounds into the animation root's coordinate space.
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

The output is an AAR library. Copy the integration resources into the host app.
See [the implementation guide](docs/IMPLEMENTATION.md) for animation timing and lifecycle details.

## Example integration

The [Launcher Phone OS patch kit](https://github.com/IvanChanPing/launcher-phone-os-combined)
shows one integration, including APK hooks, build scripts and tests. Its version-specific
patcher is separate from the animation library.
