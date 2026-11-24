#!/usr/bin/env python3
import argparse
import glob
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "com.yutingou.yingzhongying"
ACTIVITY = "com.vliveconvert.app.MainActivity"
MIN_FREE_KB = 2 * 1024 * 1024


class SmokeError(RuntimeError):
    pass


def tap_center(bounds: str) -> Tuple[int, int]:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not match:
        raise ValueError("invalid uiautomator bounds: {!r}".format(bounds))
    x1, y1, x2, y2 = (int(value) for value in match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def parse_ui_xml(xml: str) -> List[Dict[str, str]]:
    upper = xml.upper()
    if "<!DOCTYPE" in upper or "<!ENTITY" in upper:
        raise SmokeError("UI dump 包含 DTD/ENTITY，拒绝解析")
    root = ET.fromstring(xml)
    nodes = []
    for node in root.iter("node"):
        attrs = {key: node.attrib.get(key, "") for key in (
            "text", "content-desc", "clickable", "bounds", "resource-id", "class"
        )}
        if attrs["text"] or attrs["content-desc"]:
            nodes.append(attrs)
    return nodes


def find_node(
    nodes: Sequence[Dict[str, str]],
    text: Optional[str] = None,
    content_desc: Optional[str] = None,
) -> Optional[Dict[str, str]]:
    for node in reversed(list(nodes)):
        if text is not None and node.get("text") == text:
            return node
        if content_desc is not None and node.get("content-desc") == content_desc:
            return node
    return None


def find_node_containing(
    nodes: Sequence[Dict[str, str]],
    text: str,
) -> Optional[Dict[str, str]]:
    for node in reversed(list(nodes)):
        if text in node.get("text", "") or text in node.get("content-desc", ""):
            return node
    return None


def delete_original_is_off(ui_text: str) -> bool:
    return "删原图 关" in (ui_text or "") and "删原图 开" not in (ui_text or "")


class Adb:
    def __init__(self, serial: str):
        self.serial = serial
        candidate = Path.home() / "Library/Android/sdk/platform-tools/adb"
        self.binary = str(candidate) if candidate.exists() else shutil.which("adb")
        if not self.binary:
            raise SmokeError("找不到 adb；请设置 ANDROID_HOME 或把 adb 加入 PATH")

    def run(self, *args: str, check: bool = True) -> subprocess.CompletedProcess:
        command = [self.binary, "-s", self.serial] + list(args)
        result = subprocess.run(command, text=True, capture_output=True)
        if check and result.returncode != 0:
            raise SmokeError("命令失败：{}\n{}".format(" ".join(command), result.stderr.strip()))
        return result

    def shell(self, command: str, check: bool = True) -> subprocess.CompletedProcess:
        return self.run("shell", command, check=check)

    def devices(self) -> List[str]:
        result = subprocess.run([self.binary, "devices"], text=True, capture_output=True)
        if result.returncode != 0:
            raise SmokeError("adb devices 失败：{}".format(result.stderr.strip()))
        return [line.split()[0] for line in result.stdout.splitlines()[1:]
                if line.strip() and line.split()[1] == "device"]

    def dump_ui(self) -> List[Dict[str, str]]:
        remote = "/sdcard/_vlc_smoke_ui.xml"
        last_error = None
        for _ in range(3):
            self.shell("rm -f {}".format(remote), check=False)
            dumped = self.shell("uiautomator dump {}".format(remote), check=False)
            if dumped.returncode != 0:
                last_error = "uiautomator dump 失败：{}".format(dumped.stderr.strip())
                time.sleep(0.5)
                continue
            xml = self.run("exec-out", "cat", remote).stdout
            if xml.strip():
                try:
                    return parse_ui_xml(xml)
                except (ET.ParseError, SmokeError) as exc:
                    last_error = "UI dump 不是合法 XML：{}".format(exc)
            else:
                last_error = "UI dump 为空"
            time.sleep(0.5)
        raise SmokeError(last_error or "UI dump 失败")

    def wait_text(self, text: str, timeout: float = 120.0) -> List[Dict[str, str]]:
        deadline = time.monotonic() + timeout
        last_nodes = []
        while time.monotonic() < deadline:
            last_nodes = self.dump_ui()
            joined = "\n".join(
                (node.get("text", "") + "\n" + node.get("content-desc", "")).strip()
                for node in last_nodes
            )
            if text in joined:
                return last_nodes
            time.sleep(0.5)
        raise SmokeError("等待 UI 文本超时：{!r}".format(text))

    def tap_text(self, text: str, timeout: float = 30.0) -> None:
        nodes = self.wait_text(text, timeout)
        node = (find_node(nodes, text=text) or find_node(nodes, content_desc=text) or
                find_node_containing(nodes, text))
        if node is None:
            raise SmokeError("找不到可点击节点：{!r}".format(text))
        x, y = tap_center(node["bounds"])
        self.shell("input tap {} {}".format(x, y))

    def tap_desc(self, content_desc: str, timeout: float = 30.0) -> None:
        nodes = self.wait_text(content_desc, timeout)
        node = find_node(nodes, content_desc=content_desc)
        if node is None:
            raise SmokeError("找不到 content-desc：{!r}".format(content_desc))
        x, y = tap_center(node["bounds"])
        self.shell("input tap {} {}".format(x, y))

    def input_text(self, text: str) -> None:
        self.shell("input text {}".format(text))


def ui_text(nodes: Sequence[Dict[str, str]]) -> str:
    return "\n".join(node.get("text", "") for node in nodes)


def confirm_preflight_if_present(adb: Adb) -> None:
    nodes = adb.dump_ui()
    if "转换前确认" in ui_text(nodes):
        adb.tap_text("开始转换")


def device_is_locked(window_dump: str) -> bool:
    if "isKeyguardShowing=true" in (window_dump or ""):
        return True
    focus = re.search(r"mCurrentFocus=Window\{[^}]*\}", window_dump or "")
    return bool(focus and "NotificationShade" in focus.group(0))


def ensure_device_ready(adb: Adb) -> None:
    if adb.serial not in adb.devices():
        raise SmokeError("设备未在线或未授权：{}".format(adb.serial))
    boot = adb.shell("getprop sys.boot_completed").stdout.strip()
    if boot != "1":
        raise SmokeError("设备尚未完成启动")
    power = adb.shell("dumpsys power").stdout
    if "mWakefulness=Awake" not in power:
        raise SmokeError("设备处于息屏状态；请唤醒并解锁后重跑")
    window = adb.shell("dumpsys window").stdout
    if device_is_locked(window):
        raise SmokeError("设备仍在锁屏/通知栏；请解锁后重跑")
    df = adb.shell("df -k /sdcard").stdout.splitlines()
    if len(df) < 2:
        raise SmokeError("无法读取 /sdcard 可用空间")
    fields = df[1].split()
    try:
        free_kb = int(fields[3])
    except (IndexError, ValueError):
        raise SmokeError("无法解析 /sdcard 可用空间")
    if free_kb < MIN_FREE_KB:
        raise SmokeError("可用空间不足 2GiB；安全门禁拒绝继续")


def installed_base_path(output: str) -> Optional[str]:
    match = re.search(r"^package:(.+)$", output or "", flags=re.MULTILINE)
    return match.group(1).strip() if match else None


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_installed_apk(adb: Adb, apk: Path) -> None:
    remote = installed_base_path(adb.shell("pm path {}".format(PACKAGE)).stdout)
    if not remote:
        raise SmokeError("设备上未安装 {}".format(PACKAGE))
    with tempfile.TemporaryDirectory(prefix="vlc_installed_apk_") as temp:
        local = Path(temp) / "installed.apk"
        adb.run("pull", remote, str(local))
        if sha256_file(local) != sha256_file(apk):
            raise SmokeError("设备已安装 APK 与待测 APK 的 SHA-256 不一致")


def install_and_grant(adb: Adb, apk: Path, skip_install: bool = False) -> None:
    if not skip_install:
        adb.run("install", "-r", str(apk))
    for permission in (
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.ACCESS_MEDIA_LOCATION",
    ):
        adb.shell("pm grant {} {}".format(PACKAGE, permission), check=False)


def fixture_search_prefix(prefix: str) -> str:
    stem, number = prefix.rsplit("_", 1)
    return "{}_{}".format(stem, number[:3])


def fixture_display_names(prefix: str) -> List[str]:
    stem, number = prefix.rsplit("_", 1)
    start = int(number)
    return ["{}_{:06d}.jpg".format(stem, start + index) for index in range(3)]


def parse_permission_state(output: str) -> Optional[bool]:
    value = (output or "").strip().lower()
    if value == "granted":
        return True
    if value == "denied":
        return False
    return None


def parse_permission_dumpsys(output: str, permission: str) -> Optional[bool]:
    values = re.findall(
        r"{}:\s*granted=(true|false)".format(re.escape(permission)),
        output or "",
        flags=re.IGNORECASE,
    )
    if not values:
        return None
    return any(value.lower() == "true" for value in values)


def wait_permission(
    adb: Adb,
    permission: str,
    expected: bool,
    timeout: float = 30.0,
) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = adb.shell("dumpsys package {}".format(PACKAGE), check=False)
        if parse_permission_dumpsys(result.stdout, permission) is expected:
            return
        time.sleep(0.5)
    raise SmokeError("权限状态超时：{} expected={}".format(permission, expected))


def media_display_names(output: str) -> List[str]:
    return [match.strip() for match in re.findall(r"_display_name=([^\r\n]+)", output or "")]


def wait_media_names(adb: Adb, prefix: str, expected: int, timeout: float = 30.0) -> List[str]:
    names = fixture_display_names(prefix)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        found = []
        for name in names:
            result = adb.shell(
                "content query --uri content://media/external/images/media "
                "--projection _display_name --where \"_display_name='{}'\"".format(name),
                check=False,
            )
            if name in media_display_names(result.stdout):
                found.append(name)
        if len(found) >= expected:
            return found
        time.sleep(0.5)
    raise SmokeError("MediaStore 中超时未出现 {} 张夹具：{}".format(expected, prefix))


def push_fixtures(adb: Adb, work: Path, prefix: str) -> Tuple[str, List[Path]]:
    generated = work / "fixtures"
    subprocess.run([
        sys.executable, str(ROOT / "tools/make_location_fixtures.py"),
        "--out", str(generated), "--prefix", prefix,
    ], check=True)
    run_id = str(int(time.time() * 1000))
    device_dir = "/sdcard/DCIM/_vlc_smoke_{}".format(run_id)
    adb.shell("mkdir -p {}".format(device_dir))
    files = sorted(generated.glob("*.jpg")) + sorted(generated.glob("*.mp4"))
    for path in files:
        adb.run("push", str(path), "{}/{}".format(device_dir, path.name))
        adb.shell("content call --uri content://media/ --method scan_file --arg {}/{}".format(
            device_dir, path.name))
    adb.shell("content call --uri content://media/ --method scan_volume --arg external_primary")
    wait_media_names(adb, prefix, 3)
    return device_dir, files


def pick_output_path(paths: Sequence[str], exclude_dir: str) -> Optional[str]:
    directory = exclude_dir.rstrip("/").rsplit("/", 1)[-1]
    marker = "/{}/".format(directory)
    return next((path for path in paths if marker not in path), None)


def query_output_paths(
    adb: Adb,
    names: Sequence[str],
    exclude_dir: str = "",
) -> Dict[str, str]:
    paths = {}
    for name in names:
        result = adb.shell(
            "content query --uri content://media/external/images/media "
            "--projection _data --where \"_display_name='{}'\"".format(name),
            check=False,
        )
        candidates = [match.strip() for match in re.findall(r"_data=([^\r\n]+)", result.stdout)]
        selected = pick_output_path(candidates, exclude_dir) if exclude_dir else (
            candidates[0] if candidates else None
        )
        if selected:
            paths[name] = selected
    return paths


def run_exif_probe(adb: Adb, work: Path, paths: Sequence[str]) -> str:
    sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk")))
    android_jars = sorted(sdk.glob("platforms/android-*/android.jar"))
    d8_bins = sorted(sdk.glob("build-tools/*/d8"))
    if not android_jars or not d8_bins:
        raise SmokeError("找不到 android.jar 或 d8")
    android_jar = android_jars[-1]
    d8 = d8_bins[-1]
    javac = os.environ.get("JAVA_HOME", "")
    javac = str(Path(javac) / "bin/javac") if javac else shutil.which("javac")
    if not javac:
        raise SmokeError("找不到 javac")
    classes = work / "probe-classes"
    dex = work / "probe-dex"
    classes.mkdir(parents=True, exist_ok=True)
    dex.mkdir(parents=True, exist_ok=True)
    subprocess.run([
        javac, "--release", "17", "-cp", str(android_jar), "-d", str(classes),
        str(ROOT / "tools/android-probe/ExifProbe.java"),
    ], check=True)
    subprocess.run([
        str(d8), "--release", "--lib", str(android_jar), "--min-api", "29",
        "--output", str(dex), str(classes / "ExifProbe.class"),
    ], check=True)
    remote = "/data/local/tmp/vlc_smoke_probe"
    adb.shell("rm -rf {0} && mkdir -p {0}".format(remote))
    adb.run("push", str(dex / "classes.dex"), remote + "/classes.dex")
    command = "CLASSPATH={}/classes.dex app_process /system/bin ExifProbe {}".format(
        remote, " ".join(paths))
    return adb.shell(command).stdout


def parse_exif_results(output: str) -> Dict[str, Optional[str]]:
    results = {}
    current = None
    for line in output.splitlines():
        if line.startswith("FILE "):
            current = line[len("FILE "):].strip()
            results[current] = None
        elif current and "getLatLong=" in line:
            results[current] = line.split("getLatLong=", 1)[1].strip()
    return results


def meminfo(adb: Adb) -> str:
    return adb.shell("dumpsys meminfo {}".format(PACKAGE), check=False).stdout


def cleanup(adb: Adb, device_dir: str, keep: bool, output_paths: Sequence[str] = ()) -> None:
    adb.shell("rm -f /sdcard/_vlc_smoke_ui.xml", check=False)
    adb.shell("rm -rf /data/local/tmp/vlc_smoke_probe", check=False)
    if keep:
        print("保留设备夹具：{}".format(device_dir))
        return
    adb.shell("rm -rf {}".format(device_dir), check=False)
    for path in output_paths:
        adb.shell("rm -f {}".format(path), check=False)
    adb.shell("content call --uri content://media/ --method scan_volume --arg external_primary", check=False)


def build_plan(
    serial: str,
    apk: Path,
    profile: bool,
    skip_install: bool = False,
    skip_permission_revoke: bool = False,
) -> List[str]:
    steps = [
        "check device awake/unlocked and free space >= 2GiB",
        "skip install; verify already-installed APK" if skip_install else "install -r {}".format(apk),
        "grant media and location permissions",
        "push three disposable vivo fixtures",
        "assert delete-original switch is off",
        "add fixtures and force-stop/relaunch to verify queue recovery",
        "skip OEM-unstable permission revoke; guard covered by prior real-device run"
        if skip_permission_revoke else
        "revoke location permission and verify guard, then restore it",
        "convert and read output EXIF with platform ExifInterface",
        "cleanup only _vlc_smoke_* fixtures and probe files",
    ]
    if profile:
        steps.insert(7, "capture dumpsys meminfo before/after conversion")
    return ["[{}] {}".format(index + 1, step) for index, step in enumerate(steps)]


def run_smoke(
    adb: Adb,
    apk: Path,
    profile: bool,
    keep: bool,
    skip_install: bool = False,
    skip_permission_revoke: bool = False,
) -> Path:
    ensure_device_ready(adb)
    if skip_install:
        verify_installed_apk(adb, apk)
    install_and_grant(adb, apk, skip_install=skip_install)
    adb.shell("am start -W -n {}/{}".format(PACKAGE, ACTIVITY))
    nodes = adb.wait_text("删原图", timeout=60)
    if not delete_original_is_off(ui_text(nodes)):
        raise SmokeError("删原图不是明确的“关”；安全门禁拒绝继续")

    with tempfile.TemporaryDirectory(prefix="vlc_smoke_") as temp:
        work = Path(temp)
        prefix = "IMG_{:06d}".format(int(time.time()) % 1000000)
        device_dir, _ = push_fixtures(adb, work, prefix)
        paths: Dict[str, str] = {}
        try:
            adb.shell("am force-stop {}".format(PACKAGE))
            adb.shell("am start -W -n {}/{}".format(PACKAGE, ACTIVITY))
            adb.wait_text("删原图", timeout=60)
            adb.tap_desc("添加照片")
            adb.wait_text("选择照片")
            adb.tap_text("全部相册")
            adb.tap_desc("搜索文件名")
            adb.input_text(fixture_search_prefix(prefix))
            adb.wait_text("全局匹配 3 张", timeout=60)
            adb.tap_text("全选")
            adb.wait_text("已选 3 张")
            adb.tap_text("添加 3 张")
            adb.wait_text("开始转换", timeout=60)

            location_guard = "PASS"
            if skip_permission_revoke:
                location_guard = "SKIPPED(OEM-unstable; prior real-device PASS)"
            else:
                adb.shell("pm revoke {} android.permission.ACCESS_MEDIA_LOCATION".format(PACKAGE))
                wait_permission(adb, "android.permission.ACCESS_MEDIA_LOCATION", False)
                adb.shell("am start -W -n {}/{}".format(PACKAGE, ACTIVITY))
                adb.wait_text("开始转换", timeout=60)
                adb.tap_text("开始转换")
                confirm_preflight_if_present(adb)
                adb.wait_text("缺少「位置」权限", timeout=30)
                adb.tap_text("取消")
                adb.shell("pm grant {} android.permission.ACCESS_MEDIA_LOCATION".format(PACKAGE))
                wait_permission(adb, "android.permission.ACCESS_MEDIA_LOCATION", True)
            adb.tap_text("开始转换")
            confirm_preflight_if_present(adb)

            before = meminfo(adb) if profile else ""
            started = time.monotonic()
            adb.wait_text("完成：", timeout=180)
            elapsed = time.monotonic() - started
            after = meminfo(adb) if profile else ""

            names = fixture_display_names(prefix)
            wait_media_names(adb, prefix, 3)
            paths = query_output_paths(adb, names, exclude_dir=device_dir)
            missing = [name for name in names if name not in paths]
            if missing:
                raise SmokeError("输出文件未入库：{}".format(", ".join(missing)))
            probe = run_exif_probe(adb, work, [paths[name] for name in names])
            exif = parse_exif_results(probe)
            for name in names[:2]:
                value = exif.get(paths[name])
                if not value or value == "null/false":
                    raise SmokeError("产物缺少可用 GPS：{} -> {}".format(name, value))
            value = exif.get(paths[names[2]])
            if value != "null/false":
                raise SmokeError("无 GPS 夹具不应被写入假位置：{} -> {}".format(names[2], value))

            report_dir = ROOT / "build/device-smoke"
            report_dir.mkdir(parents=True, exist_ok=True)
            report = report_dir / "smoke-{}.txt".format(int(time.time() * 1000))
            lines = [
                "serial={}".format(adb.serial),
                "apk={}".format(apk),
                "fixture_prefix={}".format(prefix),
                "queue_recovery=PASS",
                "location_guard={}".format(location_guard),
                "conversion_seconds={:.3f}".format(elapsed),
                "exif_readback=PASS",
                "",
                probe,
            ]
            if profile:
                lines += ["", "== meminfo before ==", before, "== meminfo after ==", after]
            report.write_text("\n".join(lines), encoding="utf-8")
            print("\n".join(lines[:7]))
            print("report={}".format(report))
            return report
        finally:
            cleanup(adb, device_dir, keep, paths.values())


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="影中影真机冒烟验证")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", ""))
    parser.add_argument("--apk", default=os.environ.get("VLC_APK", ""))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--profile", action="store_true")
    parser.add_argument("--keep-fixtures", action="store_true")
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--skip-permission-revoke", action="store_true")
    args = parser.parse_args(argv)
    if not args.serial:
        raise SmokeError("必须显式设置 ANDROID_SERIAL 或 --serial")
    if not args.apk:
        raise SmokeError("必须显式设置 VLC_APK 或 --apk")
    apk = Path(args.apk)
    if args.dry_run:
        print("DRY-RUN serial={} profile={}".format(args.serial, args.profile))
        print("\n".join(build_plan(
            args.serial, apk, args.profile, args.skip_install, args.skip_permission_revoke)))
        return 0
    if not apk.is_file():
        raise SmokeError("APK 不存在：{}".format(apk))
    adb = Adb(args.serial)
    run_smoke(
        adb, apk, args.profile, args.keep_fixtures,
        args.skip_install, args.skip_permission_revoke)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SmokeError as exc:
        print("ERROR: {}".format(exc), file=sys.stderr)
        sys.exit(2)
