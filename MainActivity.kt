package com.hmc.auto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var et: EditText
    private lateinit var info: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        root.addView(TextView(this).apply {
            text = "1. Bật dịch vụ trợ năng \"Harmonica Auto\".\n" +
                "2. Dán JSON từ Sheet Maker rồi Lưu bài (hoặc mở file JSON).\n" +
                "3. Vào game, mở kèn, xoay ngang, bấm Căn trên bảng nổi và chạm lần lượt 12 nút theo hướng dẫn.\n" +
                "4. Bấm ▶ trên bảng nổi."
        })
        root.addView(Button(this).apply {
            text = "Mở cài đặt trợ năng"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        et = EditText(this).apply { hint = "Dán JSON bài nhạc"; minLines = 6; maxLines = 10 }
        root.addView(et)
        root.addView(Button(this).apply { text = "Lưu bài"; setOnClickListener { save(et.text.toString()) } })
        root.addView(Button(this).apply {
            text = "Mở file JSON"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                }, 1)
            }
        })
        info = TextView(this)
        root.addView(info)
        setContentView(ScrollView(this).apply { addView(root) })
        getSharedPreferences(PREFS, MODE_PRIVATE).getString("song", null)?.let { et.setText(it); describe(it) }
    }

    private fun describe(js: String) {
        info.text = try {
            val s = parseSong(js)
            "Bài đang nạp: ${s.notes.count { it.k > 0 }} nốt, bpm ${s.bpm}"
        } catch (e: Exception) { "JSON lỗi" }
    }

    private fun save(js: String) {
        try { parseSong(js) } catch (e: Exception) { info.text = "JSON lỗi: ${e.message}"; return }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("song", js).apply()
        describe(js)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
        et.setText(txt); save(txt)
    }
}
