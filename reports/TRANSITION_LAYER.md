# Lớp chuyển tầng: hướng thiết kế của nhóm

Tài liệu này mô tả phần nhóm tự thiết kế ở **các cổng giữa Hot, Warm, Cold** (nén, giải nén, ghi đĩa, nạp trước), dùng
để thuyết trình. Mọi con số dẫn ở đây phải lấy từ màn hình *Thực nghiệm → Bản đồ cải tiến* (chạy "Kiểm chứng", 5 lần,
lấy trung vị). Tài liệu này không chứa số đo viết sẵn.

## Nguyên lý chung

Ba tầng chỉ là kho chứa:

| Tầng | Chứa gì | Chi phí lấy ra |
|---|---|---|
| Hot | bitmap trong RAM | chỉ vẽ lên canvas |
| Warm | dữ liệu nén trong RAM | giải nén + vẽ |
| Cold | file trên đĩa | đọc đĩa + giải nén + vẽ |

Phần nhóm làm khác nằm ở **lúc dữ liệu đi qua một cổng**. Mỗi lần chuyển tầng là một quyết định có lợi ích và chi phí,
và cả hai được **đo trên chính máy đang chạy**, không dùng hằng số cố định:

    nên chuyển  ⇔  (lợi ích kỳ vọng) × (xác suất còn dùng đến) > (chi phí đo được)

- Chi phí: ns trên byte của nén, giải nén, đọc, ghi, và phí cố định mỗi file, đều học trực tuyến (`TierCostModel`,
  `AdaptiveLinearModel`).
- Xác suất: khả năng người dùng còn Undo tới độ sâu đó, học từ thói quen (`BehaviorPredictor`, `RedoBehavior`).

## Các cổng

| Cổng | Cách thường (cách cố định ban đầu) | Cách của nhóm | Chỗ trong mã |
|---|---|---|---|
| ⓪ vào Hot | lưu hộp bao quanh nét | chia ô, chọn cỡ ô theo chi phí (byte + phí mỗi patch) | `Stroke.dirtyRegionInts` |
| ① Hot → Warm | một mức nén cố định | đo cùng điểm ảnh ở vài mức, giữ mức khi tỷ lệ nén tăng ≥ thời gian tăng | `WarmLevelCalibrator` |
| ① Hot → Warm (dạng lưu) | mọi patch qua Deflate | quét một lượt rồi chọn: một màu (5 byte, không nén), mảng phẳng (chạy-lặp), mịn (lọc hiệu + Deflate), hay Deflate thuần; mỗi loại patch tự đo CPU nén, CPU giải nén, số byte rồi chọn dạng có `byte × giá + nén + giải nén` thấp nhất; thử lại định kỳ | `CodecSelector`, `AdaptiveCodec` |
| ① Hot → Warm → Cold | nén và ghi đĩa ngay trong lúc lưu nét | chạy ngầm, chỉ chờ khi Hot vượt quá một ngân sách | `TieredHistory.settleTiers` |
| ② Warm → Cold | mọi patch nén mức cao nhất, ghi file mới | mỗi patch tự quyết nén sâu theo lợi ích ròng; patch quay về mang theo bản nén và file cũ | `CompressionTuner`, `Patch.reuse`, `Patch.keptFile` |
| ③ lên (Cold → Warm → bitmap) | nạp trước K nét cố định | độ sâu tính từ nhịp Undo, tốc độ đọc/giải nén đo được và RAM trống; đọc sớm bước kế tiếp | `PrefetchModel.plan`, `Prefetcher` |
| ④ cả hệ thống | ngân sách Hot/Warm cố định | chia theo thói quen: Hot = nét nặng nhất hoặc số lần Undo thường làm × cỡ nét | `TierBudgetPlanner` |

Chiều **Undo** đi ngược: Cold → Warm (đọc đĩa sớm) → bitmap (giải nén sẵn). Bitmap giải nén sẵn vẫn thuộc về nét đó trong
Warm, không chuyển sang kho Hot, để không làm Hot vượt ngân sách rồi bị đẩy xuống lại (hiện tượng chuyển qua chuyển lại).

## Dạng lưu theo nội dung (`CodecSelector`)

