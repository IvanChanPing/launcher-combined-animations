# Pre-build risk pass — 2026-10-03

## 2026-10-04 — Host-independent runtime

Before editing: all nine runtime owners were read. LauncherAccess contains the vendor class,
method, field, grid and profile mappings; the controller also checks vendor types/intent flags,
and gesture XML requires a vendor attribute. These are the complete host-bound entry points.
Replace them with LauncherHost (implemented by the Activity), LauncherScene and geometry helpers.
Keep the Activity weakly referenced: no host object is stored separately by the controller.
Host callbacks supply safe launch, artwork, layout/model readiness, scene and return identity.
Validate scene geometry before hiding views. Keep native fallback, cancellation generations,
surface finish and snapshot restoration unchanged; do not tune motion or add terminal decisions.
Bundle a plain surface layout; custom parents may override its factory to supply native parameters.
Source checks must reject vendor names/reflection in runtime and cover two unrelated host classes.
No new logging is added; existing bounded automatic diagnostics remain unchanged.
Android compilation is not authorized. Java parsing and source tests cannot establish device motion.
The known expansion/direction failure is outside this portability change.

## Verified source contracts

- Exact original base and both split hashes are pinned; canonical decode is tied to these inputs.
- Existing safe launch and options methods are reused; no replacement of licensing or permissions.
- Model-ready F(), post-body lifecycle hooks and root a0() follow the actual target ownership.
- Root ViewGroupOverlay supports absolute child layout and explicit removal. SDK36 includes the
  imported Android classes; API33 gates splash style/receiver overloads; minimum API24 retained.
- Native Bitmap/Canvas snapshots, Matrix mapping, ValueAnimator, Drawable constant states,
  ActivityOptions and stdlib Python ZIP/XML/subprocess are reused instead of adding frameworks.
- AGP8.10.1 supports API36 and requires Gradle8.11.1; D8 receives android.jar via --lib.
- Apktool2.10.0 consumes nested packageInfo.renameManifestPackage.
- Existing collector accepted a harmless POST probe; public GET is intentionally unavailable.

## Preconditions surfaced before compilation

Exact XAPK, SDK36/build-tools36.0.0, Java17/21, pinned Gradle, Apktool2.10.0, Baksmali,
fresh work directory; signing requires an explicit existing keystore/alias and private password
environment variables. --check never compiles. --compile-authorized is required to invoke builds.
Outputs are base plus both mandatory splits, aligned then signed with the same key.

## Cross-cutting handling in source

Complete cells captured before hiding;24 MiB bitmap cap; native fallback before hidden state on
capture failure; exact alpha restoration; no ancestor clip changes; main-thread visual state;
background-only bounded telemetry; weak Activity keys and weak owner references; generation
guards; all launch/lifecycle hooks; configuration/stop/destroy release; actual binding and
focus/keyguard readiness; CALL permission and same-package launch exclusions; repeat-tap guard.

The48 ms settle window and1500 ms optional-visual timeout are explicit product choices.
They do not label the application failed or repeat an uncertain launch.
Source snapshots are synchronous small-view draws; device tests must measure frame cost and
software Canvas compatibility with any hardware-backed icons.

## Unverified runtime risks — not hidden prerequisites

- Android compilation/type-check, D8 conversion, Apktool assembly and signing are not yet run.
- Renaming/re-signing can conflict with PairIP, billing, server identity and vendor integrity checks.
  Protection code and original product identifiers remain untouched; no bypass is implemented.
- OEM gesture navigation may override the requested window animation. Real UI tests must identify
  that result; source window selectors alone are not visual proof.
- Snapshot drawing, normalization, transformed artwork, folder/search geometry and timings require
  frame-by-frame actual-device comparison. Unsupported grids use explicit extensions.
- The automatic telemetry queue is bounded and in-memory; unsent events can be lost at process death.

## Verification reachability

Run host contract tests, XML/Python/Java syntax parsing, SDK import census and exact-target copied
owner/resource preparation before the first authorized compile. After compilation, follow the real
UI matrix in IMPLEMENTATION.md. No backend/source check is represented as end-to-end proof.

## 2026-10-03 Nova gesture-contract port risk pass

VERIFIED from Nova: o9/p1 consumes the HOME bundle; FloatingSurfaceView records the real icon
in a Picture, attaches a translucent top SurfaceView, shares its SurfaceControl/RectF and finish
Message; ea/a handles what=0. API30 panel flags/type and API31+ DragLayer attachment are distinct.
Nova uses a400 ms focus fallback only through stableAPI32 and a two-frame removal delay.
AOSP FallbackSwipeHandler confirms the system animates the actual window and icon surface.

Canonical reuse: Android Picture, SurfaceView, SurfaceHolder, Messenger and target LayoutInflater
native parameters; target layout_ignoreInsets attr is0x7f04033e. No substitute reverse animator,
screen capture permission, new dependency, privileged remote runner or locally chosen close curve.

Preconditions/variants: API30+, real system contract/replyTo, current model/root/window, visible
package/profile-matching app or widget. Malformed/absent contracts or unavailable targets use the
native fallback with bounded diagnostic codes. Contract response is not gated on Home gaining focus.
Grid-disabled mode still responds; unlock has no gesture provider. Pause/destroy/configuration,
touch/new gestures and stale completion are covered; tray-only restoration waits for its parent.
Message copyFrom preserves the system routing token. Diagnostics exclude identities/pixels/parcels.

UNVERIFIED: OEM delivery/consumption of the contract, compositor coordinates/insets, API30 panel
attachment and real animation frames. No new Android compilation is authorized in this pass.
The first published APK does not include this port. The invented return artwork path is removed.
