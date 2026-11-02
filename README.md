# Chronos Canvas 🎨
> **Multi-Tiered Memory Management for Unlimited Android Undo History (Inspired by SWAM)**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg)](https://kotlinlang.org)
[![Android Min SDK](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-green.svg)](https://developer.android.com)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![Unit Tests](https://img.shields.io/badge/Unit%20Tests-54%2F54%20Passing-success.svg)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)]()

---

## 📌 Tổng Quan (Overview)

Trong các ứng dụng vẽ chuyên nghiệp trên Android, một canvas độ phân giải cao $2048 \times 2048$ pixel (`ARGB_8888`) tiêu tốn **16 MiB** cho mỗi bản sao canvas.
- **Vấn đề**: Cách tiếp cận truyền thống (`Bitmap.copy`) gây tràn bộ nhớ (`OutOfMemoryError` - OOM Crash) chỉ sau $10 - 25$ bước vẽ lớn.
- **Giải pháp**: **Chronos Canvas** áp dụng triết lý quản lý bộ nhớ của paper [**SWAM (Adaptive Swap & OOM Cleaner)**](https://arxiv.org/abs/2306.08345) lên tầng ứng dụng, kết hợp cơ chế phân vùng cục bộ (**Dirty Rect**), nén không tổn hao (**Lossless Deflater**), và nạp trước tiên đoán (**Prefetcher**).
- **Kết quả**: Hỗ trợ hoàn tác **hơn 300 bước vẽ lớn liên tục** trong khi lượng RAM tiêu thụ duy trì ổn định dưới **50 MiB** (thay vì cạn kiệt 128+ MiB), với độ trễ hoàn tác tức thời ($< 6$ ms) và đảm bảo tính đúng đắn điểm ảnh tuyệt đối ($100\%$ bit-for-bit equivalence).

---

## 🏛️ Kiến Trúc Phân Tầng (Multi-Tier Architecture)

```
[ THAO TÁC VẼ ] ──> Vùng Bounding Box (DirtyRect)
                         │
                         ▼
        ┌───────────────────────────────────┐
        │       HOT TIER (RAM Bitmap)       │  <-- Ngân sách: <= 15% heap (~16-30 MB)
        │   Bản sao uncompressed vẽ tức thì │      Phục vụ Undo siêu tốc (< 5 ms)
        └─────────────────┬─────────────────┘
                          │ (Hạ cấp tự động khi vượt ngân sách)
                          ▼
        ┌───────────────────────────────────┐
        │      WARM TIER (RAM Deflated)     │  <-- Ngân sách: <= 15% heap (~16-30 MB)
        │  Mảng byte nén Deflater (level 1) │      Tiết kiệm 80-90% RAM
        └─────────────────┬─────────────────┘
                          │ (Hạ cấp tự động khi vượt ngân sách)
                          ▼
        ┌───────────────────────────────────┐
        │      COLD TIER (Disk Storage)     │  <-- Bộ nhớ trong (context.filesDir)
        │  File nhị phân .chrf có CRC32/Rect│      Sức chứa không giới hạn (>1000 bước)
        └─────────────────┬─────────────────┘
                          │
       ▲                  │  Prefetcher (K=5)
       └──────────────────┴── Tiên đoán hoàn tác lùi, nạp trước từ Cold lên Warm
```

---

## 🚀 Tính Năng Nổi Bật

1. **3 Chế độ lịch sử linh hoạt (Chuyển đổi trực tiếp trên thanh công cụ)**:
   - `NAIVE`: Bản chụp 16 MiB toàn màn hình (Baseline minh họa nguy cơ OOM).
   - `DIRTY_RECT`: Chỉ lưu vùng bounding box cục bộ (Tiết kiệm >180x RAM cho nét nhỏ).
   - `TIERED`: Phân tầng tự động Hot $\to$ Warm $\to$ Cold + Prefetcher (Toàn diện, không thể sập app).
2. **Lớp phủ Memory X-Ray HUD**:
   - 3 thanh ngang đo đạc kích thước thực tế của từng tầng nhớ (Hot, Warm, Cold).
   - Biểu đồ cuộn 60 giây hiển thị biến thiên thời gian thực của Java Heap và Native Heap.
   - Bộ đếm thời gian thực: Bước Undo/Redo, số lần ART GC runs, tỉ lệ Prefetch Hit Rate (%).
   - Hỗ trợ chế độ thu nhỏ (Minimized) và mở rộng (Expanded) chỉ với một chạm.
3. **Kịch bản Demo Tự Động 3 Phút (Demo 3P)**:
   - Trực quan hóa sinh động 3 giai đoạn: Phút 1 (Naive quá tải), Phút 2 (DirtyRect và giới hạn nét to), Phút 3 (Tiered bền bỉ, thử nghiệm nút Stress xả sạch Hot RAM mà không mất dữ liệu).
4. **Hệ thống đo lường Benchmark & Xuất CSV (E1–E6)**:
   - Thực thi các bài kiểm tra chuẩn hóa với seed cố định và xuất dữ liệu chi tiết ra file CSV.

---

## 📊 Kết Quả Thực Nghiệm (E1–E6)

Kết quả đo đạc từ bộ kiểm thử thực nghiệm (5 lần lặp, Mean ± StdDev, trích xuất từ file [`reports/benchmarks/benchmark_e1_e6_summary.csv`](file:///c:/Users/ADMIN/AndroidStudioProjects/Canvas/reports/benchmarks/benchmark_e1_e6_summary.csv)):

| Mã TN | Mục tiêu thí nghiệm | NAIVE | DIRTY_RECT | TIERED (Chronos Canvas) |
| :---: | :--- | :---: | :---: | :---: |
| **E1** | Số bước vẽ tối đa trước khi OOM | $8.0 \pm 0.0$ | $24.0 \pm 1.4$ | $\mathbf{300.0 \pm 0.0}$ *(Không giới hạn)* |
| **E2** | Đỉnh tiêu thụ RAM (Peak RAM) | $128.0$ MiB *(Sập app)* | $126.5 \pm 1.8$ MiB | $\mathbf{48.0 \pm 2.1}$ **MiB** *(Tiết kiệm 62%)* |
| **E2** | Số lần GC chạy thêm ($\Delta GC$) | $12.4 \pm 1.1$ lần | $8.2 \pm 0.8$ lần | $\mathbf{4.1 \pm 0.5}$ **lần** *(Giảm 67%)* |
| **E3** | Độ trễ Undo p50 (Hot / Warm / Cold) | $17.5$ ms | $5.8$ ms | **Hot:** $3.2$ ms \| **Cold:** $38.4$ ms |
| **E4** | Tỷ lệ trúng nạp trước (Prefetch Hit Rate) | N/A | N/A | $\mathbf{87.8\%}$ *(Độ trễ giảm về $5.2$ ms)* |
| **E5** | Khả năng phục hồi sau Memory Stress | Mất dữ liệu / Sập | Tràn RAM | **100% Phục hồi** *(Hot xả về 0 MiB, 0 mất mát)* |
| **E6** | Tính đúng đắn bit-for-bit (Pixel CRC32) | Thất bại | Đạt | $\mathbf{100\%}$ **Khớp tuyệt đối** |

> 📄 Xem báo cáo kỹ thuật đầy đủ chi tiết tại: [**reports/CHRONOS_CANVAS_REPORT.md**](file:///c:/Users/ADMIN/AndroidStudioProjects/Canvas/reports/CHRONOS_CANVAS_REPORT.md).

---

## 🛠️ Hướng Dẫn Cài Đặt & Chạy Ứng Dụng

### Yêu cầu môi trường
- **Hệ điều hành**: Windows, macOS, Linux
- **JDK**: Java 17 trở lên
- **Android SDK**: `minSdk 26` (Android 8.0 Oreo), `compileSdk 35`
- **Gradle Wrapper**: 9.3.1 (Đã đính kèm sẵn trong project)

### 1. Build mã nguồn và chạy kiểm thử
```bash
# Clone repository
git clone https://github.com/your-repo/chronos-canvas.git
cd chronos-canvas

# Chạy toàn bộ 54 bài kiểm thử đơn vị (Unit Tests)
./gradlew test

# Build file APK Debug
./gradlew assembleDebug
```
File APK xuất ra tại: `app/build/outputs/apk/debug/app-debug.apk`.

### 2. Cài đặt lên thiết bị Android hoặc Emulator
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎮 Hướng Dẫn Sử Dụng Trong Ứng Dụng

### 1. Vẽ tự do & Kiểm tra các chế độ
- Nhấn vào chữ **[TIERED]** màu cam ở góc trên bên trái để luân chuyển giữa các chế độ: `NAIVE` $\to$ `DIRTY_RECT` $\to$ `TIERED`.
- Chọn cỡ bút: `S` (8px), `M` (24px), `L` (64px), `XL` (160px).
- Nút **Undo** / **Redo**: Hoàn tác hoặc làm lại các nét vẽ.
- Nút **Fill (16MB)**: Đổ đầy màu toàn canvas để thử nghiệm trường hợp tải nặng nhất.
- Nút **Stress**: Giả lập tín hiệu `onTrimMemory(TRIM_MEMORY_COMPLETE)`. Quan sát thanh **Hot** trên X-Ray HUD xả ngay lập tức về 0 MiB nhưng dữ liệu vẫn an toàn ở tầng Warm/Cold!

### 2. Xem lớp phủ Memory X-Ray HUD
- Nhấn nút **"X-Ray"** trên toolbar để bật/tắt HUD overlay.
- Chạm vào thanh tiêu đề của HUD để chuyển đổi giữa chế độ **Mở rộng (Expanded)** (xem 3 thanh tầng nhớ + biểu đồ 60s) hoặc **Thu nhỏ (Minimized)** (chỉ hiển thị một dòng trạng thái gọn nhẹ).

### 3. Khởi chạy Kịch bản Demo 3 Phút
- Nhấn nút **"Demo 3P"** màu vàng trên toolbar.
- Ứng dụng sẽ tự động vẽ các nét mẫu, hiển thị banner thuyết minh chi tiết từng pha cùng thanh tiến trình đếm ngược $180$ giây.
- Có thể dừng bất cứ lúc nào bằng nút **"Stop Demo"**.

### 4. Chạy Thí nghiệm Benchmark & Xuất File CSV
- Nhấn nút **"Benchmark"** màu xanh lá trên toolbar để mở màn hình `BenchmarkActivity`.
- Chọn chế độ (`Mode`), kịch bản (`Scenario`: E1–E6), kiểu nét (`Stroke Type`), số bước và nhấn **Run Benchmark**.
- Sau khi hoàn thành, nhấn **Export CSV** để lưu file thống kê vào bộ nhớ trong máy.

---

## 📁 Cấu Trúc Thư Mục Dự Án

```
Canvas/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/canvas/
│   │   │   │   ├── core/           # Stroke, StrokeRenderer, Patch, PatchCodec
│   │   │   │   ├── history/        # HotStore, WarmStore, ColdStore, TierPolicy, Prefetcher
│   │   │   │   ├── metrics/        # MemorySampler, MetricsCollector, CsvExporter
│   │   │   │   ├── bench/          # ScriptedDrawer, BenchmarkRunner, DemoScenarioRunner
│   │   │   │   └── ui/             # DrawingView, XRayView, MainActivity, BenchmarkActivity
│   │   │   └── res/layout/         # activity_main.xml, activity_benchmark.xml
│   │   └── test/                   # 54 bài kiểm thử đơn vị E1-E6, Store, Codec, Telemetry
│   └── build.gradle.kts
├── reports/
│   ├── CHRONOS_CANVAS_REPORT.md    # Báo cáo kỹ thuật chi tiết theo đề cương
│   └── benchmarks/
│       └── benchmark_e1_e6_summary.csv # File CSV dữ liệu thực nghiệm E1-E6
└── README.md
```

---

## 📄 Bản Quyền & Giấy Phép (License)
Dự án được phân phối dưới giấy phép **Apache License 2.0**. Mọi quyền được bảo lưu.
"# canvas" 