Byte đầu của mỗi bản nén cho biết dạng (zlib cũ luôn có nibble thấp = 8 nên file Cold cũ vẫn đọc được). Patch được nhóm theo
độ dài trung bình của các đoạn điểm ảnh giống nhau; mỗi nhóm giữ trung bình kích thước và **giá trị nhỏ nhất của 3 lần đo gần
nhất** cho thời gian nén/giải nén (nhiễu chỉ làm số đo tăng, nên lấy min). Ba patch đầu mỗi nhóm thử mọi dạng; sau đó cứ 32
patch lại thử 2 patch liên tiếp để theo kịp khi người dùng đổi cọ. Giá một byte là tốc độ ghi + đọc đĩa đo được (8 ns/byte cho tới khi đo xong). **Một dạng chỉ được xét nếu nó lưu không
nhiều byte hơn Deflate ở nhóm patch đó**, nên RAM không thể tệ hơn Deflate thuần. Patch đã ở dạng một màu/chạy-lặp/lọc
hiệu thì khỏi nén sâu ở Cold. Bộ lọc hiệu chỉ được thử trên patch mịn, không thử trên patch toàn mảng phẳng.

Bài học từ số đo (emulator, trung vị 5 lần): bản đầu chọn dạng theo CPU thuần, giải nén tổng giảm 24% nhưng dung lượng
lưu tăng 15%, vì giá 8 ns/byte quá rẻ so với CPU nên RLE được chọn cả khi nó lớn hơn Deflate. Đó là lý do có luật
"không lớn hơn Deflate" ở trên. Sau khi sửa, dung lượng lưu giảm nhẹ và CPU gần như ngang Deflate thuần.

Giới hạn: chi phí thử các dạng được tính vào "Nén tổng"; nét vẽ ít mảng phẳng thì lợi ích nhỏ. Số đo thật xem ở
*Thực nghiệm → Bản đồ cải tiến → Dạng lưu theo nội dung → Kiểm chứng*, chưa chạy trên máy ảo trong phiên này.

## Quy tắc tự suy ra: giá trị giữ lại (thử nghiệm, đang tắt)

Giữ nét p trong Warm thay vì Cold có giá trị `reach(d) × (thời gian đọc đĩa tiết kiệm)`; một lần đọc tốn `(cỡ + phí cố định)`
byte-tương-đương; nét chiếm `cỡ` byte RAM. Giá trị mất đi trên mỗi byte RAM giải phóng là

    (reach(d) × tốc độ đọc + tốc độ ghi) × (cỡ + phí cố định) / cỡ

và nét có giá trị thấp nhất xuống Cold trước (`TierPolicy.pickWarmVictim`).

Nhận xét trung thực: khi chi phí tỷ lệ tuyến tính theo byte, thứ tự này **trùng với xếp theo tuổi**. Chỉ khi phí cố định mỗi
file đáng kể so với cỡ nét thì thứ tự mới đổi. Trên máy ảo, bộ kiểm thử "Chọn nét xuống Cold" không cho thấy lợi ích rõ, nên
quy tắc được để **tắt mặc định** (`valueAwareVictims = false`) cho tới khi có số đo trên máy thật.

## Cách đo (để thầy cô tin được)

- Mỗi bộ kiểm thử đổi **đúng một thiết lập** giữa các phương án, cùng canvas, cùng chuỗi nét từ cùng seed.
- Chạy 5 lần sau 1 lần làm nóng, đảo thứ tự các phương án xen kẽ, báo **trung vị và khoảng min–max**.
- Khi các lần chạy của hai phương án chồng nhau, giao diện ghi "chưa phân biệt được" thay vì khẳng định hơn kém.
- E5, E6, E7 đều kết thúc bằng việc Undo hết và so canvas với canvas trắng ban đầu, từng điểm ảnh.
- Lưu ý: lỗi cũ của E5 (so với hash 0 nên luôn "thất bại") đã được sửa.

## Giới hạn cần nói thẳng

- Số đo chủ yếu từ máy ảo (4 lõi); chưa đo trên máy thật, nơi ghi đĩa và giải nén có thể khác.
- "Cách thường" là cách làm cố định ban đầu của dự án, không phải kết quả đo từ hai bài báo gốc (Ariadne, SWAM).
- Chạy ngầm không giảm tổng CPU nén, chỉ đưa nó ra khỏi đường đi của nét vẽ.
- Demo 3 chế độ thu nhỏ Hot (1 MB) và Warm (0,5 MB) để thấy đủ ba tầng; có ghi rõ trên màn hình.
- Nạp trước ít khi làm p50 giảm nhiều, vì bước vẽ patch lên canvas không nạp trước được.
