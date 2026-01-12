package com.linghuye.memowidget

import android.content.Context
import android.graphics.Color
import android.view.Gravity

/**
 * 使用 SharedPreferences 处理插件级别的偏好设置的持久化。
 * 每个插件实例（由 appWidgetId 标识）都有自己的一组保存设置。
 */

// SharedPreferences 文件的名称
// `const val` 声明编译期常量
internal const val PREFS_NAME = "com.linghuye.memowidget.MemoWidgetProvider"

// 不同偏好类型的键，后缀为 appWidgetId
private const val PREF_PREFIX_KEY = "appwidget_"
private const val PREF_ALPHA_KEY = "alpha_"
private const val PREF_BG_COLOR_KEY = "bgcolor_"
private const val PREF_GRAVITY_KEY = "gravity_"
private const val PREF_FONT_NAME_KEY = "fontname_"
private const val PREF_FONT_SIZE_KEY = "fontsize_"

// 定义一个私有扩展函数简化获取 Prefs 的过程
private fun Context.prefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

// 定义一个内联扩展函数简化编辑过程
private inline fun Context.editPrefs(action: android.content.SharedPreferences.Editor.() -> Unit) {
    prefs().edit().apply(action).apply()
}

/** 为特定插件保存备忘录文本（以 HTML 格式）。 */
// `internal` 修饰符表示仅在当前模块内可见
internal fun saveTitlePref(ctx: Context, appWidgetId: Int, text: String) { 
    ctx.editPrefs { putString(PREF_PREFIX_KEY + appWidgetId, text) } 
}

/** 为特定插件加载备忘录文本（以 HTML 格式）；如果未找到，则默认为提示字符串。 */
internal fun loadTitlePref(ctx: Context, appWidgetId: Int): String { 
    try {
        return ctx.prefs().getString(PREF_PREFIX_KEY + appWidgetId, null) ?: ctx.getString(R.string.memo_hint) 
    }
    catch(e: Exception) {
        android.util.Log.e("yelinghuye", "loadTitlePref: ${e.message}")
        return ctx.getString(R.string.memo_hint)
    }
}

/** 保存背景透明度（0-255）。 */
internal fun saveBgAlphaPref(ctx: Context, appWidgetId: Int, alpha: Int) { 
    ctx.editPrefs { putInt(PREF_ALPHA_KEY + appWidgetId, alpha) } 
}

/** 加载背景透明度；默认为 0（透明）。 */
internal fun loadBgAlphaPref(ctx: Context, appWidgetId: Int): Int { 
    try {
        return ctx.prefs().getInt(PREF_ALPHA_KEY + appWidgetId, 0) 
    }
    catch(e: Exception) {
        android.util.Log.d("yelinghuye", "loadBgAlphaPref: ${e.message}")
        return 0
    }
}

/** 保存背景颜色整数。 */
internal fun saveBgColorPref(ctx: Context, appWidgetId: Int, color: Int) { 
    ctx.editPrefs { putInt(PREF_BG_COLOR_KEY + appWidgetId, color) } 
}

/** 加载背景颜色；默认为黑色。 */
internal fun loadBgColorPref(ctx: Context, appWidgetId: Int): Int { 
    try {
        return ctx.prefs().getInt(PREF_BG_COLOR_KEY + appWidgetId, Color.BLACK) 
    }
    catch(e: Exception) {
        android.util.Log.e("yelinghuye", "loadBgColorPref: ${e.message}")
        return Color.BLACK
    }
}

/** 保存对齐重力标志。 */
internal fun saveGravityPref(ctx: Context, appWidgetId: Int, gravity: Int) { 
    ctx.editPrefs { putInt(PREF_GRAVITY_KEY + appWidgetId, gravity) } 
}

/** 加载对齐重力；默认为 左上对齐 (Top-Start)。 */
internal fun loadGravityPref(ctx: Context, appWidgetId: Int): Int { 
    try {
        return ctx.prefs().getInt(PREF_GRAVITY_KEY + appWidgetId, Gravity.TOP or Gravity.START) 
    }
    catch(e: Exception) {
        android.util.Log.e("yelinghuye", "loadGravityPref: ${e.message}")
        return Gravity.TOP or Gravity.START
    }
}

/** 保存全局/基础字体大小。 */
internal fun saveFontSizePref(ctx: Context, appWidgetId: Int, size: Float) { 
    ctx.editPrefs { putFloat(PREF_FONT_SIZE_KEY + appWidgetId, size) } 
}

/** 加载全局字体大小；默认为 18sp。 */
internal fun loadFontSizePref(ctx: Context, appWidgetId: Int): Float {
    try {
        return ctx.prefs().getFloat(PREF_FONT_SIZE_KEY + appWidgetId, 16.0f) 
    }
    catch(e: Exception) {
        android.util.Log.e("yelinghuye", "loadFontSizePref: ${e.message}")
        return 16.0f
    }
}

/** 删除特定插件的所有偏好设置（在删除插件时调用）。 */
internal fun deleteTitlePref(ctx: Context, appWidgetId: Int) { 
    try {
        ctx.editPrefs {
            remove(PREF_PREFIX_KEY + appWidgetId)
            remove(PREF_ALPHA_KEY + appWidgetId)
            remove(PREF_BG_COLOR_KEY + appWidgetId)
            remove(PREF_GRAVITY_KEY + appWidgetId)
            remove(PREF_FONT_NAME_KEY + appWidgetId)
            remove(PREF_FONT_SIZE_KEY + appWidgetId)
            apply() 
        }
    } catch(e: Exception) {
        android.util.Log.e("yelinghuye", "deleteTitlePref: ${e.message}")
    } 
}
