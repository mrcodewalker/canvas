import sys, time
sys.path.insert(0, r"C:\Users\ADMIN\AndroidStudioProjects\Canvas")
import scratch_tap as t

t.scroll_top()
seen = []
for _ in range(int(sys.argv[1]) if len(sys.argv) > 1 else 14):
    for n in t.nodes():
        if n[0] and n[0] not in seen:
            seen.append(n[0])
    t.adb("shell", "input", "swipe", "700", "2100", "700", "1000", "250")
    time.sleep(0.5)
sys.stdout.reconfigure(encoding="utf-8")
print("\n".join(seen))
