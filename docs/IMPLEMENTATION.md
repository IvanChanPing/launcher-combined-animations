# Implementation guide

Publication status, 2026-10-04: this describes the current source design, not proven visual
parity. The 2026-10-03 clone compiled, but the user reported missing icon expansion and wrong
downward motion. Diagnosis and correction remain pending; publishing this source changes no behavior.

## Scope and reference boundaries

The Android-SDK-only runtime combines three animation flows. `LauncherHost` supplies host
behavior and `LauncherScene` supplies explicit view/cell geometry. The core contains no
launcher-specific classes, model fields or reflection. See [Host API](HOST_API.md) for integration.

## Owner map

| Owner | Responsibility |
| --- | --- |
| LauncherHost | Host-supplied launch, layout, artwork and app/profile lookup |
| LauncherScene | Explicit grid cells, anchor and dock/indicator views |
| LauncherGeometry | Android View matrix/scroll coordinate mapping |
| MotionMath | Ring selection, timing, factor-four deceleration and sparse-layout extension |
| SnapshotGridView | Whole icon/label/badge/folder snapshots; dock and indicator strips |
| IconOverlayView | Independent adaptive layers, nonadaptive fallback, Nova tracks and crop |
| CombinedTransitionController | Main-thread lifecycle, one-shot replay, readiness and cleanup |
| NovaGestureContract | Nova HOME-parcel consumption, exact reply and weak finish receiver |
| NovaGestureSurface | Nova Picture/SurfaceView provider; Android owns its trajectory |
| UnlockSignalTracker | USER_PRESENT before/after resume; short-lived unlock token |
| TransitionDiagnostics | Bounded private event codes automatically delivered to the box |

## Ordered flow: open

1. The host launcher performs its original safe-mode and launch-policy checks.
2. The inserted interceptor checks the launcher, source, model, focus, lock, animation setting,
   same-package destination and CALL permission gate. Unsupported paths stay native.
3. LauncherHost supplies the actual visible folder, search/library, or current workspace plus
   dock/indicator. Whole-cell snapshots include labels, badges and folder artwork.
4. The selected drawable's independent constant state and mapped source rectangle are prepared.
   A root overlay draws grid snapshots below the separate selected artwork. No ancestor clipping
   flags or live icon translations/scales are modified.
5. Originals are hidden only after successful capture. The first pre-draw starts one linear clock.
6. Nova movement uses cubic (.2,0,0,1): X 250/360 ms and Y 450/200 ms. The alternate predicate
   uses the destination center, source top/center, and actual cell height. Scale is 450 ms,
   crop/radius 375 ms; scale/crop use the two-segment path recorded in IconOverlayView.
   Alpha waits 25 ms and fades for 50/40 ms.
7. When alpha is strictly below .13, replay ownership is claimed once. ActivityOptions scale-up
   uses the live artwork rectangle in the same root coordinate space. API 33+ requests icon
   splash style. The host's launchFromTransition callback remains authoritative for flags, source bounds,
   shortcuts, profiles, analytics, permission behavior and exception handling.
8. Duplicate taps are consumed while this open owns the gesture. An issued open continues through
   pause while Home is visible; stop, configuration, destruction and completion remove overlays,
   release bitmaps and restore exact alpha. Non-launch pause interruptions still cancel visuals.
   A replay is never repeated after an ambiguous vendor result.

## Nova gesture-return contract

The 2026-10-03 reverse-endpoint artwork animation was rejected and is not Nova's method.
Nova 8.9.2 ALSO implements GestureNavContract through o9/p1.onNewIntent,
FloatingSurfaceView, x7/u and ea/a. The earlier slide-down-only map missed this branch.
The grid animator must not synthesize the selected icon's return trajectory.

Ordered protocol (local Android parcel/Binder messages, not a network protocol):

