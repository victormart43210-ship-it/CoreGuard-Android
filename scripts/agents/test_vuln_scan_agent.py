#!/usr/bin/env python3
"""Regression tests for VULN-IMPLICIT launcher exemption (no pytest required)."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from vuln_scan_agent import (  # noqa: E402
    AgentReport,
    is_safe_launcher_activity,
    iter_unprotected_exports,
    scan_unprotected_components,
)


LAUNCHER_ACTIVITY = """
<manifest>
  <application>
    <activity android:name=".MainActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>
"""

EXPORTED_NO_PERMISSION = """
<manifest>
  <application>
    <activity android:name=".DeepLinkActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.VIEW" />
      </intent-filter>
    </activity>
  </application>
</manifest>
"""

EXPORTED_WITH_PERMISSION = """
<manifest>
  <application>
    <service
        android:name=".elite.ScamGuardNotificationListener"
        android:exported="true"
        android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
      <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
      </intent-filter>
    </service>
  </application>
</manifest>
"""

MAIN_WITHOUT_LAUNCHER = """
<manifest>
  <application>
    <activity android:name=".InternalEntry" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
      </intent-filter>
    </activity>
  </application>
</manifest>
"""


class LauncherExportExemptionTest(unittest.TestCase):
    def test_launcher_activity_is_safe(self):
        self.assertTrue(
            is_safe_launcher_activity(
                "activity",
                """
                <activity android:name=".MainActivity" android:exported="true">
                  <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                  </intent-filter>
                </activity>
                """,
            )
        )

    def test_main_without_launcher_is_not_safe(self):
        self.assertFalse(
            is_safe_launcher_activity(
                "activity",
                """
                <activity android:name=".InternalEntry" android:exported="true">
                  <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                  </intent-filter>
                </activity>
                """,
            )
        )

    def test_service_never_treated_as_launcher(self):
        self.assertFalse(
            is_safe_launcher_activity(
                "service",
                """
                <service android:name=".S" android:exported="true">
                  <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                  </intent-filter>
                </service>
                """,
            )
        )

    def test_iter_skips_launcher_only(self):
        hits = list(iter_unprotected_exports(LAUNCHER_ACTIVITY))
        self.assertEqual(hits, [])

    def test_iter_flags_exported_activity_without_permission(self):
        hits = list(iter_unprotected_exports(EXPORTED_NO_PERMISSION))
        self.assertEqual(len(hits), 1)
        self.assertEqual(hits[0][1], "activity")

    def test_iter_skips_exported_with_permission(self):
        hits = list(iter_unprotected_exports(EXPORTED_WITH_PERMISSION))
        self.assertEqual(hits, [])

    def test_iter_flags_main_without_launcher_category(self):
        hits = list(iter_unprotected_exports(MAIN_WITHOUT_LAUNCHER))
        self.assertEqual(len(hits), 1)

    def test_scan_real_style_manifest_tree(self):
        combined = """
        <manifest>
          <application>
            <activity android:name=".MainActivity" android:exported="true">
              <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
              </intent-filter>
            </activity>
            <activity android:name=".DeepLinkActivity" android:exported="true">
              <intent-filter>
                <action android:name="android.intent.action.VIEW" />
              </intent-filter>
            </activity>
          </application>
        </manifest>
        """
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = root / "app" / "src" / "main" / "AndroidManifest.xml"
            manifest.parent.mkdir(parents=True)
            manifest.write_text(combined)
            report = AgentReport()
            scan_unprotected_components(root, report)
            implicit = [f for f in report.findings if f.rule == "VULN-IMPLICIT"]
            self.assertEqual(len(implicit), 1)
            self.assertIn("DeepLinkActivity", implicit[0].message)


if __name__ == "__main__":
    unittest.main()
