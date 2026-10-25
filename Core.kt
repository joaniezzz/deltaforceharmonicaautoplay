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

/**
 * Đổi bài thành danh sách thao tác chạm theo thời gian (ms).
 * Nút tông/nửa cung chỉ đổi trạng thái cho các nốt sau: chạm nút, chạm nốt,
 * rồi chạm ngược về tông của bài trước nốt kế tiếp cần tông khác.
 */
fun compile(song: Song, tap: Long, scale: Double, hold: Boolean): List<Act> {
    val spb = 60000.0 / (song.bpm * scale)
    val out = ArrayList<Act>()
    val base = regIdx(song.base)
    var reg = base
    var sharp = false
    var prevEnd = 0L
    fun press(p: Int, t: Long) {
        val latest = t - tap - 25
        val tp = maxOf(prevEnd + 25, latest - 100)
        out.add(Act(tp, listOf(Stk(p, 0, tap))))
        prevEnd = tp + tap
    }
    for (n in song.notes) {
        if (n.k <= 0) continue
        val t = (n.t * spb).toLong() + 400
        val r = regIdx(n.o)
        if (r != reg) { press(regBtn(r), t); reg = r }
        val want = n.s == 1
        if (!hold && want != sharp) { press(0, t); sharp = want }
        val tn = maxOf(t, prevEnd + 15)
        val key = 3 + n.k
        if (hold && want) {
            out.add(Act(tn - 40, listOf(Stk(0, 0, tap + 80), Stk(key, 40, tap))))
        } else {
            out.add(Act(tn, listOf(Stk(key, 0, tap))))
        }
        prevEnd = tn + tap
    }
    if (reg != base) press(regBtn(base), prevEnd + 400)
    if (sharp) press(0, prevEnd + 400)
    return out
}
