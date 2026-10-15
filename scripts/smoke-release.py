#!/usr/bin/env python3
"""Exercise the installed production APK through Android's actual UI."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

APP = "io.github.fangshix.lexi"
COMPONENT = APP + "/com.finisdiscago.leximvp.MainActivity"
OUT = Path("release-device-reports")
OUT.mkdir(exist_ok=True)


def adb(*args, timeout=60):
    return subprocess.check_output(["adb", *args], timeout=timeout)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/lexi-smoke.xml")
    root = ET.fromstring(adb("shell", "cat", "/sdcard/lexi-smoke.xml"))
    return [n for n in root.iter("node") if n.get("package") == APP]


def find(label, timeout=40, description_only=False):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            candidates = []
            for node in nodes():
                values = [node.get("content-desc", "")] if description_only else [node.get("text", ""), node.get("content-desc", "")]
                values = [value.strip() for value in values]
                if description_only and label not in values:
                    continue
                if any(label in value for value in values):
                    candidates.append((0 if label in values else 1, node))
            if candidates:
                return sorted(candidates, key=lambda pair: pair[0])[0][1]
        except (subprocess.SubprocessError, ET.ParseError):
            pass
        time.sleep(0.5)
    raise AssertionError(f"Visible production UI missing: {label}")


def tap(label, description_only=False):
    node = find(label, description_only=description_only)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    assert x2 > x1 and y2 > y1, f"Empty touch target: {label}"
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.5)


def capture(name):
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))


def launch():
    output = adb("shell", "am", "start", "-W", "-n", COMPONENT).decode()
    (OUT / "launch.txt").write_text(output)
    assert "Status: ok" in output and "timeout" not in output.lower(), output


try:
    apk = next(Path("signed-candidate").glob("*.apk"))
    (OUT / "install.txt").write_bytes(adb("install", "--no-streaming", "-r", str(apk), timeout=240))
    launch()
    tap("选择词书 / 添加单词")
    tap("体验示例词库")
    find("10 个词条")
    capture("sample-imported")
    tap("学习", description_only=True)
    capture("study-overview")
    tap("开始今日学习")
    tap("认识")
    tap("继续")
    capture("learning")
    adb("shell", "input", "keyevent", "4")
    tap("我的", description_only=True)
    find("我的学习计划")
    capture("profile-day")
    tap("打开设置")
    find("日间模式")
    capture("settings-day")
    tap("夜间模式")
    time.sleep(1)
    capture("settings-night")
    adb("shell", "am", "force-stop", APP)
    launch()
    tap("我的", description_only=True)
    find("我的学习计划")
    capture("profile-night-after-restart")
    (OUT / "result.txt").write_text("PASS: signed release install, sample import, learning, navigation, theme switch and restart\n")
finally:
    capture("last-screen")
    try:
        adb("shell", "uiautomator", "dump", "/sdcard/lexi-smoke.xml")
        (OUT / "last-ui.xml").write_bytes(adb("shell", "cat", "/sdcard/lexi-smoke.xml"))
    except subprocess.SubprocessError:
        pass
    (OUT / "logcat.txt").write_bytes(adb("logcat", "-d"))
