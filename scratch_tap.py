import re, subprocess, sys, os, time

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")


def adb(*a):
    return subprocess.run([ADB, *a], capture_output=True).stdout


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/u.xml")
    xml = adb("exec-out", "cat", "/sdcard/u.xml").decode("utf-8", "ignore")
    out = []
    for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        out.append((m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5))))
    return out


def tap_verify_under(title_part):
    """Scroll until the card whose title contains title_part is on screen, then tap its 'Kiểm chứng'."""
    for _ in range(40):
        ns = nodes()
        t = [n for n in ns if title_part in n[0]]
        if t:
            ty = t[0][2]
            v = [n for n in ns if n[0] == "Kiểm chứng" and n[2] > ty]
            v.sort(key=lambda n: n[2])
            if v and v[0][4] < 2600:
                n = v[0]
                adb("shell", "input", "tap", str((n[1] + n[3]) // 2), str((n[2] + n[4]) // 2))
                print("tapped", title_part)
                return True
        # not found / button hidden: scroll down
        adb("shell", "input", "swipe", "700", "2100", "700", "1500", "250")
        time.sleep(0.6)
    print("not found", title_part)
    return False


def scroll_top():
    for _ in range(25):
        adb("shell", "input", "swipe", "700", "700", "700", "2300", "150")
    time.sleep(0.5)


if __name__ == "__main__":
    scroll_top()
    tap_verify_under(sys.argv[1])