| Step | Trigger / owner | Exact data and behavior |
| --- | --- | --- |
| 1 | Gesture system starts HOME | Intent bundle gesture_nav_contract_v1 contains android.intent.extra.COMPONENT_NAME (ComponentName), android.intent.extra.USER (UserHandle), android.intent.extra.REMOTE_CALLBACK (Message with non-null replyTo). |
| 2 | Launcher consumes request | API 30+ only; remove the extra exactly once; retain component/user/callback, not a guessed last-launched app. Nova also has a preference gate. |
| 3 | Resolve settled target | LauncherHost.findTransitionTarget resolves the supplied component and user. The host owns app/widget metadata and dock/workspace priority; core reads no private model fields. |
| 4 | Record icon surface | Save source visibility; record only its artwork bounds in Picture; hide the real source; translucent SurfaceView, setZOrderOnTop(true), hardware Canvas; update destination on global layout. |
| 5 | Attach by API branch | Nova uses an application-panel WindowManager view on API30 (copied window params, token null, type1000, flags OR0x40018). API31+ adds a child to the host root. LauncherHost.createTransitionSurface supplies parent-native parameters and any custom inset policy. |
| 6 | Reply to system | Copy the original Message (preserves its routing token); set Bundle with gesture_nav_contract_icon_position (RectF), gesture_nav_contract_surface_control (SurfaceControl), gesture_nav_contract_finish_callback (Message). Send via original replyTo. Resend when surface is created / destination changes. |
| 7 | System owns movement | FallbackSwipeHandler accepts the destination and SurfaceControl and moves the actual task plus supplied icon surface. The launcher does not run a reverse icon animator or invent its duration. |
| 8 | Finish and cleanup | System sends finish Message what=0. Nova restores source and removes surface after two display-frame intervals. Nova also has focus-gain400ms cleanup for SDK<32 or SDK32 with PREVIEW_SDK_INT<1; pause/destroy/touch cancel old surfaces. |
| 9 | Combine with iLauncher | Exclude system-owned destination from grid/tray snapshots. Hold surface cleanup while the selected icon's parent tray is hidden; after sibling cleanup, restore once and observe Nova's two-frame removal delay. This is ownership coordination, not an extra animation. |

No encryption is added: the platform-supplied Messenger is the local Binder reply channel.
Preserve the incoming Message routing identity; gate completion against the active session so stale
callbacks cannot remove a newer surface. Never upload component/profile/message/pixels in diagnostics.

Variants: malformed/absent contract, API<30, no visible matching app/widget, model not ready,
surface not yet valid, remote callback failure, old-API panel failure, new gesture during cleanup.
Without a valid contract/target, retain Nova's mapped window-style fallback plus the existing grid,
and report a bounded reason. Do not substitute a large drawn icon or claim a system-window shrink.

The open path retains the verified f10/d0.G + az/n + v00/d tracks and handoff geometry.
The existing iLauncher ring tables and dock clock remain unchanged; tray-only cleanup is held until its parent is restored. Surface/finish integration
was included in the 2026-10-03 compiled clone; real-device gesture parity is not established.

Primary cross-checks:
- https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/GestureNavContract.java
- https://android.googlesource.com/platform/packages/apps/Launcher3/+/master/src/com/android/launcher3/views/FloatingSurfaceView.java
- https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/android13-qpr3-s5-release/quickstep/src/com/android/quickstep/FallbackSwipeHandler.java

## Ordered flow: unlock and return

1. Application installation registers USER_PRESENT and SCREEN_OFF. USER_PRESENT both stores a
   five-second token and immediately notifies the weak launcher owners.
2. Resume/focus/model-ready/new-Home-intent converge on one eligibility gate: resumed, focused,
   interactive, unlocked, actual root attached, model binding complete.
3. Cold entry requires a HOME intent. Unlock requires an actual Home scene; it never substitutes
   selected-icon growth. Loading does not consume its token. The host provides model-ready notification.
4. Layout readiness must agree across observations 48 ms apart. Reference entry delays are
   60 ms for unlock/cold and 35 ms for return. A 1500 ms optional-visual deadline avoids retaining
   an unrendered root forever; this is not a terminal app failure.
5. Capture the currently resulting surface after the vendor's full lifecycle body, not the old
   remembered folder/library. With a real gesture contract, exclude the system-selected icon and
   supply Nova's surface handshake while grid snapshots animate inward. Without it, do not synthesize
   a reverse icon. Unlock consumes its token only here and has no gesture surface.
6. The non-contract fallback window selectors inherit platform Animation.Activity and override close/task-to-front/
   wallpaper-open pairs: opaque Home entry plus top-layer 225 ms foreground translation
   0 to 109.99756%, accelerate_quad, fillBefore/fillAfter/fillEnabled.
