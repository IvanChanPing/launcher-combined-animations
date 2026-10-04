package example.grid;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.ivanchan.launcher.combined.transitions.LauncherHost;
import com.ivanchan.launcher.combined.transitions.LauncherScene;

/** Source-contract fixture, not an installed application or runtime test.
 * Purpose: Represent an unrelated image-grid launcher without vendor types.
 * Invocation: Syntax/API checks. Contract: Test supplies views; launches are deliberately declined.
 * Verification: All required host methods are implemented; no Android execution is claimed.
 */
public final class GridHost extends Activity implements LauncherHost {
    public ViewGroup root;
    public ImageView cell;
    public ComponentName component;
    public UserHandle profile;
    public ViewGroup transitionRoot() { return root; }
    public boolean isTransitionBinding() { return cell == null; }
    public LauncherScene captureTransitionScene(View selected) {
        return new LauncherScene(root, root, true, 120f, 80f, 48f).addIcon(cell, 0, 0, 4, 5);
    }
    public RectF transitionIconBounds(View icon) { return new RectF(0, 0, icon.getWidth(), icon.getHeight()); }
    public Drawable transitionIconDrawable(View icon) { return ((ImageView) icon).getDrawable(); }
    public boolean launchFromTransition(View icon, Intent intent, Object item) { return false; }
    public View findTransitionTarget(LauncherScene scene, ComponentName requested, UserHandle user) {
        return requested.equals(component) && user.equals(profile) ? cell : null;
    }
}
