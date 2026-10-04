# Combined Launcher Animations

https://github.com/user-attachments/assets/44daed2d-2217-47fd-92bb-d5a377fc6a06

Nova-style app transitions and iLauncher-style icon animations for integration into any
Android launcher. Connect the animation code to your launcher's icons, layout and lifecycle
through a launcher adapter.

Known issue: selected-icon expansion and return direction need correction. This is integration
source, not a finished, device-verified animation release.

## Before you start

This guide takes you from an existing launcher project to connecting the animation library,
building your launcher and checking the result on a phone. The three paths to check are:

- Unlock to Home: the home-screen icons enter, with the bottom app bar moving separately.
- Open an app: the tapped icon animates while the other icons leave.
- Return Home: Android's supplied return transition connects to the destination icon while
  the other icons enter again.

You need:

- The **source project of the launcher you want to modify**, not just its installed APK.
- A working build setup for that project. Build and run the unchanged launcher first so you
  have a starting point to compare against.
- Android SDK 36 and a Java 17-compatible toolchain for the copied module. Its minimum SDK is 24.
- An editor such as Android Studio, and enough Java or Kotlin to edit an Activity and follow
  a method call. This is a source integration, not a phone setting.
- An Android phone or emulator for testing the resulting launcher.

The examples use Java for Activity code and Kotlin syntax for Gradle files. If your Activity
is Kotlin, implement the same interface in Kotlin; do not paste Java methods into a `.kt` file.
No particular launcher package or superclass is required.

In this guide, **Activity** means the class that owns your Home screen; **root** is the container
holding that screen's views; **dock** is the bottom row of pinned apps; and **binding** means
the launcher is filling or rebuilding its icon views. An **adapter** is the small amount of
code you write to connect those existing objects to this library.

