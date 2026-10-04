# Integrate your launcher's icon renderer

Use this guide when you have a launcher source project and need to connect its existing icons
to `LauncherHost`. Keep your launcher, renderer and app model. The task is to map their data
to the library's inputs, not to rename your classes to match an example launcher.

Prerequisites: the project builds unchanged; you can locate the Home Activity and an icon click
handler; you have completed the README's module setup. Method signatures are in
[HOST_API.md](HOST_API.md). Lifecycle and launch wiring are in the
[README](../README.md#6-tell-the-library-when-the-launcher-starts-stops-and-receives-home).

Status: this is a source integration guide, not a tested adapter for every launcher. The current
library still has the selected-icon motion issue described in the README. These instructions
do not repair that issue or promise Nova parity. The code example below is an isolated geometry
helper, not a complete launcher or a compiled sample application.

## 1. Check whether the current API fits your renderer

Open the code that draws a visible app icon and answer these in order:

1. Does each icon cell have its **own Android View**? A custom `View.onDraw(Canvas)` counts;
   drawing fifty icons inside one View does not give you fifty independently addressable Views.
2. Can that View draw the whole cell, including the label and badge, through `View.draw(Canvas)`?
   `SnapshotGridView` uses that operation for sibling snapshots.
3. Can you obtain the displayed icon artwork as a `Drawable` with independent constant state,
   and its rectangle inside that cell? `IconOverlayView` uses those for the selected icon.
4. Can you identify a `ViewGroup` ancestor containing all participating cells and dock strips?

If all four are true, continue with the View-cell route. Otherwise use this boundary table
before writing an adapter:

| Renderer | Existing route | Additional work, if any |
| --- | --- | --- |
| One stock ImageView per cell | View-cell route | Account for the image matrix and padding; check that the drawable is fully visible. |
| Cell ViewGroup with image/label/badge children | View-cell route | Convert the child's artwork rectangle into the parent cell's coordinates. |
| One custom Canvas-drawn View per cell | View-cell route | Expose the actual artwork and draw rectangle from that View's existing render data. |
| One shared Canvas or texture atlas for the whole grid | No direct per-icon route | Supply real per-cell View-backed presentation or extend the renderer contract. A rectangle alone does not satisfy the current API. |
| Compose-only icons | No direct composable-node route | Compose nodes are not the per-cell Views required here. Android View interoperability exists, but a working per-cell bridge must be implemented and tested. |
| Flutter, OpenGL, SurfaceView or TextureView output | No supplied adapter | Resolve per-cell capture, artwork, coordinates and visibility ownership in a renderer-specific bridge. Do not assume the surface appears in a `View.draw` bitmap. |

Do not pass the same page View for every icon: `LauncherScene.addIcon` rejects duplicate Views,
and the controller hides the selected View. Do not add invisible placeholder Views and call the
bridge complete; the runtime checks visibility and those placeholders do not draw the real cells.

Checkpoint: you can name the actual per-cell View and its artwork provider, or you have identified
that your renderer needs an unimplemented bridge. A project using several renderers can have more
than one answer; classify its workspace, folders and app list separately.

## 2. Work through one View-based cell

Assume your existing launcher has this layout. These names describe the example; they are not
classes supplied by the library:

```text
homeRoot : ViewGroup
├── currentPage
│   └── cell : ViewGroup            One item in your app model
│       ├── image : ImageView       The app artwork
│       ├── label                  The app name
│       └── badge                  Optional count/profile marker
└── dock : ViewGroup
```

### A. Choose one consistent cell identity

At the click handler, keep the `cell` reference and its existing app-model item. Use that same
View object in all of these places:

| Connection | Example value |
| --- | --- |
| `scene.addIcon(...)` | `cell` |
| `interceptLaunch(activity, source, intent, item)` | `source = cell` |
| `transitionIconBounds(source)` | Rectangle expressed inside `cell` |
| `transitionIconDrawable(source)` | Artwork currently displayed by `image` |
| `findTransitionTarget(...)` | The current visible matching `cell`, not a cached View from a previous page |

Why this matters: the grid excludes the selected item by View identity. Passing `image` to the
interceptor while adding `cell` to the scene does not exclude the parent snapshot.

Checkpoint: inspect one tap in your debugger. The scene's selected entry and the launch source
are the same object. Confirm it again for a dock item; dock children are not also grid entries.

### B. Calculate the artwork rectangle, not the whole cell rectangle

For a normal ImageView, start from its drawable bounds, apply its image matrix, add its padding,
then map the rectangle into the cell. This follows the transformation order in
[Android's ImageView drawing code](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/widget/ImageView.java).

The following helper assumes a stock ImageView with fully visible, square icon artwork, no
custom draw overrides, and no cropping or rotation/skew. It returns `null` while artwork/layout
is unavailable. Add these imports to the Java file containing the helper:

```java
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.ivanchan.launcher.combined.transitions.LauncherGeometry;
```

```java
private static RectF artworkBoundsInCell(ImageView image, ViewGroup cell) {
    Drawable artwork = image.getDrawable();
    if (artwork == null || !image.isLaidOut() || artwork.getBounds().isEmpty()) {
        return null;
    }
    RectF bounds = new RectF(artwork.getBounds());
    image.getImageMatrix().mapRect(bounds);
    bounds.offset(image.getPaddingLeft(), image.getPaddingTop());
    LauncherGeometry.toRoot(image, cell).mapRect(bounds);
    return bounds;
}
```

Here `cell` must be an ancestor of `image`; it is the example cell container, not `homeRoot`.
If the ImageView itself is your source cell, use the bounds after padding and omit the ancestor
mapping. If the helper returns `null`, keep that source out of animated eligibility until ready;
do not pass `null` into the overlay constructor. Use the existing `shouldAnimateLaunch` gate.

Worked numbers: a drawable with bounds `(0,0,48,48)`, image scale 1.5, padding `(8,10)`, and
image position `(12,6)` inside an otherwise untransformed cell maps to `(20,16,92,88)` in the
cell. The resulting artwork is 72x72 pixels. The label's height is not part of that rectangle.
These are example inputs, not dimensions to hardcode into your launcher.

For `CENTER_CROP`, a custom clip, a rotated drawable or a custom ImageView subclass, stop using
this simple recipe. Record the actual draw transform and clipping from your renderer. The current
host API accepts a rectangle, not an arbitrary clip/mask or transformed-pixel rendering callback;
some appearances need a renderer extension rather than another rectangle calculation.

Checkpoint: compare the calculated rectangle with the actual draw location in your renderer's
layout/drawing data. Check nonzero padding and an off-center cell before connecting motion.

### C. Supply the artwork that is actually displayed

In `transitionIconDrawable(cell)`, resolve that cell's current ImageView and return its artwork.
Check its constant state is available before admitting the item for animation. The runtime
creates an independent copy; it does not retain and mutate your original drawable's bounds.

Do not reload a default package icon if the cell displays an icon-pack replacement, themed icon,
work-profile variant or shortcut-specific artwork. Check tint/state, masks and overlays too:
copying a drawable does not document or reproduce extra drawing performed by the cell itself.

Checkpoint: the source artwork matches the visible cell, and its representation fits the recipe
in the next section. Matching the package name alone is not this check.

## 3. Adapt the artwork recipe to your renderer

Pick the applicable recipe; do not implement every branch just to complete the guide.

### Custom View drawing one icon with Canvas

1. Open that View's `onDraw` or the renderer it delegates to.
2. Locate the drawable/bitmap, destination rectangle, padding, translations and clipping used
   for the icon—not the text or badge.
3. Expose that existing draw data through your host adapter. Do not guess bounds from cell size.
4. Confirm the whole View still draws the complete cell when the grid takes a snapshot.
5. Check whether the selected artwork can be represented faithfully by the current Drawable
   path. A custom shader or multiple independent decorations may require additional renderer work.

Checkpoint: whole-cell capture and selected-artwork capture are deliberately different inputs.
You have not replaced the whole page with an icon snapshot.

### Adaptive drawable with separate foreground and background

1. Preserve the actual `AdaptiveIconDrawable` when that is what your launcher displays.
2. Supply the displayed artwork rectangle and check that both layers are available.
3. Check whether your launcher applies extra normalization, an icon-pack mask, or its own outline
   and shadow. Record those separately; the current `LauncherHost` methods do not accept all of them.
4. Compare the selected artwork with the original cell before checking its animation.

The runtime's adaptive branch draws foreground and background separately. It also uses its own
rounded clip and layer positioning, so supplying an adaptive drawable does not establish exact
parity with your launcher's mask or with Nova. Android's [adaptive-icon guide](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
describes the platform layers; the engine's current limitations remain a separate concern.

### Flattened bitmap, legacy icon or rendered icon-pack cache

1. Identify the final displayed artwork in your cache, including any baked-in mask or badge.
2. Preserve its density and displayed bounds when exposing it as a drawable.
3. Confirm its constant state and choose the nonadaptive route deliberately.

A flattened bitmap does not provide the original foreground and background as independent
layers. This runtime will draw it as one image; it cannot recover hidden layer content from it.
If separate-layer expansion is required, obtain those layers from the host's original asset
pipeline. Do not fabricate a background and describe it as the original icon.

Checkpoint: document whether this adapter supplies genuine layers or one flattened image.
Do not present these as visually interchangeable implementations.

### Themed icons, animated artwork and recycled cells

1. Resolve the current theme/profile/shortcut artwork at capture time.
2. Check whether tint, state, animation frame or decorations live in the drawable or are added
   by the surrounding View. Preserve what the current API can express; identify what it cannot.
3. Re-resolve return targets from the current visible model. A recycled cell can now represent
   another app, so do not use a saved View as the app's permanent identity.
4. During rebinding, report `isTransitionBinding=true`; clear it and notify model readiness once
   the host has finished. Do not freeze the host's old model just to keep a transition target.

Checkpoint: the same app in two profiles resolves by both component and user, and a theme or
page change does not reuse another item's artwork or bounds.

### Shared-surface and non-View renderers

The current library does not ship a Compose, Flutter, OpenGL or shared-Canvas adapter. A bridge
must provide real per-cell drawing/capture, cell-local bounds, root mapping, selected-item hiding,
restoration and identity lookup. Wrapping the whole screen in one View is not enough.

For Compose, Android documents [embedding Views and handling their reuse](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose).
That is an available platform mechanism, not a ready-made adapter for this library. If you choose
it, implement and test ownership and reuse before enabling these transitions. Alternatively,
extend the library's renderer interface for your native rendering system as a separate feature.

Checkpoint: if you cannot supply the required per-cell contract, stop at the compatibility
boundary. Do not claim the normal View instructions have completed your integration.

## 4. Finish the scene without changing coordinate systems midway

1. Return the shared ancestor container from `transitionRoot`.
2. Add visible whole-cell Views using their actual logical rows and columns. Include empty slots
   in grid dimensions; list indices are not automatically workspace cell positions.
3. Add dock/indicator parents as separate strips; do not add their children to the grid as well.
4. Supply grid anchor and cell height in **root-local pixels**.
5. Supply each icon's artwork bounds in **that cell's local pixels**. The runtime maps them to
   the root. Do not pre-map them and then let the runtime map them a second time.

Keep these three spaces distinct:

| Value | Coordinate space | Owner |
| --- | --- | --- |
| `transitionIconBounds(cell)` | Inside the supplied cell | Your artwork recipe |
| Scene anchor and cell height | Inside the animation root | Your layout adapter |
| Screen location / system destination | Screen-relative when the platform contract asks for it | Runtime/platform boundary |

Checkpoint: translating or scrolling a parent changes the mapped position once, not twice.
If your renderer applies transforms outside the View hierarchy, account for those explicitly;
`LauncherGeometry` cannot read transformations stored only inside your renderer.

## 5. Wire launch and return using the existing host routes

1. Follow the README's lifecycle table and both Home-intent calls. Keep the existing callback bodies.
2. Insert the launch interceptor at the host's safe launch gate, after its required checks.
3. Pass the cell identity chosen in section 2, the existing Intent and the host's own model item.
4. If interception returns `true`, do not also launch immediately. The controller calls
   `launchFromTransition` at handoff; that method uses your existing safe-launch route.
5. Consume the prepared options once for that same source View and pass them through the route
   that actually launches the app, including profile and shortcut branches.
6. In `findTransitionTarget`, resolve a current visible cell by component **and** user. Return
   `null` when no target exists. Do not substitute an off-screen duplicate.

Checkpoint: trace workspace, dock, folder and app-list paths. Each accepted tap has one launch,
not two; unsupported items continue through the host's native route. Return lookup is fresh,
and absence of Android's return contract is not mistaken for a request to invent one.

## 6. Verify the adapter in stages

Use these acceptance checks in order. They describe what to verify on your host; they are not
results already obtained for an arbitrary launcher.

| Stage | Exercise | Evidence required before proceeding |
| --- | --- | --- |
| Build integration | Include the module and implement the interface | Your launcher compiles; no placeholder method bodies remain. |
| Artwork and identity | One ordinary icon, then one padded/custom cell | Correct drawable, local artwork bounds, and consistent selected View identity. |
| Scene membership | Visible page plus dock | Each grid cell once; strips do not contain registered grid items. |
| Launch handoff | Workspace, dock, folder, profile and shortcut taps | One accepted launch and prepared options reaching the actual safe-launch call. |
| Return | Home button and gesture separately | Correct current component/profile target; distinguish supplied-contract and fallback paths. |
| Lifecycle | Lock/unlock, page switch, rebind, rotation, interrupted launch | No stale cell reuse or permanently hidden original Views. |
| Visual comparison | Adaptive and flattened artwork in all supported layouts | Inspect actual device output; parser checks and compilation do not prove appearance. |

The source fixtures [GridHost](../tests/fixtures/GridHost.java) and
[ListHost](../tests/fixtures/ListHost.java) show two API shapes. They deliberately decline app
launches and are **not** complete sample launchers. Do not copy them as an end-to-end integration.

## 7. Keep adapter issues separate from engine limitations

Fix wrong host inputs in the adapter: stale item identity, wrong drawable, duplicate scene
membership, double coordinate conversion, or options not reaching the launch call.

Do not disguise engine differences by changing those inputs until a single phone looks right.
The current Nova comparison identifies differences in normalization/insets, conditional starting
scale, layer anchoring, extra artwork, and measured floating-layout launch bounds. The current
API does not expose all of these as host settings. They require runtime work and separate
validation; this guide documents them instead of inventing integration flags.

## Documentation method

This guide uses renderer-specific recipes rather than one imaginary universal example. Its
structure follows [Diátaxis tutorials](https://diataxis.fr/tutorials/) and
[how-to guidance](https://diataxis.fr/how-to-guides/): concrete steps and checks, with explicit
branches for different tasks. The method/API contract stays in HOST_API.md; animation internals
stay in IMPLEMENTATION.md.

For generated examples, the quality bar is source grounding plus tests, not fluent wording.
[Google's documentation workflow](https://cloud.google.com/blog/topics/developers-practitioners/smarter-authoring-better-code-how-ai-is-reshaping-google-clouds-developer-experience)
and [GitHub's AI-code review guidance](https://docs.github.com/en/copilot/tutorials/review-ai-generated-code)
inform that check. This guide's helper has source/syntax checks only, not Android compilation or
a human usability test. A developer following a renderer branch from a fresh project is still
needed to evaluate whether the instructions are genuinely sufficient.
