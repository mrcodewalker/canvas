package com.example.canvas.bench

/**
 * One measurable claim of an improvement: "against the plain way, this metric of the suite is lower/higher".
 * [kind] is what the audience reads on the badge (RAM, CPU, Đĩa). The numbers are never written here: they come
 * from the last run of [suiteId] on this device, and show "chưa đo" until that suite was run.
 */
class ImprovementClaim(
    val kind: String,
    val suiteId: String,
    val metricLabel: String,
    /** Index (in the suite's variants) of the plain, unimproved way the claim is compared with. */
    val baselineIndex: Int
)

/**
 * An improvement the team made at one boundary between tiers, against the way it is usually done (or the way this
 * project did it first). [gate] says where it sits: 0 = before Hot, 1 = Hot→Warm, 2 = Warm→Cold, 3 = back up
 * (Cold→Warm→Hot), 4 = the whole system.
 */
class Improvement(
    val gate: Int,
    val title: String,
    val plain: String,
    val ours: String,
    val claims: List<ImprovementClaim>,
    val suiteId: String,
    /** A caveat that goes with the numbers, so they are not read as more than they are. */
    val note: String? = null
)

object Improvements {
    val all: List<Improvement> = listOf(
        Improvement(
            gate = 0,
            title = "Lưu ô nét chạm tới, không lưu cả hộp bao",
            plain = "Lưu hình chữ nhật bao quanh nét, nét là khung rỗng thì vẫn lưu cả hình vuông.",
            ours = "Chia nét thành các ô; cỡ ô chọn theo chi phí đo được (byte + phí mỗi patch), chỉ lưu ô nét đi qua.",
            claims = listOf(ImprovementClaim("RAM", "tiles", "Lịch sử lưu (đỉnh)", 1)),
            suiteId = "tiles",
            note = "Hộp bao chạm trần RAM và dừng sớm, nên số của nó chỉ tính đến lúc dừng."
        ),
        Improvement(
            gate = 1,
            title = "Hot → Warm: mức nén tự chọn theo đo đạc",
            plain = "Một mức nén cố định cho mọi patch (nén mạnh thì tốn CPU, nén nhẹ thì tốn RAM).",
            ours = "Nén cùng điểm ảnh ở vài mức, chỉ giữ mức khi tỷ lệ nén tăng không ít hơn thời gian tăng.",
            claims = listOf(
                ImprovementClaim("CPU", "compression", "Nén tổng", 1),
                ImprovementClaim("RAM", "compression", "Dung lượng lưu (Warm + Cold)", 0)
            ),
            suiteId = "compression"
        ),
        Improvement(
            gate = 1,
            title = "Hot → Warm: dạng lưu chọn theo nội dung patch",
            plain = "Mọi patch, kể cả patch chỉ có một màu, đều đi qua Deflate: tốn CPU nén, CPU giải nén và vẫn giữ vài chục byte.",
            ours = "Quét một lượt để biết patch một màu (5 byte, không nén), nhiều mảng phẳng (chạy-lặp), hay mịn (lọc hiệu rồi Deflate). Mỗi loại patch tự đo và học dạng rẻ nhất trên máy này; định kỳ thử lại.",
            claims = listOf(
                ImprovementClaim("CPU nén", "codec", "Nén tổng", 0),
                ImprovementClaim("CPU giải nén", "codec", "Giải nén tổng", 0),
                ImprovementClaim("RAM", "codec", "Dung lượng lưu (Warm + Cold)", 0)
            ),
            suiteId = "codec",
            note = "Việc thử các dạng ở mỗi loại patch mới cũng tốn CPU; số nén tổng đã gồm khoản đó. Nếu nét vẽ ít mảng phẳng thì lợi ích nhỏ."
        ),
        Improvement(
            gate = 1,
            title = "Hot → Warm → Cold: chuyển tầng chạy ngầm",
            plain = "Nén và ghi đĩa diễn ra ngay trong lúc lưu nét, nên nét làm tràn Hot phải chờ cả hai việc xong.",
            ours = "Nét vào Hot là xong; nén và ghi đĩa làm song song với việc vẽ, chỉ chờ khi Hot đã vượt quá một ngân sách.",
            claims = listOf(
                ImprovementClaim("Độ trễ p95", "async", "Thời gian lưu nét p95", 0),
                ImprovementClaim("Nét lâu nhất", "async", "Nét lâu nhất", 0)
            ),
            suiteId = "async",
            note = "Nén và ghi đĩa vẫn tốn CPU như cũ; cải tiến là bỏ chúng khỏi đường đi của nét vẽ."
        ),
        Improvement(
            gate = 2,
            title = "Warm → Cold: nén sâu có chọn lọc, giữ lại bản nén và file",
            plain = "Mọi patch xuống Cold đều bị nén mức cao nhất và ghi file mới, kể cả patch vừa từ Cold lên.",
            ours = "Mỗi patch tự quyết nén sâu hay không theo lợi ích ròng; patch quay về mang theo bản nén và file cũ nên không nén, không ghi lại.",
            claims = listOf(
                ImprovementClaim("CPU", "boundary", "Nén tổng", 0),
                ImprovementClaim("Đĩa", "boundary", "Ghi đĩa tổng", 0)
            ),
            suiteId = "boundary"
        ),
        Improvement(
            gate = 2,
            title = "Warm → Cold: chọn nét xuống đĩa theo giá trị giữ lại (thử nghiệm)",
            plain = "Nét cũ nhất xuống đĩa trước, bất kể to hay nhỏ.",
            ours = "Giá trị giữ lại mỗi byte RAM = (xác suất còn Undo tới đó × tốc độ đọc + tốc độ ghi) × (cỡ + phí cố định mỗi file) / cỡ; nét giá trị thấp nhất xuống trước.",
            claims = listOf(
                ImprovementClaim("Đĩa đọc", "victims", "Đọc đĩa tổng", 0),
                ImprovementClaim("Độ trễ TB", "victims", "Hoàn tác trung bình", 0)
            ),
            suiteId = "victims",
            note = "Thử nghiệm, mặc định đang tắt: trên máy ảo chưa thấy lợi ích rõ vì phí cố định mỗi file nhỏ so với cỡ nét. Cần đo trên máy thật trước khi bật."
        ),
        Improvement(
            gate = 3,
            title = "Cold → Warm → Hot: nạp trước thích nghi",
            plain = "Nạp trước một số nét cố định (K = 5), không biết nét nặng hay nhẹ, máy nhanh hay chậm.",
            ours = "Độ sâu tính từ nhịp Undo của người dùng, tốc độ đọc/giải nén đo được và RAM còn trống; đọc sớm bước kế tiếp.",
            claims = listOf(
                ImprovementClaim("Độ trễ p95", "prefetch", "Hoàn tác p95", 0),
                ImprovementClaim("Undo chậm", "prefetch", "Lần Undo chậm hơn 50 ms", 0)
            ),
            suiteId = "prefetch",
            note = "Một lần chạy trên máy ảo: chênh vài chục phần trăm có thể là nhiễu. Chạy lại để thấy độ dao động."
        ),
        Improvement(
            gate = 4,
            title = "Cả hệ thống: chia ngân sách Hot/Warm theo thói quen vẽ",
            plain = "Ngân sách Hot và Warm là hai số cố định cho mọi người dùng.",
            ours = "Hot = nét nặng nhất hoặc số lần Undo thường làm × cỡ nét; Warm nhận phần còn lại vì chứa cùng số bước ở cỡ nhỏ hơn nhiều.",
            claims = listOf(ImprovementClaim("RAM", "modes", "RAM cho lịch sử (Hot + Warm)", 1)),
            suiteId = "modes",
            note = "So với Dirty rect ở thời điểm nó dừng vì chạm trần RAM; hai chế độ vẽ số nét khác nhau."
        )
    )
}
