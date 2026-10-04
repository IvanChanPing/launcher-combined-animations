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

### 1. Start with the launcher you want to change

You add this code **inside your launcher's project**. It is not a theme or an APK that changes
whichever launcher is already installed. You need editable launcher source and its build setup.
If you only have an APK, decompiling and patching it is a separate job; these steps are for source.

Below, “host” just means your launcher. The animation library handles the animation; the host
tells it where the icons are and how to open an app. You keep your own launcher package, layout
and app model. You do not need to use Launcher Phone OS.

The renderer uses Android Views. A Compose-only or Flutter launcher needs a View-backed bridge
for its icon artwork and overlay container; copying the module alone does not provide that bridge.

### 2. Copy the animation module into your project

Download this repository and copy its entire `runtime/` folder beside your launcher's app module:

```text
your-launcher/
├── settings.gradle.kts
├── build.gradle.kts
├── app/                    Your existing launcher
└── runtime/                The folder copied from this repository
```

Keep the Java sources and `res/` files together. The resources include the surface used during
return-to-Home. You do not need this repository's tests or the example launcher's patcher to
include the module in your app.

### 3. Connect the two modules in Gradle

In your existing `settings.gradle.kts`, add:

```kotlin
include(":runtime")
```

In your launcher module's `build.gradle.kts`, add this inside its existing `dependencies` block:

```kotlin
implementation(project(":runtime"))
```

For Groovy build files, the equivalents are `include ':runtime'` in `settings.gradle` and
`implementation project(':runtime')` inside `dependencies` in `app/build.gradle`.

The copied module uses Android SDK 36, Java 17 and minSdk 24. This repository pins Android
Gradle Plugin 8.10.1 and Gradle 8.11.1. Your project must resolve `com.android.library` through
its plugin setup; use the same Android plugin version as your app module. Do not paste a second,
conflicting plugin version into an existing setup or replace your whole build file.

Sync the project in Android Studio. This step connects the library to your launcher; it does
not yet connect any icon taps or animations.

### 4. Connect your Home screen Activity

Find the Activity registered for `android.intent.category.HOME` in your manifest. This is the
screen Android opens when you go Home. Make that Activity implement `LauncherHost`, keeping
its existing superclass and other interfaces.

Import the library types where needed:

```java
import com.ivanchan.launcher.combined.transitions.CombinedTransitionController;
import com.ivanchan.launcher.combined.transitions.LauncherHost;
import com.ivanchan.launcher.combined.transitions.LauncherScene;
```

Use your IDE's “Implement methods” action for `LauncherHost`, then fill in all seven methods.
Do not leave the generated `null` or `false` placeholders without checking what each means:

| Method | What you supply |
| --- | --- |
| `transitionRoot()` | The `ViewGroup` containing your visible icons and the animation overlay. |
| `isTransitionBinding()` | `true` while you are rebuilding or loading the icon views; `false` when ready. |
| `captureTransitionScene(selected)` | A description of the current visible grid and dock, built in step 5. Return `null` if unavailable. |
| `transitionIconBounds(icon)` | The artwork rectangle inside that icon's View, without the text label. |
| `transitionIconDrawable(icon)` | That icon's artwork, with an independent drawable constant state. |
| `launchFromTransition(icon, intent, item)` | Your existing safe app-launch route, connected in step 7. |
| `findTransitionTarget(scene, component, user)` | The visible icon to return to for that app and user profile, or `null` if none exists. |

These methods are the adapter. Their bodies depend on your launcher because every launcher
stores its icons and app records differently. The animation classes do not need to be renamed
to match your launcher. Full signatures are in [LauncherHost.java](runtime/src/main/java/com/ivanchan/launcher/combined/transitions/LauncherHost.java).

### 5. Tell it which icons and bottom bar to animate

Inside `captureTransitionScene`, create a fresh scene from the page that is actually visible:

```java
LauncherScene scene = new LauncherScene(
    root, visiblePage, true, anchorX, anchorY, cellHeight);
for (MyCell cell : visibleCells) {
    scene.addIcon(cell.view, cell.column, cell.row, columnCount, rowCount);
}
scene.addStrip(dock);
scene.addStrip(pageIndicator);
return scene;
```

