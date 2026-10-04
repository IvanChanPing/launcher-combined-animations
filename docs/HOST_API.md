# Launcher host API

The launcher Activity implements `LauncherHost`; it can extend any `Activity` subclass and use
its own package, views and app model. The runtime retains the Activity weakly and calls the host
on the main thread. No global adapter singleton or reflection map is used.

## Required methods

| Method | Return / responsibility |
| --- | --- |
| `transitionRoot()` | The ViewGroup containing animated views and overlays. |
| `isTransitionBinding()` | True while the model or view hierarchy is being rebuilt. |
| `captureTransitionScene(selected)` | A fresh `LauncherScene` for the visible page, folder or app list; null when unavailable. |
| `transitionIconBounds(icon)` | Artwork rectangle local to the supplied View, excluding its label. |
| `transitionIconDrawable(icon)` | Drawable with an independent constant state for selected-icon opening. |
| `launchFromTransition(icon, intent, item)` | Call the launcher's existing safe-launch method once; return whether it accepted the launch. |
| `findTransitionTarget(scene, component, user)` | Resolve the visible destination for that app and profile, or null. |

`Object item` belongs entirely to the host. The runtime passes it back without reading fields.
Profile matching, shortcuts, permissions, safe mode and app-disabled checks stay in the launcher.
For return target lookup, search the dock before the visible workspace and match the supplied user.
If your launcher supports widget destinations, prefer an app icon before a widget.

## Build a scene

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

`MyCell` represents your own model, not a library type. All views must descend from `root`.
Pass zero-based cell positions, including empty cells in the declared grid dimensions.
Each icon appears once. Do not also add dock children as grid icons; the dock is one strip.
The runtime detects whether the selected icon belongs to a strip through ordinary parent links.
Duplicate icons, overlapping strips, out-of-range cells and invalid geometry are rejected
before originals are hidden.

For the iLauncher grid layout, use `MotionMath.centerRow(columns, rows)` for the anchor row
and the midpoint between the two center columns. Convert the anchor to root-local pixels;
use `LauncherGeometry.toRoot(view, root)` or `bounds(view, root)` for transformed views.
For custom layouts, choose the anchor in your host and assign logical rows/columns explicitly.

Return a new scene after layout/model changes. Do not retain or mutate a scene during playback.
Supply icon artwork bounds in local coordinates; the runtime maps them to the animation root.
For widget return destinations, override `transitionReturnBounds` to return full widget bounds.

## Launch integration

Call `interceptLaunch` at the existing safe-launch gate. When it returns false, continue your
normal launch. When it returns true, the runtime owns that tap and will call
`launchFromTransition` at the animation handoff. Keep your existing launch checks and flags.
During that callback, `consumeLaunchOptions(activity, icon)` returns the prepared
`ActivityOptions`; use those options in the safe-launch path, falling back to your normal ones
when null. The controller guards re-entry so the callback can use the original launch method.

Override `shouldAnimateLaunch` for host-specific item types or exclusions. To explicitly skip
an animation, add `CombinedTransitionController.IGNORE` to the launch Intent.

## Lifecycle

Call `install(application)` once from the Application. After your Activity has initialized its
model and views, forward `onLauncherCreated`, then start/resume/focus/configuration callbacks
after the corresponding host body. Call `onLauncherModelReady` when model binding completes.
Forward pause/stop/destroy at entry so transient captures can be released.

For Home intents, call `recordHomeIntent(activity, intent)` before host processing and
`onLauncherNewIntent(activity)` afterward. It consumes the optional system gesture contract
once. Home-button and gesture paths both use the intent supplied by Android; the library does
not invent a missing contract or take ownership of another app's window.

## Custom roots and rendering

The bundled layout uses only Android attributes. `createTransitionSurface` inflates it against
the actual parent so the parent creates its own LayoutParams. Override the factory if your
root needs special inset fields. Return a fresh, unattached `NovaGestureSurface` and preserve
the parent's LayoutParams type; no overlay permission is requested.

This renderer uses Android Views and Canvas. A Compose-only or other non-View launcher needs
View-backed transition artwork and a ViewGroup overlay host. Integrating the source does not
change an unrelated already-installed launcher without modifying that launcher.

The existing selected-icon expansion/direction issue is still open. Test on the target device
with workspace/dock icons, folders, profiles, Home button, gesture Home, cancellation and unlock.
