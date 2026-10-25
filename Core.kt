package com.hmc.auto

import org.json.JSONObject

const val PREFS = "hmc"

// Thứ tự điểm chạm: 0 nửa cung, 1 ngẩng lên (cao), 2 tự nhiên (trung), 3 cúi xuống (thấp), 4..11 phím 1..7, 1 cao
val LABELS = listOf(
    "Nửa Cung", "Ngẩng Lên", "Ngẩng Tự Nhiên", "Cúi Xuống",
    "Phím 1", "Phím 2", "Phím 3", "Phím 4", "Phím 5", "Phím 6", "Phím 7", "Phím 1 cao"
)

data class N(val t: Double, val k: Int, val o: String, val s: Int, val d: Double)
data class Song(val bpm: Int, val base: String, val notes: List<N>)

fun parseSong(js: String): Song {
    val j = JSONObject(js)
    val a = j.getJSONArray("notes")
    val l = ArrayList<N>()
    for (i in 0 until a.length()) {
        val n = a.getJSONObject(i)
        l.add(N(n.getDouble("t"), n.getInt("k"), n.optString("o", "mid"), n.optInt("s", 0), n.optDouble("d", 1.0)))
    }
    return Song(j.optInt("bpm", 100), j.optString("base", "mid"), l)
}

class Stk(val p: Int, val off: Long, val dur: Long)
class Act(val t: Long, val strokes: List<Stk>)

fun regIdx(o: String) = when (o) { "low" -> 0; "high" -> 2; else -> 1 }
fun regBtn(r: Int) = when (r) { 0 -> 3; 2 -> 1; else -> 2 }

private class Pn(val start: Long, val dur: Long, val key: Int, val sharp: Boolean, val pre: List<Int>)

/**
 * Phím kèn kêu theo thời gian giữ, nên mỗi nốt được GIỮ đúng độ dài của nó
 * (trừ một khoảng ngắt nhỏ để tách nốt). Nút tông/nửa cung chỉ cần chạm ngắn,
 * được xếp vào khoảng nhả giữa hai nốt. Tham số tap (nút "Chạm") giờ là độ ngắt giữa các nốt.
 * Ba nút tông là nhóm chọn 1 trong 3 (mặc định ở giữa): đầu bài chạm nút tông của bài để đồng bộ.
 */
fun compile(song: Song, tap: Long, scale: Double, hold: Boolean): List<Act> {
    val spb = 60000.0 / (song.bpm * scale)
    val gap = maxOf(15L, tap / 2)
    val bt = 70L
    val step = bt + 30
    val base = regIdx(song.base)
    val out = ArrayList<Act>()
    out.add(Act(0, listOf(Stk(regBtn(base), 0, bt))))

    val ns = ArrayList<Pn>()
    var reg = base
    var sharp = false
    for (n in song.notes) {
        if (n.k <= 0) continue
        val r = regIdx(n.o)
        val pre = ArrayList<Int>()
        if (r != reg) { pre.add(regBtn(r)); reg = r }
        val want = n.s == 1
        if (!hold && want != sharp) { pre.add(0); sharp = want }
        ns.add(Pn((n.t * spb).toLong() + 600, (n.d * spb).toLong(), 3 + n.k, want, pre))
    }

    var lastEnd = 0L
    for ((i, n) in ns.withIndex()) {
        var end = n.start + n.dur - gap
        if (i + 1 < ns.size) {
            val nx = ns[i + 1]
            val preStart = nx.start - nx.pre.size * step - 20
            end = minOf(end, preStart - 20)
        }
        end = maxOf(end, n.start + 40)
        val len = end - n.start
        var tp = n.start - n.pre.size * step - 20
        for (p in n.pre) { out.add(Act(tp, listOf(Stk(p, 0, bt)))); tp += step }
        if (hold && n.sharp) {
            out.add(Act(n.start - 20, listOf(Stk(0, 0, len + 40), Stk(n.key, 20, len))))
        } else {
            out.add(Act(n.start, listOf(Stk(n.key, 0, len))))
        }
        lastEnd = end
    }

    // cuối bài: chạm về tông của bài, tắt nửa cung
    var tp = lastEnd + 120
    if (reg != base) { out.add(Act(tp, listOf(Stk(regBtn(base), 0, bt)))); tp += step }
    if (sharp) out.add(Act(tp, listOf(Stk(0, 0, bt))))
    return out
}
