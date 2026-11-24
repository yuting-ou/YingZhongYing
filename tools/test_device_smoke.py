import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

import device_smoke


class DeviceSmokeUnitTest(unittest.TestCase):
    def test_installed_base_path_parses_pm_path_output(self):
        output = "package:/data/app/~~abc/com.example/base.apk\n"
        self.assertEqual("/data/app/~~abc/com.example/base.apk", device_smoke.installed_base_path(output))

    def test_sha256_file_matches_known_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "app.apk"
            path.write_bytes(b"vlc-apk")
            self.assertEqual(
                hashlib.sha256(b"vlc-apk").hexdigest(),
                device_smoke.sha256_file(path),
            )

    def test_tap_center_parses_uiautomator_bounds(self):
        self.assertEqual((20, 30), device_smoke.tap_center("[10,20][30,40]"))

    def test_tap_center_rejects_malformed_bounds(self):
        with self.assertRaises(ValueError):
            device_smoke.tap_center("not-bounds")

    def test_parse_ui_xml_extracts_clickable_nodes(self):
        xml = """<?xml version='1.0' encoding='UTF-8' standalone='yes'?>
        <hierarchy>
          <node text="添加照片" content-desc="add" clickable="true"
                bounds="[12,34][108,130]" />
          <node text="删原图 关" clickable="false" bounds="[0,0][10,10]" />
        </hierarchy>"""
        nodes = device_smoke.parse_ui_xml(xml)
        self.assertEqual(2, len(nodes))
        self.assertEqual("添加照片", nodes[0]["text"])
        self.assertEqual((60, 82), device_smoke.tap_center(nodes[0]["bounds"]))

    def test_find_node_matches_exact_text_or_content_description(self):
        nodes = [
            {"text": "添加", "content-desc": "", "clickable": "true", "bounds": "[0,0][10,10]"},
            {"text": "", "content-desc": "添加照片", "clickable": "true", "bounds": "[20,20][40,40]"},
        ]
        self.assertIs(nodes[1], device_smoke.find_node(nodes, content_desc="添加照片"))
        self.assertIs(nodes[0], device_smoke.find_node(nodes, text="添加"))
        self.assertIsNone(device_smoke.find_node(nodes, text="不存在"))

    def test_find_node_containing_matches_counted_button_label(self):
        nodes = [{"text": "开始转换（3）", "content-desc": "", "bounds": "[0,0][10,10]"}]
        self.assertIs(nodes[0], device_smoke.find_node_containing(nodes, "开始转换"))

    def test_delete_original_must_be_explicitly_off(self):
        self.assertTrue(device_smoke.delete_original_is_off("输出 DCIM/Camera 删原图 关 设置 ›"))
        self.assertFalse(device_smoke.delete_original_is_off("输出 DCIM/Camera 删原图 开 设置 ›"))
        self.assertFalse(device_smoke.delete_original_is_off("设置 ›"))

    def test_permission_state_parses_package_dumpsys(self):
        permission = "android.permission.ACCESS_MEDIA_LOCATION"
        self.assertIs(True, device_smoke.parse_permission_dumpsys(
            "{}: granted=true, flags=[]".format(permission), permission))
        self.assertIs(False, device_smoke.parse_permission_dumpsys(
            "{}: granted=false, flags=[]".format(permission), permission))
        self.assertIsNone(device_smoke.parse_permission_dumpsys("no permission entry", permission))

    def test_output_path_excludes_smoke_source_directory(self):
        paths = [
            "/storage/emulated/0/DCIM/_vlc_smoke_1/IMG_1.jpg",
            "/storage/emulated/0/DCIM/Camera/IMG_1.jpg",
        ]
        self.assertEqual(
            "/storage/emulated/0/DCIM/Camera/IMG_1.jpg",
            device_smoke.pick_output_path(paths, "/sdcard/DCIM/_vlc_smoke_1"),
        )

    def test_fixture_search_prefix_matches_all_three_numeric_names(self):
        self.assertEqual("IMG_123", device_smoke.fixture_search_prefix("IMG_123450"))

    def test_fixture_display_names_are_derived_from_numeric_prefix(self):
        self.assertEqual(
            ["IMG_123450.jpg", "IMG_123451.jpg", "IMG_123452.jpg"],
            device_smoke.fixture_display_names("IMG_123450"),
        )

    def test_media_display_names_are_extracted_from_content_query(self):
        output = "Row: 0 _display_name=IMG_123.jpg\nRow: 1 _display_name=IMG_124.jpg\n"
        self.assertEqual(
            ["IMG_123.jpg", "IMG_124.jpg"],
            device_smoke.media_display_names(output),
        )

    def test_window_dump_ignores_historical_notification_shade(self):
        dump = """WindowStateAnimator{NotificationShade}
        isKeyguardShowing=false
        mCurrentFocus=Window{abc com.android.launcher3/.Launcher}"""
        self.assertFalse(device_smoke.device_is_locked(dump))

    def test_window_dump_detects_current_shade_or_showing_keyguard(self):
        self.assertTrue(device_smoke.device_is_locked(
            "isKeyguardShowing=false\nmCurrentFocus=Window{abc NotificationShade}"))
        self.assertTrue(device_smoke.device_is_locked(
            "isKeyguardShowing=true\nmCurrentFocus=Window{abc com.android.launcher3/.Launcher}"))

    def test_dry_run_does_not_require_a_real_apk(self):
        self.assertEqual(
            0,
            device_smoke.main([
                "--dry-run",
                "--serial", "emulator-5554",
                "--apk", "/tmp/nonexistent.apk",
            ]),
        )

    def test_dry_run_accepts_skip_install(self):
        self.assertEqual(
            0,
            device_smoke.main([
                "--dry-run", "--skip-install",
                "--serial", "emulator-5554",
                "--apk", "/tmp/nonexistent.apk",
            ]),
        )

    def test_dry_run_accepts_skip_permission_revoke(self):
        self.assertEqual(
            0,
            device_smoke.main([
                "--dry-run", "--skip-permission-revoke",
                "--serial", "emulator-5554",
                "--apk", "/tmp/nonexistent.apk",
            ]),
        )

    def test_missing_serial_is_rejected(self):
        with self.assertRaises(device_smoke.SmokeError):
            device_smoke.main(["--apk", "/tmp/app.apk"])


if __name__ == "__main__":
    unittest.main()