This is a template, not a complete method to paste unchanged. `MyCell`, `visibleCells`, `root`,
`visiblePage`, `dock` and `pageIndicator` stand for the objects in **your** launcher.
Omit a strip call if your launcher has no corresponding view.

- Pass each whole icon cell, including its label and badge, to `addIcon`.
- Rows and columns start at zero. A four-column grid has columns `0`, `1`, `2`, `3`.
  Include empty cells when giving the total grid width and height.
- Add only the current visible page or open folder/app-list surface, not every page at once.
- The constructor's `true` means this is the Home workspace. Use `false` for a folder or app list.
- Add the bottom app bar as one `dock` strip. **Do not also add its app icons to the grid loop.**
  The bar moves as a group; treating its children as grid icons would give them two animation roles.
- Every view must be inside `root`. Do not add the same icon twice or overlapping strips.

`anchorX` and `anchorY` are the point the grid moves relative to. They and `cellHeight` use
pixels in the root's coordinate system, not screen coordinates or dp. The
[host API guide](docs/HOST_API.md#build-a-scene) explains the iLauncher anchor calculation
and the `LauncherGeometry` helpers for converting positions.

There are two different rectangles to keep straight: the grid captures the **whole cell**;
`transitionIconBounds` describes **only the artwork**, measured inside the supplied icon View.
Do not include the label or pass screen coordinates as artwork bounds.

Build the scene after layout has given the views their positions. Recreate it after page or
model changes, and do not alter a scene while it is playing.

### 6. Tell the library when the launcher starts, stops and receives Home

In your existing Application's `onCreate`, after its normal initialization, call:

```java
CombinedTransitionController.install(this);
```

If you add an Application class, register it in your manifest. If the launcher already has one,
use that class rather than replacing it.

In the Home Activity, forward these events. All the calls below take the Activity as `this`.
Keep the launcher's existing callback bodies and superclass calls.

| Your launcher event | Library call | Where to put it |
| --- | --- | --- |
| `onCreate` | `onLauncherCreated(this)` | After the launcher initializes its model and views. |
| `onStart` | `onLauncherStarted(this)` | After the existing body. |
| `onResume` | `onLauncherResumed(this)` | After the existing body. |
| `onWindowFocusChanged` | `onLauncherFocused(this)` | After the existing body; the controller reads the current focus. |
| Icon/model binding finishes | `onLauncherModelReady(this)` | After binding is complete and your binding flag is cleared. |
| `onConfigurationChanged` | `onLauncherConfigurationChanged(this)` | After the existing body. |
| `onPause` | `onLauncherPaused(this)` | At entry, before the existing body. |
| `onStop` | `onLauncherStopped(this)` | At entry, before the existing body. |
| `onDestroy` | `onLauncherDestroyed(this)` | At entry, before the existing body. |

Prefix each library call in the table with `CombinedTransitionController.`. “Binding finishes”
is your launcher's own event, not a standard Activity callback: find where it finishes adding
icons to the screen and notify the controller there.

`onNewIntent` needs two calls, in this order:

```java
@Override
protected void onNewIntent(Intent intent) {
    CombinedTransitionController.recordHomeIntent(this, intent);
    super.onNewIntent(intent);
    // Keep the rest of your launcher's existing intent handling here.
    CombinedTransitionController.onLauncherNewIntent(this);
}
```

Merge those calls into your existing method; do not delete its Home handling. The first call
reads Android's return-transition information before the launcher processes the intent.
The second tells the controller that the launcher has finished handling it.

### 7. Connect icon taps without replacing your app-launch logic

Find the common method your launcher uses to open an app safely. Connect here rather than
adding a separate `startActivity` call to each icon. Keep the existing profile, shortcut,
permission, safe-mode and disabled-app checks.

At that launch gate, before creating the normal launch options, call:

```java
boolean handled = CombinedTransitionController.interceptLaunch(
    this, sourceView, intent, item);
```

Here `sourceView` is the tapped icon View, `intent` is the existing launch Intent and `item`
is your launcher's app/shortcut record. The library passes `item` back without reading its fields.

- If `handled` is `true`, stop that invocation of the normal launch path. The controller owns
  this tap and will call `launchFromTransition` at the animation's launch point.
- If it is `false`, continue the normal launch path unchanged.
- In `launchFromTransition`, call your existing safe-launch method once and return whether
  it accepted the launch. The controller guards re-entry into the interceptor during this call.

In your existing launch-options provider, ask for the prepared options:

```java
ActivityOptions transitionOptions =
    CombinedTransitionController.consumeLaunchOptions(this, sourceView);
```

If non-null, use those options for this launch. Otherwise use your normal options. If your
launch API takes a `Bundle`, use `transitionOptions.toBundle()`. Do not discard the prepared
options or launch the app again after returning `true` from the interceptor.

Both snippets run in the Activity; use your Activity reference instead of `this` if the code
lives in a helper. `ActivityOptions` is `android.app.ActivityOptions`. Make sure workspace,
dock, folder and app-list taps all reach this integration point. Use `shouldAnimateLaunch`
to exclude host-specific item types that your adapter cannot represent.

### 8. Connect the destination for returning Home

Implement `findTransitionTarget` by looking up the supplied app `component` and `user` in your
currently visible icons. Search the dock first, then the visible workspace. Match both the app
and its profile; a work-profile icon is not interchangeable with its personal-profile copy.

Return the real destination View, not a stored screenshot or an icon on an off-screen page.
Return `null` when there is no visible match. By default, return artwork uses the same local
bounds as opening; override `transitionReturnBounds` if you support a different target such
as a widget.

Shrink-to-icon return uses the transition contract supplied by Android's navigation system.
Connecting this method does not give the launcher control over another app's window when
Android supplies no contract. Test Home-button and gesture navigation separately.

The module already includes the gesture-surface layout. Only override `createTransitionSurface`
if your root needs special inset handling or layout parameters; return a fresh, unattached
`NovaGestureSurface` using that parent's layout-parameter type. The return-window XML in
`integration/res/` is an optional non-gesture fallback, not a substitute for the contract.

### 9. Add the diagnostic permissions

This library automatically uploads transition event codes, generation and SDK to the author's
collector. It does not send app identity or icon pixels. Review this behavior before distributing
your launcher; the uploader is [TransitionDiagnostics.java](runtime/src/main/java/com/ivanchan/launcher/combined/transitions/TransitionDiagnostics.java).

Add these permissions inside your host manifest's `<manifest>` element, outside `<application>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Keep existing declarations if they are already present; do not duplicate them.

### 10. Build your launcher and test the three paths separately

Build the **launcher app**, not just this library. Use your project's normal build task. For a
project with an `app` module and a Gradle wrapper, a debug-build command is:

```sh
./gradlew :app:assembleDebug
```

Install that launcher's resulting APK and select it as the Home app in Android's settings.
An AAR is a library for developers; it is not the APK you install on the phone. If you need
a separately installed test clone, give your host app a different application ID as part of
that launcher's own build setup; changing this library's namespace does not create a clone.

Check these separately so you know which connection you are testing:

1. Lock the phone and unlock to Home. Check the iLauncher-style grid entrance and the bottom bar.
2. Tap an app on the visible workspace. Check the selected-icon opening and the other icons leaving.
3. Return Home from that app. Check the destination icon and the other icons coming back.
4. Repeat with icons at the corners and center, then with the dock, a folder and the app list.
5. Repeat return using the Home button and Home gesture. Also check work-profile apps,
   adaptive/nonadaptive icons, interrupted animations and system animations disabled.

The selected-icon expansion/return-direction issue noted above remains open; these instructions
explain integration, not a claim that its motion has been corrected.

For the full coordinate and callback contract, use [HOST_API.md](docs/HOST_API.md).
For timing and the ordered animation flow, use [IMPLEMENTATION.md](docs/IMPLEMENTATION.md).

## Build the library on its own

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
