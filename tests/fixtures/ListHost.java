package example.list;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;
import com.ivanchan.launcher.combined.transitions.LauncherHost;
import com.ivanchan.launcher.combined.transitions.LauncherScene;

/** Source-contract fixture for a different package, ordinary row Views and a private row model.
 * Invocation: Syntax/API checks. Contract: Test supplies rows; it does not launch external apps.
 * Verification: Same public API as GridHost, different view/model and one-column geometry.
 */
public final class ListHost extends Activity implements LauncherHost {
    public static final class Row {
        public View view;
        public Drawable icon;
        public ComponentName component;
        public UserHandle user;
    }
    public ViewGroup root;
    public List<Row> rows;
    public ViewGroup transitionRoot() { return root; }
    public boolean isTransitionBinding() { return rows == null; }
    public LauncherScene captureTransitionScene(View selected) {
        LauncherScene scene = new LauncherScene(root, root, true, 80f, 60f, 48f);
        for (int i = 0; i < rows.size(); i++) scene.addIcon(rows.get(i).view, 0, i, 1, rows.size());
        return scene;
    }
    public RectF transitionIconBounds(View icon) { return new RectF(8f, 8f, 40f, 40f); }
    public Drawable transitionIconDrawable(View icon) {
        for (Row row : rows) if (row.view == icon) return row.icon;
        return null;
    }
    public boolean launchFromTransition(View icon, Intent intent, Object item) { return false; }
    public View findTransitionTarget(LauncherScene scene, ComponentName requested, UserHandle user) {
        for (Row row : rows) if (requested.equals(row.component) && user.equals(row.user)) return row.view;
        return null;
    }
}
