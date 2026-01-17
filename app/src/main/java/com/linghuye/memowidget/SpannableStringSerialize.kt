package com.linghuye.memowidget

import android.graphics.Typeface
import android.text.*
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import org.json.JSONArray
import org.json.JSONObject

/**
 * 将 SpannableStringBuilder 序列化为 JSON 字符串
 * 支持：文字颜色(ForegroundColorSpan)、字体大小(AbsoluteSizeSpan)、粗体(StyleSpan-BOLD)、斜体(StyleSpan-ITALIC)、下划线(UnderlineSpan)
 * 过滤其他类型的 Span，确保无尾部不可见字符
 */
internal fun serializeSpannable(spannable: SpannableStringBuilder): String {
    // 去除尾部空白字符，确保无不可见字符
    val text = spannable.toString().trimEnd()
    
    val jsonObject = JSONObject()
    jsonObject.put("text", text)
    
    val spansArray = JSONArray()
    
    if (text.isNotEmpty()) {
        // 获取所有 Span
        val allSpans = spannable.getSpans(0, spannable.length, Object::class.java)
        
        for (span in allSpans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            
            // 跳过无效范围或超出文本长度的 Span
            if (start < 0 || end > text.length || start >= end) continue
            
            val spanData = JSONObject()
            spanData.put("start", start)
            spanData.put("end", end)
            
            when (span) {
                is ForegroundColorSpan -> {
                    spanData.put("type", "color")
                    spanData.put("value", span.foregroundColor)
                    spansArray.put(spanData)
                }
                is AbsoluteSizeSpan -> {
                    spanData.put("type", "size")
                    spanData.put("value", span.size) // 使用 px 单位
                    spansArray.put(spanData)
                }
                is StyleSpan -> {
                    when (span.style) {
                        Typeface.BOLD -> {
                            spanData.put("type", "bold")
                            spansArray.put(spanData)
                        }
                        Typeface.ITALIC -> {
                            spanData.put("type", "italic")
                            spansArray.put(spanData)
                        }
                        Typeface.BOLD_ITALIC -> {
                            // 拆分为两个独立的 Span
                            val boldSpan = JSONObject()
                            boldSpan.put("start", start)
                            boldSpan.put("end", end)
                            boldSpan.put("type", "bold")
                            spansArray.put(boldSpan)
                            
                            val italicSpan = JSONObject()
                            italicSpan.put("start", start)
                            italicSpan.put("end", end)
                            italicSpan.put("type", "italic")
                            spansArray.put(italicSpan)
                        }
                    }
                }
                is UnderlineSpan -> {
                    val flags = spannable.getSpanFlags(span)
                    if ((flags and Spanned.SPAN_COMPOSING) == 0) {
                        spanData.put("type", "underline")
                        spansArray.put(spanData)
                    }
                }
                // 过滤其他类型的 Span
            }
        }
    }
    
    jsonObject.put("spans", spansArray)
    return jsonObject.toString()
}

/**
 * 从 JSON 字符串反序列化为 SpannableStringBuilder
 * 支持：文字颜色(ForegroundColorSpan)、字体大小(AbsoluteSizeSpan)、粗体(StyleSpan-BOLD)、斜体(StyleSpan-ITALIC)、下划线(UnderlineSpan)
 * 确保完全恢复样式，无额外不可见字符
 */
internal fun deserializeSpannable(jsonString: String): SpannableStringBuilder {
    try {
        val jsonObject = JSONObject(jsonString)
        val text = jsonObject.optString("text", "")
        
        val spannable = SpannableStringBuilder(text)
        
        if (text.isNotEmpty() && jsonObject.has("spans")) {
            val spansArray = jsonObject.getJSONArray("spans")
            val nSpanCount = spansArray.length()
            
            for (i in 0 until nSpanCount) {
                val spanData = spansArray.getJSONObject(i)
                val type = spanData.optString("type", "")
                var start = spanData.optInt("start", -1)
                var end = spanData.optInt("end", -1)
                
                // 验证范围有效性
                if (start < 0 || end > text.length || start >= end) continue
                
                // 确保不超出文本长度
                start = start.coerceIn(0, text.length)
                end = end.coerceIn(start, text.length)
                
                if (start >= end) continue
                
                when (type) {
                    "color" -> {
                        val color = spanData.optInt("value", 0)
                        spannable.setSpan(
                            ForegroundColorSpan(color),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    "size" -> {
                        val size = spanData.optInt("value", 14)
                        spannable.setSpan(
                            AbsoluteSizeSpan(size, false), // false 表示使用 px 单位
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    "bold" -> {
                        spannable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    "italic" -> {
                        spannable.setSpan(
                            StyleSpan(Typeface.ITALIC),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    "underline" -> {
                        spannable.setSpan(
                            UnderlineSpan(),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
        }   
        return spannable
    } 
    catch (e: Exception) {
        // 解析失败返回空的 SpannableStringBuilder
        return SpannableStringBuilder("")
    }
}