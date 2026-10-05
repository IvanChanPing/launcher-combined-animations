package com.ivanchan.launcher.combined.transitions;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Purpose: Connect the animation runtime to any View-based Android launcher.
 * Invocation: Implement on the launcher Activity and forward its lifecycle to the controller.
 * Contract: Main-thread callbacks; no launcher class names, reflection or model types in core.
 * The Activity remains weakly referenced. Return fresh scene data for each capture; keep app,
 * shortcut, profile, permission and launch policy in the host's existing safe-launch function.
 * Verification: API/dependency source tests and Java parsing; device integration is separate.
 * Visual: Host supplies the real icon artwork, grid anchor, dock views and return destination.
 */
public interface LauncherHost {
    ViewGroup transitionRoot();
    boolean isTransitionBinding();
    LauncherScene captureTransitionScene(View selected) throws ReflectiveOperationException;
    RectF transitionIconBounds(View icon) throws ReflectiveOperationException;
    Drawable transitionIconDrawable(View icon) throws ReflectiveOperationException;
    /**
     * Purpose: Match the selected card to this launcher's rendered icon corners.
     * Invocation: Open/return card construction. Contract: Fraction of artwork width/height,
     * finite from 0 to .5; 0 is square, .25 rounded square, .5 circular for square artwork.
     * Supply the renderer's actual value; the default leaves unknown artwork square.
     * Verification: Endpoint math and source checks; host phone test still required.
     * Visual: Card starts and lands rounded, with flat corners at full screen.
     */
    default float transitionIconCornerFraction(View icon) { return 0f; }
    boolean launchFromTransition(View icon, Intent intent, Object item) throws ReflectiveOperationException;
    View findTransitionTarget(LauncherScene scene, ComponentName component, UserHandle user)
            throws ReflectiveOperationException;

    /** Purpose: Keep launch eligibility in the host instead of hardcoded icon/view classes.
     * Invocation: Before capture. Contract: Return false for unsupported items or host exclusions.
     * Verification: Two distinct host implementations exercise the API in source fixtures.
     */
    default boolean shouldAnimateLaunch(View icon, Intent intent, Object item) { return true; }

    /** Purpose: Supply icon-only bounds, or full widget bounds when the host returns a widget.
     * Invocation: Before gesture Picture capture. Contract: Coordinates are local to target.
     * Verification: Call-site checks ensure core never reads a host model or icon accessor.
     */
    default RectF transitionReturnBounds(View target) throws ReflectiveOperationException {
        return transitionIconBounds(target);
    }

    /** Purpose: Obtain layout parameters from the actual parent without vendor attributes.
     * Invocation: Gesture return before attachment. Contract: Return a fresh unattached view.
     * Override for custom parent inset policy; preserve the parent's native LayoutParams type.
     * Verification: Bundled XML has only Android attributes; custom hosts require UI testing.
     */
    default NovaGestureSurface createTransitionSurface(Activity activity, ViewGroup parent) {
        return (NovaGestureSurface) LayoutInflater.from(activity)
                .inflate(R.layout.combined_gesture_surface, parent, false);
    }
}
