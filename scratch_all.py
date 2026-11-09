import sys, time
sys.path.insert(0, r"C:\Users\ADMIN\AndroidStudioProjects\Canvas")
import scratch_tap as t

titles = ["dạng lưu chọn theo nội dung", "Lưu ô nét chạm tới", "mức nén tự chọn", "chuyển tầng chạy ngầm", "nén sâu có chọn lọc",
          "nạp trước thích nghi", "chia ngân sách", "chọn nét xuống đĩa"]


def wait_done(limit=900):
    t0 = time.time()
    time.sleep(8)
    while time.time() - t0 < limit:
        if not any(n[0] == "Đang chạy…" for n in t.nodes()):
            return True
        time.sleep(8)
    return False


sys.stdout.reconfigure(encoding="utf-8")
for title in titles:
    t.scroll_top()
    if t.tap_verify_under(title):
        print(title, "done" if wait_done() else "TIMEOUT", flush=True)
