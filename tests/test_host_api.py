"""Source architecture checks, not Android compilation or runtime/rendering tests."""
import hashlib
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'runtime/src/main/java/com/ivanchan/launcher/combined/transitions'


class HostApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sources = {p.name: p.read_text() for p in JAVA.glob('*.java')}

    def test_no_vendor_or_reflection_dependency(self):
        for name, source in self.sources.items():
            for banned in ('com.android.launcher3', 'com.iphonelauncher', 'LauncherAccess',
                           'Class.forName', 'getField(', 'getMethod(', 'java.lang.reflect'):
                self.assertNotIn(banned, source, name)

    def test_public_host_api_and_distinct_fixtures(self):
        required = ('transitionRoot', 'isTransitionBinding', 'captureTransitionScene',
                    'transitionIconBounds', 'transitionIconDrawable',
                    'launchFromTransition', 'findTransitionTarget')
        api = self.sources['LauncherHost.java']
        self.assertIn('public interface LauncherHost', api)
        for fixture in ('GridHost.java', 'ListHost.java'):
            source = (ROOT / 'tests/fixtures' / fixture).read_text()
            self.assertIn('extends Activity implements LauncherHost', source)
            for method in required:
                self.assertRegex(api, r'\b' + method + r'\(')
                self.assertRegex(source, r'\b' + method + r'\(')
        self.assertIn('ImageView', (ROOT / 'tests/fixtures/GridHost.java').read_text())
        self.assertIn('List<Row>', (ROOT / 'tests/fixtures/ListHost.java').read_text())

    def test_controller_uses_host_callbacks(self):
        source = self.sources['CombinedTransitionController.java']
        for method in ('transitionRoot', 'isTransitionBinding', 'captureTransitionScene',
                       'shouldAnimateLaunch', 'launchFromTransition', 'findTransitionTarget',
                       'transitionIconBounds', 'transitionIconDrawable', 'transitionReturnBounds'):
            self.assertIn('.' + method + '(', source)
        self.assertIn('activity instanceof LauncherHost', source)
        self.assertIn('WeakReference<Activity>', source)
        self.assertNotRegex(source, r'private\s+(?:final\s+)?LauncherHost\s+\w+;')

    def test_renderers_receive_host_geometry(self):
        icon = self.sources['IconOverlayView.java']
        surface = self.sources['NovaGestureSurface.java']
        self.assertIn('RectF artworkBounds, Drawable original', icon)
        self.assertIn('start = new RectF(artworkBounds)', icon)
        self.assertIn('artworkBounds.roundOut(view.localBounds)', surface)
        self.assertIn('.createTransitionSurface(activity, host)', surface)
        self.assertNotIn('getIdentifier(', surface)

    def test_scene_is_public_and_checks_input(self):
        source = self.sources['LauncherScene.java']
        for text in ('public final class LauncherScene', 'public LauncherScene addIcon(',
                     'public LauncherScene addStrip(', 'public boolean isInStrip(',
                     'Invalid cell coordinates', 'Duplicate scene icon',
                     'Overlapping scene strips', 'Float.isFinite', 'LauncherGeometry.toRoot'):
            self.assertIn(text, source)

    def test_surface_resource_has_no_custom_attributes(self):
        for path in (ROOT / 'runtime/src/main/res/layout/combined_gesture_surface.xml',
                     ROOT / 'integration/res/layout/combined_gesture_surface.xml'):
            tree = ET.parse(path).getroot()
            for name in tree.attrib:
                self.assertTrue(name.startswith('{http://schemas.android.com/apk/res/android}'))
        self.assertIn('R.layout.combined_gesture_surface', self.sources['LauncherHost.java'])

    def test_motion_and_wire_contract_unchanged(self):
        expected = {
            'MotionMath.java': 'd045f4dca4ea28da0b9317f8b8f742477baab26814d9243b010dad8602dd649e',
            'NovaGestureContract.java': 'b7e1dae2a67befaa24f883a7f4fafad2360ef65433721e9a5144eddfc158c2cf',
        }
        for name, digest in expected.items():
            self.assertEqual(digest, hashlib.sha256((JAVA / name).read_bytes()).hexdigest())

    def test_all_lifecycle_entry_points_remain(self):
        source = self.sources['CombinedTransitionController.java']
        for name in ('onLauncherCreated', 'onLauncherStarted', 'onLauncherResumed',
                     'onLauncherFocused', 'onLauncherModelReady', 'recordHomeIntent',
                     'onLauncherNewIntent', 'onLauncherPaused', 'onLauncherStopped',
                     'onLauncherConfigurationChanged', 'onLauncherDestroyed'):
            self.assertIn('public static void ' + name + '(', source)
        self.assertIn('old.removeAllUpdateListeners(); old.removeAllListeners(); old.cancel();', source)
        self.assertIn('source.setAlpha(sourceAlpha)', source)


if __name__ == '__main__':
    unittest.main()