7. At scene endpoint restore real content, matching iLauncher's early restoration convention.

## Grid and crop arithmetic

Reference group delays: 0,30,45,45,45,45,45 ms.
Durations: 350,750,1070,1310,1470,1550,1550 ms.
Remaining displacement is (1-clampedProgress)^8. Scale is 1+(6-ring)*remaining.
The reference endpoint is last-populated-group delay+duration-500 (615/855/1015 ms for rings2/3/4).
Dock and indicator share a translated strip interval beginning at300 ms, traveling by their combined
height in bottom-aligned iLauncher; the clone includes floating margins to start at the viewport edge. The selected dock
icon is excluded from that bitmap so it is not rendered twice.

Ring indices use actual cells, not pixel distance. 4x4/4x5 anchor row1;4x6 row2,
between middle columns;5x4 row1/column2 with its distinct vertical anchor.
Other grids/list surfaces use the documented middle-cell/ranked-center extension.
Sparse layouts whose endpoint would leave no dock interval use650 ms. These extensions are
product choices, not claims that iLauncher supported every target layout.
Open reverses the same scene on a normalized450 ms clock.

Adaptive crop grows from a short-side square to screen aspect. Scale covers the shorter screen
dimension, not an oversized maximum-dimension square. Background stretches uniformly to cover
the changing long axis; foreground retains normalized layer bounds with centered long-axis
offset. Radius independently approaches32dp (0 in multi-window). Nonadaptive artwork does not
receive adaptive clipping. Target artwork bounds and every ancestor matrix/scroll supply origin.

## Host integration

Implement LauncherHost on the Activity and forward the lifecycle callbacks described in
[Host API](HOST_API.md). `interceptLaunch` accepts any Activity implementing that interface;
`shouldAnimateLaunch` controls host-specific exclusions. The host supplies safe launch and consumes
one-shot ActivityOptions during replay. No library class replacement or vendor patcher is required.

An issued external launch or a real gesture contract permits return preparation. Contract handling
does not wait for Home focus. With grid animation disabled, a valid contract is still answered.
Activity ownership remains weak, and no adapter object separately retains the Activity.

## Diagnostics and privacy

The runtime automatically POSTs bounded transition codes to:
https://204-168-163-118.sslip.io/imelog/launcher-combined

Only event code, generation and SDK are sent; no app identities, intents, pixels, credentials
or global logs. A single worker uses a validated INTERNET network, 3-second timeouts,
a64-item drop-oldest queue and automatic15-second retries. The queue is in memory: process
death can lose queued diagnostics. There is no crash-reporting claim.
The collector is POST-only publicly; Codex reads its on-box log. No user log courier is required.

## Required actual-device test matrix (after authorized compilation)

For each case, interact with the real launcher and inspect recorded frames plus automatic events:

1. On gesture Home, confirm gesture_contract_received, gesture_surface_reply and
   gesture_system_finished arrive in automatic diagnostics; verify the real app window lands
   on the supplied icon while siblings zoom. Check non-contract navigation remains a native fallback.
   Test both API30 panel and API31+ child routes, a late finish and a second gesture before cleanup.
2. Cold HOME while binding, then first unlock; USER_PRESENT before and after resume; repeated
   lock/unlock; unlock into another foreground app must not flash Home.
2. Workspace corner/center icons, all supported rows, sparse page, dock and folder preview.
3. Open folder then launch; library/search launch; return after vendor resets the visible surface.
4. Adaptive and nonadaptive artwork, badges/labels, portrait/landscape/multi-window, scrolled parents.
5. Rapid double tap, tap during inward flight, HOME during open, screen-off, configuration and destroy.
6. Animation scale0: native launch, no invisible icons or retained overlays.
7. CALL permission missing, disabled/uninstalled app, work profile and shortcut branches.
8. Back, HOME button and gesture HOME: confirm which OEM window selector actually runs.
9. Install coexistence, provider identity, all three splits, PairIP/vendor licensing and services.

Host tests prove source/patch contracts only. The 2026-10-03 Android build type-checked the
runtime and assembled Smali/resources. No UI, rendering, installation or licensing success is implied.