Follow [steps 1–10](#integrate-into-your-launcher) in order. The
[standalone library build](#build-the-library-on-its-own) and
[example patch kit](#example-integration) are separate reference sections, not extra setup steps.

### Start with your renderer

Before implementing the adapter, identify how your launcher draws one icon. The class drawing
the artwork is not always the View you need to pass to the animation library.

| Your launcher uses | Start here |
| --- | --- |
| An `ImageView`, or a cell containing an image, label and badge | [Worked View-cell example](docs/RENDERER_INTEGRATION.md#2-work-through-one-view-based-cell) |
| A custom View that draws its own icon with Canvas | [Custom View recipe](docs/RENDERER_INTEGRATION.md#3-adapt-the-artwork-recipe-to-your-renderer) |
| Adaptive, themed, icon-pack or cached bitmap artwork | [Choose the artwork representation](docs/RENDERER_INTEGRATION.md#3-adapt-the-artwork-recipe-to-your-renderer) |
| One Canvas, Compose, Flutter, OpenGL or another shared surface for many icons | [Renderer boundary and missing bridge](docs/RENDERER_INTEGRATION.md#1-check-whether-the-current-api-fits-your-renderer) |

The [renderer integration guide](docs/RENDERER_INTEGRATION.md) shows what to inspect, which
objects to connect, and what to check before continuing. It distinguishes existing API support
from additional renderer work. One package-independent interface does not automatically provide
an adapter for every rendering system.

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

1. Download or clone [this repository](https://github.com/IvanChanPing/launcher-combined-animations).
2. Open your existing launcher project folder. Use the folder containing its `settings.gradle`
   or `settings.gradle.kts`, not the `src/` folder.
3. Copy this repository's entire `runtime/` folder into that folder, beside your launcher module.

For a launcher whose app module is named `app`, the result is:

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

Before continuing, check that `runtime/build.gradle.kts` and `runtime/src/main/` exist in your
launcher project. If you put `runtime` inside `app/src/`, move it beside `app` instead.

### 3. Connect the two modules in Gradle

In your existing `settings.gradle.kts`, add:

```kotlin
include(":runtime")
```

In your launcher module's `build.gradle.kts` (for example `app/build.gradle.kts`), add the
dependency to the existing block. Keep any dependencies already there:

```kotlin
dependencies {
    implementation(project(":runtime"))
    // Keep your other dependencies here.
}
```

For Groovy build files, the equivalents are `include ':runtime'` in `settings.gradle` and
`implementation project(':runtime')` inside `dependencies` in `app/build.gradle`.

The copied module uses Android SDK 36, Java 17 and minSdk 24. This repository pins Android
Gradle Plugin 8.10.1 and Gradle 8.11.1. Your project must resolve `com.android.library` through
its plugin setup; use the same Android plugin version as your app module. Do not paste a second,
conflicting plugin version into an existing setup or replace your whole build file.

Sync the project in Android Studio. This step connects the library to your launcher; it does
not yet connect any icon taps or animations.

Before continuing, confirm that your editor can resolve the `LauncherHost` import in step 4.
If it cannot, check the module folder, settings entry and app dependency before adding more code.

### 4. Connect your Home screen Activity

1. In your launcher project, search for `android.intent.category.HOME` in `AndroidManifest.xml`.
2. Find the enclosing `<activity>` and read its `android:name`. If it is an `<activity-alias>`,
   follow its `android:targetActivity` to the actual Activity class.
3. Open that class. This is where the Activity-side changes in this guide belong.
4. Add `LauncherHost` to its implemented interfaces, keeping the existing superclass and
   any interfaces it already implements.

For example, if your Java declaration is:

```java
public class HomeActivity extends ExistingBaseActivity {
```

Change only the declaration to:

```java
public class HomeActivity extends ExistingBaseActivity implements LauncherHost {
```

`HomeActivity` and `ExistingBaseActivity` are example names. Keep the names already in your file;
do not create a replacement Activity just to match the example. These snippets show one line,
not a complete class.

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

Use the [worked cell mapping](docs/RENDERER_INTEGRATION.md#2-work-through-one-view-based-cell)
when filling these methods. In particular, use the same whole-cell View for scene membership,
launch interception and return lookup; do not mix a child ImageView with its parent cell.

To fill in those methods, first identify these existing objects in your launcher:

| Find in your launcher | How to locate it | Used by |
| --- | --- | --- |
| Home-screen container | Follow the Activity's layout inflation and view initialization. Choose a `ViewGroup` containing both the page and dock. | `transitionRoot` |
| Current page and icon cells | Follow the code that adds icons to the visible page; use its cell positions and page-selection state. | `captureTransitionScene` |
| Model-loading flag | Follow the start and end of that icon-binding code. | `isTransitionBinding` |
| Icon artwork and its rectangle | Read the icon View's drawing code or artwork accessors. Use artwork bounds, not the whole cell rectangle. | `transitionIconBounds`, `transitionIconDrawable` |
| Existing app-launch method | Follow an icon's click listener to the method that checks the item and opens it. | `launchFromTransition` |
| Component and user stored on each item | Follow the data used to bind each visible icon. | `findTransitionTarget` |

This is the launcher-specific work. There is no universal field name to copy here. Once you
have these objects, the following steps show how to pass them to the library; you do not need
to rewrite the animation engine.

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

For example, suppose an icon cell is 96 pixels wide and 120 pixels high, but its artwork occupies
the rectangle from `(20, 8)` to `(76, 64)` inside that cell. Its artwork bounds would be
`new RectF(20, 8, 76, 64)`, not `new RectF(0, 0, 96, 120)`. These numbers only illustrate the
coordinate system; measure your own artwork rather than copying them.

Build the scene after layout has given the views their positions. Recreate it after page or
model changes, and do not alter a scene while it is playing.

Before continuing, check one scene by following its inputs: each visible cell appears once,
its row/column is inside the declared grid, the dock is a separate strip, and `cellHeight` is
greater than zero. Do not supply an unmeasured scene while the launcher is still loading.

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

Before continuing, check both sides of the connection: Application initialization calls
`install`, and the Activity forwards every event in the table. `onResume` alone does not replace
the model-ready notification or the two Home-intent calls.

### 7. Connect icon taps without replacing your app-launch logic

In your editor, open a workspace icon's click listener and follow the method it calls to launch
the item. Then follow a dock icon and a folder icon. Identify the common launch method, or the
separate paths if they do not share one. Search for `startActivity` and `LauncherApps` as starting
points, then follow their callers; a matching name alone is not enough to choose the hook.

Connect at the existing safe-launch gate rather than adding a new direct launch to each icon.
Keep the existing profile, shortcut, permission, safe-mode and disabled-app checks.

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

The order is:

```text
Tap an icon → existing launch checks → interceptLaunch
    false → continue the existing launch
    true  → controller owns the tap
              → launchFromTransition at handoff
              → existing safe launch uses consumeLaunchOptions
```

Before continuing, trace the `true` branch: it must not also perform the original immediate
launch. Trace the callback too: it must reach the existing safe-launch method, not a second
new launch implementation.

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

1. Save your changes in the launcher project.
2. Open a terminal in that project's root folder—the one containing its Gradle wrapper.
3. Build the **launcher app**, not just this library, using the project's normal build task.

For a project with an `app` module and a Gradle wrapper, a debug-build command is:

```sh
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat :app:assembleDebug`. If your project uses a different module name
or build variant, use the task you used for its unchanged build in the prerequisites.

4. Wait for the build to finish successfully. If it fails, address the first reported error;
   do not install an older APK and treat it as this build.
5. Install the APK produced by that successful build, using your launcher's existing install workflow.
6. On the phone, open Android Settings, search for **Home app**, and select your test launcher.

An AAR is a library for developers; it is not the APK you install on the phone. If you need
a separately installed test clone, give your host app a different application ID as part of
that launcher's own build setup; changing this library's namespace does not create a clone.

Check these separately so you know which connection you are testing. They are acceptance checks,
not a claim that this revision has already passed them:

1. Lock the phone and unlock to Home. Check the iLauncher-style grid entrance and the bottom bar.
2. Tap an app on the visible workspace. Check the selected-icon opening and the other icons leaving.
3. Return Home from that app. Check the destination icon and the other icons coming back.
4. Repeat with icons at the corners and center, then with the dock, a folder and the app list.
5. Repeat return using the Home button and Home gesture. Also check work-profile apps,
   adaptive/nonadaptive icons, interrupted animations and system animations disabled.

For the full coordinate and callback contract, use [HOST_API.md](docs/HOST_API.md).
For timing and the ordered animation flow, use [IMPLEMENTATION.md](docs/IMPLEMENTATION.md).

## If you get stuck

Use the exact error or the first failing step to choose what to inspect. A visual symptom alone
does not identify its cause.

| What you see | What to check next |
| --- | --- |
| Your editor cannot resolve `LauncherHost` | Check the three connections in steps 2–3: module folder, settings entry, app dependency. |
| `Invalid scene geometry` | Check the supplied anchor values are finite and the measured cell height is positive. |
| `Invalid cell coordinates` | Check zero-based row/column indices against the grid dimensions passed to `addIcon`. |
| `Duplicate scene icon`, `Strip contains a grid icon` or `Overlapping scene strips` | Check scene membership in step 5; a dock child must not also be a grid item. |
| The app opens twice | Trace the interceptor's `true` branch and `launchFromTransition` in step 7; there must be one launch handoff. |
| Unlock entrance is absent | Check Application installation, the binding flag, model-ready notification and Activity callbacks in step 6. |
| Home return has no destination icon | Check component/profile matching, target visibility, and Home-intent forwarding in steps 6 and 8; also check whether Android supplied a return contract. |

The known selected-icon motion issue at the start of this README is separate from these
integration checks. Do not assume changing your adapter will resolve that library issue.

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
