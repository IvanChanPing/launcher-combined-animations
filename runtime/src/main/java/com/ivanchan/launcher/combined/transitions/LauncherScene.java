package com.ivanchan.launcher.combined.transitions;

import android.graphics.Matrix;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Purpose: Describe one launcher surface without its private model or LayoutParams fields.
 * Invocation: LauncherHost.captureTransitionScene builds a fresh instance for each transition.
 * Contract: Pixel anchor/cell height are root-local; cells use zero-based grid coordinates.
 * Supply each grid view once and dock/indicator parents separately; never mutate during playback.
 * Verification: Input validation and host-independent source tests; rendering needs a device.
 * Visual: Whole icon cells fly around the supplied anchor; strips enter from the bottom edge.
 */
public final class LauncherScene {
    public final ViewGroup root;
    public final View content;
    public final boolean home;
    final List<Item> items = new ArrayList<>();
    final List<View> strips = new ArrayList<>();
    final float anchorX;
    final float anchorY;
    final float cellHeight;
    int maxRing;
    boolean sourceInStrip;

    public LauncherScene(ViewGroup root, View content, boolean home,
            float anchorX, float anchorY, float cellHeight) {
        this.root = Objects.requireNonNull(root);
        this.content = Objects.requireNonNull(content);
        if (!Float.isFinite(anchorX) || !Float.isFinite(anchorY)
                || !Float.isFinite(cellHeight) || cellHeight <= 0f)
            throw new IllegalArgumentException("Invalid scene geometry");
        this.home = home; this.anchorX = anchorX; this.anchorY = anchorY; this.cellHeight = cellHeight;
        LauncherGeometry.toRoot(content, root);
    }

    public LauncherScene addIcon(View view, int column, int row, int columns, int rows) {
        if (columns <= 0 || rows <= 0 || column < 0 || column >= columns || row < 0 || row >= rows)
            throw new IllegalArgumentException("Invalid cell coordinates");
        for (Item item : items)
            if (item.view == view) throw new IllegalArgumentException("Duplicate scene icon");
        if (isInStrip(view)) throw new IllegalArgumentException("Icon already belongs to a strip");
        Item item = new Item(Objects.requireNonNull(view), root,
                MotionMath.ring(column, row, columns, rows));
        items.add(item); maxRing = Math.max(maxRing, item.ring);
        return this;
    }

    public LauncherScene addStrip(View view) {
        Objects.requireNonNull(view);
        LauncherGeometry.toRoot(view, root);
        for (View strip : strips)
            if (inside(view, strip) || inside(strip, view))
                throw new IllegalArgumentException("Overlapping scene strips");
        for (Item item : items)
            if (inside(item.view, view)) throw new IllegalArgumentException("Strip contains a grid icon");
        strips.add(view);
        return this;
    }

    public boolean isInStrip(View view) {
        for (View strip : strips) if (inside(view, strip)) return true;
        return false;
    }

    private static boolean inside(View child, View parent) {
        for (View v = child; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null)
            if (v == parent) return true;
        return false;
    }

    static final class Item {
        final View view;
        final Matrix matrix;
        final int ring;
        Item(View view, ViewGroup root, int ring) {
            this.view = view; this.matrix = LauncherGeometry.toRoot(view, root); this.ring = ring;
        }
    }
}
