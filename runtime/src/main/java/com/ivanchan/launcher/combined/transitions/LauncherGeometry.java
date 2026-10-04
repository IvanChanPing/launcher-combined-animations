package com.ivanchan.launcher.combined.transitions;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

/** Purpose: Map ordinary Android Views into the shared animation root.
 * Invocation: Scene capture, icon overlay and gesture destination updates.
 * Contract: No launcher-specific types; include every ancestor transform and scroll offset.
 * Verification: Preserved mapping algorithm, dependency tests and syntax parsing.
 */
public final class LauncherGeometry {
    private LauncherGeometry() { }

    public static Matrix toRoot(View child, ViewGroup root) {
        Matrix result = new Matrix();
        View view = child;
        while (view != root) {
            if (!(view.getParent() instanceof View))
                throw new IllegalArgumentException("Detached transition source");
            View parent = (View) view.getParent();
            Matrix step = new Matrix(view.getMatrix());
            step.postTranslate(view.getLeft() - parent.getScrollX(),
                    view.getTop() - parent.getScrollY());
            result.postConcat(step);
            view = parent;
        }
        return result;
    }

    public static RectF bounds(View child, ViewGroup root) {
        RectF rect = new RectF(0, 0, child.getWidth(), child.getHeight());
        toRoot(child, root).mapRect(rect);
        return rect;
    }

    public static boolean visible(View view) {
        if (!view.isShown() || !view.isLaidOut() || view.getWidth() <= 0 || view.getHeight() <= 0)
            return false;
        for (View v = view; v != null; v = v.getParent() instanceof View
                ? (View) v.getParent() : null)
            if (v.getAlpha() <= 0f) return false;
        return view.getGlobalVisibleRect(new Rect());
    }
}
