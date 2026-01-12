package com.linghuye.memowidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.*
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.tabs.TabLayout
import com.linghuye.memowidget.R
import kotlin.math.min
import java.util.*

/**
 * 备忘录插件的配置/编辑界面。
 * 此 Activity 处理文本编辑、格式设置、颜色选择和对齐方式。
 * 使用选项卡式布局 (TabLayout) 来组织不同的控制面板。
 */
class MemoWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var appWidgetText: SelectionAwareEditText
    private lateinit var seekBgAlpha: SeekBar
    private lateinit var editHexColor: EditText
    private lateinit var editBgHexColor: EditText

    // 选项卡面板（编辑/格式、文本颜色、背景颜色）
    private lateinit var panelFormat: View
    private lateinit var panelTextColor: View
    private lateinit var panelBgColor: View

    // 持久状态变量
    private var currentGravity = Gravity.TOP or Gravity.START
    private var currentFontGlobalSize = 16.0f
    private var hadNotifyHost = false
    private var isReadyResumeWork = false

    // 用于保存文本状态（包括内容和光标位置）的类
    data class TextState(
        val content: SpannableStringBuilder,
        val selectionStart: Int,
        val selectionEnd: Int
    )
    
    // 撤销/重做 (Undo/Redo) 历史堆栈
    private val undoStack = Stack<TextState>()
    private val redoStack = Stack<TextState>()
    private val MAX_UNDO_STACK_SIZE = 128
    
    // 用于控制撤销/重做状态保存的标志
    private var isUndoRedoOperation = false

    // 预定义的 24 色调色板，方便选择
    private val colors24 = arrayOf(
        // Grays
        "#FFFFFF", "#8E8E93", "#48484A", "#000000",
        // Reds/Pinks
        "#FF3B30", "#FF2D55", "#E11D48", "#BE123C",
        // Oranges/Yellows
        "#FF9500", "#FFCC00", "#F59E0B", "#D97706",
        // Greens
        "#34C759", "#10B981", "#059669", "#064E3B",
        // Blues/Cyans
        "#007AFF", "#5AC8FA", "#0EA5E9", "#0369A1",
        // Purples/Indigos
        "#AF52DE", "#5856D6", "#7C3AED", "#4338CA"
    )

    // 对齐方式网格按钮及其对应的 Gravity 标志 ID
    private val alignIds = intArrayOf(R.id.align_tl, R.id.align_tc, R.id.align_tr, R.id.align_cl, R.id.align_cc, R.id.align_cr, R.id.align_bl, R.id.align_bc, R.id.align_br)
    private val gravities = intArrayOf(
        Gravity.TOP or Gravity.START, Gravity.TOP or Gravity.CENTER_HORIZONTAL, Gravity.TOP or Gravity.END,
        Gravity.CENTER_VERTICAL or Gravity.START, Gravity.CENTER, Gravity.CENTER_VERTICAL or Gravity.END,
        Gravity.BOTTOM or Gravity.START, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM or Gravity.END
    )

    // 颜色调色板的 ScrollView 引用
    private lateinit var textColorScrollView: HorizontalScrollView
    private lateinit var bgColorScrollView: HorizontalScrollView

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setResult(RESULT_CANCELED)
        
        android.util.Log.i("yelinghuye", "onCreate MemoWidgetConfigureActivity")
        setContentView(R.layout.memo_widget_configure)
        
        // 准备好编辑控件
        prepareEditControls();

        // 设置各种事件监听器, 然后程序跑起来。
        setupAllEditControlsEventsListeners();
    }

    // 当Activity已经存在(没有被销毁)时,点击其他widget不会创建新的Activity实例,
    // 而是复用现有的Activity。新的 Intent 会通过 onNewIntent() 方法传递,
    // 若不处理, getIntent()永远会返回第一次启动时的旧Intent。
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        android.util.Log.i("yelinghuye", "widget onNewIntent[${intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)}]")
        setIntent(intent)
    }

    override fun onResume() {
        hadNotifyHost = false

        // 从 Intent extras 中检索插件 ID
        appWidgetId =  intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        android.util.Log.i("yelinghuye", "widget[$appWidgetId] onResume")
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            super.onResume()
            this.finish()
            return
        }

        // 从后台数据加载当前的整体编辑状态
        restoreAllEditStatusFromSharedPrefs(appWidgetId)
        isReadyResumeWork = true

        appWidgetText.requestFocus()
        super.onResume()
    }

    override fun onPause() {
        android.util.Log.i("yelinghuye", "widget[$appWidgetId] onPause")
        if(!hadNotifyHost) {
            notifyDeskHostWidgetReady()
        }
        isReadyResumeWork = false
        super.onPause()
    }

    private fun notifyDeskHostWidgetReady() {
        // 保存当前编辑状态到 SharedPreferences
        this.saveAllEditStatusToSharedPrefs(appWidgetId)
        
        // 触发物理插件更新
        updateAppWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
        
        // 通知宿主,配置成功,并返回appWidgetId
        setResult(RESULT_OK, intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        hadNotifyHost = true
    }

    private fun prepareEditControls() {
        // 绑定 UI 元素
        appWidgetText = findViewById(R.id.appwidget_text)
        seekBgAlpha = findViewById(R.id.seek_bg_alpha)
        editHexColor = findViewById(R.id.edit_hex_color)
        editBgHexColor = findViewById(R.id.edit_bg_hex_color)

        // 获取颜色调色板的 ScrollView 引用
        textColorScrollView = findViewById(R.id.text_color_scroll_view)
        bgColorScrollView = findViewById(R.id.bg_color_scroll_view)

        panelFormat = findViewById(R.id.panel_format)
        panelTextColor = findViewById(R.id.panel_text_color)
        panelBgColor = findViewById(R.id.panel_bg_color)

        // 24 色调色板网格初始化
        setupColorPaletteGrid(findViewById(R.id.text_color_container_grid)) { colorStr ->
            editHexColor.setText(colorStr.substring(1))
        }

        setupColorPaletteGrid(findViewById(R.id.bg_color_container_grid)) { colorStr ->
            editBgHexColor.setText(colorStr.substring(1))
        }
    }

    private fun saveAllEditStatusToSharedPrefs(appWidgetId: Int) {
        // 创建副本以修改，避免改动原始编辑框
        val spannableContent = SpannableStringBuilder(appWidgetText.text ?: "")
        saveTitlePref(this, appWidgetId, serializeSpannable(spannableContent))
        saveBgAlphaPref(this, appWidgetId, seekBgAlpha.progress)
        saveGravityPref(this, appWidgetId, currentGravity)
        saveFontSizePref(this, appWidgetId, currentFontGlobalSize)
        saveBgColorPref(this, appWidgetId, Color.parseColor("#" + editBgHexColor.text.toString()))
        saveUndoRedoHistoryToSharedPrefs();
    }

    // 使用从 SharedPreferences 加载的数据恢复编辑文本
    private fun restoreAllEditStatusFromSharedPrefs(appWidgetId: Int) {
        // 恢复文本状态
        val strContent = loadTitlePref(this, appWidgetId)
        appWidgetText.setText(deserializeSpannable(strContent))

        // 立即应用背景色和透明度到编辑框
        val savedBgColor = loadBgColorPref(this, appWidgetId)
        editBgHexColor.setText(String.format("%06X", 0xFFFFFF and savedBgColor))
        seekBgAlpha.progress = loadBgAlphaPref(this, appWidgetId)
        applyBackgroundColorAndAlpha()

        // 恢复对齐方式
        currentGravity = loadGravityPref(this, appWidgetId)
        appWidgetText.gravity = currentGravity

        // 恢复全局文本大小
        currentFontGlobalSize = loadFontSizePref(this, appWidgetId)
        appWidgetText.textSize = currentFontGlobalSize.toFloat()

        // 历史管理：文本更改时自动保存状态，用于撤销 (Undo) 功能
        this.loadUndoRedoStateFromSharedPrefs()

        updateAlignmentUI()
    }

    private fun setupAllEditControlsEventsListeners() {
        // TabLayout 监听器，用于在格式化、文本颜色和背景颜色面板之间进行切换
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: return
                panelFormat.visibility = if (position == 0) View.VISIBLE else View.GONE
                panelTextColor.visibility = if (position == 1) View.VISIBLE else View.GONE
                panelBgColor.visibility = if (position == 2) View.VISIBLE else View.GONE
                updateUIFeedback()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // 格式化工具栏按钮监听器
        findViewById<View>(R.id.btn_undo).setOnClickListener { undoLastEdit() }
        findViewById<View>(R.id.btn_redo).setOnClickListener { redoLastEdit() }
        findViewById<View>(R.id.btn_bold).setOnClickListener { toggleStyleSpan(Typeface.BOLD); updateUIFeedback() }
        findViewById<View>(R.id.btn_italic).setOnClickListener { toggleStyleSpan(Typeface.ITALIC); updateUIFeedback() }
        findViewById<View>(R.id.btn_underline).setOnClickListener { toggleUnderlineSpan(); updateUIFeedback() }
        findViewById<View>(R.id.btn_size_up).setOnClickListener { changeEditingTextFontSize(1.0f) }
        findViewById<View>(R.id.btn_size_down).setOnClickListener { changeEditingTextFontSize(-1.0f) }

        // HEX颜色代码输入监听
        setupHexColorEditWatcher(editHexColor, { 
            if(!isReadyResumeWork) return@setupHexColorEditWatcher
            applyTextColor(it)
            updateUIFeedback()
        })
        setupHexColorEditWatcher(editBgHexColor, { 
            if(!isReadyResumeWork) return@setupHexColorEditWatcher
            applyBackgroundColorAndAlpha()
            updateUIFeedback()
        })

        // SeekBar 滑动监听器，实时更新背景色
        seekBgAlpha.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if(!isReadyResumeWork) return
                applyBackgroundColorAndAlpha()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 光标位置追踪，用于实时按钮高亮
        appWidgetText.onSelectionChangedListener = { 
            if(isReadyResumeWork) {
                updateUIFeedback() 
            }
        }

        // 对齐方式 9 宫格按钮设置
        for (i in alignIds.indices) {
            findViewById<View>(alignIds[i]).setOnClickListener {
                this.onSetGravityButtonClick(gravities[i])
            }
        }

        // 保存按钮
        findViewById<View>(R.id.save_button).setOnClickListener {
            this.notifyDeskHostWidgetReady();
            this.finish()
        }

        //  文本更改监听器，用于自动保存状态
        appWidgetText.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if(!isReadyResumeWork) return
                if(isUndoRedoOperation) return

                // 只有在非撤销/重做操作时才保存状态
                // Note: 设置字体样式,颜色,大小不会激发beforeTextChanged!!!
                // 用户编辑操作激发的文本变动都会导致redo栈清空
                redoStack.clear()
                saveCurrentTextStateForUndo()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun onSetGravityButtonClick(gravity: Int) {
        currentGravity = gravity
        appWidgetText.gravity = currentGravity
        updateAlignmentUI()
    }

    /** 更新所有 UI 元素（字体、颜色、样式）的突出显示状态，取决于当前光标/选择位置。 */
    private fun updateUIFeedback() {
        updateStyleToolbarHighlighting()
        updateColorPaletteToolbarHighlights()
    }

    /** 如果光标/选择位置处的文本具有这些样式，则突出显示加粗/倾斜/下划线按钮。 */
    private fun updateStyleToolbarHighlighting() {
        val selectionStart = appWidgetText.selectionStart
        val spannableContent = appWidgetText.text

        val styleSpans = if (selectionStart >= 0 && spannableContent != null) {
            spannableContent.getSpans(selectionStart, selectionStart + 1, StyleSpan::class.java)
        } else emptyArray()

        val underlineSpans = if (selectionStart >= 0 && spannableContent != null) {
            spannableContent.getSpans(selectionStart, selectionStart + 1, UnderlineSpan::class.java)
        } else emptyArray()

        val isBold = styleSpans.any { it.style == Typeface.BOLD }
        val isItalic = styleSpans.any { it.style == Typeface.ITALIC }
        val isUnderline = underlineSpans.isNotEmpty()

        // Kotlin 的 `if` 表达式可以直接作为值赋给 setBackgroundColor
        val activeColor = Color.parseColor("#0A84FF")
        val inactiveColor = Color.parseColor("#3A3A3C")
        findViewById<View>(R.id.btn_bold).setBackgroundColor(if (isBold) activeColor else inactiveColor)
        findViewById<View>(R.id.btn_italic).setBackgroundColor(if (isItalic) activeColor else inactiveColor)
        findViewById<View>(R.id.btn_underline).setBackgroundColor(if (isUnderline) activeColor else inactiveColor)
    }

    /** 如果调色板中的颜色盒与当前的背景或选中的文本颜色相匹配，则突出显示它们。 */
    private fun updateColorPaletteToolbarHighlights() {
        // 更新文本颜色调色板的选择
        val textGrid = findViewById<GridLayout>(R.id.text_color_container_grid)
        val currentTextColorHex = try {
            val start = appWidgetText.selectionStart
            val end = appWidgetText.selectionEnd
            val spannable = appWidgetText.text ?: SpannableStringBuilder("")

            // 如果有选中文本，获取选中文本第一个字的颜色；否则获取光标位置的颜色
            val colorCheckPos = if (start != end && start < spannable.length) start else start.coerceAtMost(spannable.length)
            val spans = spannable.getSpans(colorCheckPos, (colorCheckPos + 1).coerceAtMost(spannable.length), ForegroundColorSpan::class.java)
            if (spans.isNotEmpty()) String.format("#%06X", 0xFFFFFF and spans[0].foregroundColor).uppercase() else null
        } 
        catch (e: Exception) 
        { } ?: ("#" + editHexColor.text.toString().uppercase())

        var selectedTextColorView: View? = null
        for (i in 0 until textGrid.childCount) {
            val v = textGrid.getChildAt(i)
            val color = v.tag as? String
            if (color == currentTextColorHex) {
                v.setPadding(4, 4, 4, 4)
                v.setBackgroundColor(Color.WHITE) // Border
                selectedTextColorView = v
            } else {
                v.setPadding(0, 0, 0, 0)
                v.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        // 自动滚动文本颜色调色板，确保选中的颜色块可见
        if (selectedTextColorView != null) {
            textColorScrollView.post {
                val scrollX = selectedTextColorView.left - (textColorScrollView.width - selectedTextColorView.width) / 2
                textColorScrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
            }
        }

        // 更新背景颜色调色板的选择
        val bgGrid = findViewById<GridLayout>(R.id.bg_color_container_grid)
        val currentBgColorHex = "#" + editBgHexColor.text.toString().uppercase()

        var selectedBgColorView: View? = null
        for (i in 0 until bgGrid.childCount) {
            val v = bgGrid.getChildAt(i)
            val color = v.tag as? String
            if (color == currentBgColorHex) {
                v.setPadding(4, 4, 4, 4)
                v.setBackgroundColor(Color.WHITE)
                selectedBgColorView = v
            } else {
                v.setPadding(0, 0, 0, 0)
                v.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        // 自动滚动背景颜色调色板，确保选中的颜色块可见
        if (selectedBgColorView != null) {
            bgColorScrollView.post {
                val scrollX = selectedBgColorView.left - (bgColorScrollView.width - selectedBgColorView.width) / 2
                bgColorScrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
            }
        }
    }

    /** 在 9 宫格中突出显示当前的对齐方式图标。 */
    private fun updateAlignmentUI() {
        for (i in alignIds.indices) {
            val v = findViewById<View>(alignIds[i])
            if (gravities[i] == currentGravity) {
                v.setBackgroundColor(Color.parseColor("#0A84FF"))
            } 
            else {
                v.setBackgroundColor(Color.parseColor("#3A3A3C"))
            }
        }
    }

    /** 使用 24 个彩色方块填充调色板网格。 */
    private fun setupColorPaletteGrid(grid: GridLayout, onSelected: (String) -> Unit) {
        grid.removeAllViews()
        for (color in colors24) {
            val container = FrameLayout(this)
            val size = (32 * resources.displayMetrics.density).toInt()
            val params = GridLayout.LayoutParams()
            params.width = size; params.height = size
            params.setMargins(0, 0, (4 * resources.displayMetrics.density).toInt(), (4 * resources.displayMetrics.density).toInt())
            container.layoutParams = params
            container.tag = color.uppercase()

            val inner = View(this)
            val innerParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            inner.layoutParams = innerParams
            inner.setBackgroundColor(Color.parseColor(color))

            container.addView(inner)
            container.setOnClickListener { onSelected(color) }
            grid.addView(container)
        }
    }

    /** 监视 HEX 代码 EditTexts，确保输入的是有效的 6 位颜色代码。 */
    private fun setupHexColorEditWatcher(editText: EditText, onColorChanged: (Int) -> Unit) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(s?.length == 6) { 
                    try { 
                        onColorChanged(Color.parseColor("#$s")) 
                    }
                    catch(e: Exception) {
                    } 
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /** 为撤销 (Undo) 历史，记录快照当前的编辑器状态。 */
    private fun saveCurrentTextStateForUndo() {
        // 避免在撤销/重做过程中保存状态
        if (isUndoRedoOperation) return
        
        // 保存当前状态（包括文本内容t和光标位置）到撤销栈
        val currentTextState = TextState(
            SpannableStringBuilder(appWidgetText.text),
            appWidgetText.selectionStart,
            appWidgetText.selectionEnd
        )
        undoStack.push(currentTextState)
        
        // 限制撤销栈大小，避免内存占用过大
        if (undoStack.size > MAX_UNDO_STACK_SIZE) {
            undoStack.removeFirst()
        }
    }

    /** 恢复到上一个文本状态。 */
    private fun undoLastEdit() {
        if (undoStack.size < 1) return
        
        // 为redo保存当前状态
        val currentTextState = TextState(
            SpannableStringBuilder(appWidgetText.text),
            appWidgetText.selectionStart,
            appWidgetText.selectionEnd
        )
        redoStack.push(currentTextState)

        // 弹出并应用前一个状态
        restoreTextState(undoStack.pop())
    }

    /** 在历史堆栈中向前移动。 */
    private fun redoLastEdit() {
        if(redoStack.isNotEmpty()) {
            // 为再次的反悔保存当前状态
            saveCurrentTextStateForUndo()
            // 前进到redo栈顶状态
            restoreTextState(redoStack.pop())
        }
    }

    private fun setTextStateDirectly(content: Spannable, selectionStart: Int = 0, selectionEnd: Int = 0) {
        // 设置撤销标志，避免在setText时再次触发保存状态
        isUndoRedoOperation = true
        appWidgetText.setText(content, TextView.BufferType.SPANNABLE)
        appWidgetText.setSelection(selectionStart, selectionEnd)
        isUndoRedoOperation = false
    }

    // 恢复之前保存的文本内容和光标位置
    private fun restoreTextState(state: TextState) {
        this.setTextStateDirectly(state.content, state.selectionStart, state.selectionStart)
    }

    /** 保存撤销/重做状态到 SharedPreferences。 */
    // 退出时才需要保存，编辑状态下都在内存中。
    private fun saveUndoRedoHistoryToSharedPrefs() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            // 使用 map 和 joinToString 一行搞定转换逻辑
            val undoData = undoStack.joinToString("\n<<<UNDO_SEP>>>\n") { item ->
                val html = Html.toHtml(item.content, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
                "$html|SELECTION|${item.selectionStart}|${item.selectionEnd}"
            }
            putString("undo_stack_$appWidgetId", undoData)

            val redoData = redoStack.joinToString("\n<<<REDO_SEP>>>\n") { item ->
                val html = Html.toHtml(item.content, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
                "$html|SELECTION|${item.selectionStart}|${item.selectionEnd}"
            }
            putString("redo_stack_$appWidgetId", redoData)
            
            apply()
        }
    }

    /** 从 SharedPreferences 恢复撤销/重做状态。 */
    private fun loadUndoRedoStateFromSharedPrefs() {
        val sharedrefs = this.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val undoData = sharedrefs.getString("undo_stack_$appWidgetId", "")
        val redoData = sharedrefs.getString("redo_stack_$appWidgetId", "")

        // 从 SharedPreferences 恢复撤销栈
        if (!undoData.isNullOrEmpty()) {
            undoStack.clear()

            // `split` 是字符串扩展函数，将字符串按分隔符分割为数组
            val undoItems = undoData.split("\n<<<UNDO_SEP>>>\n")
            for (encodedHtml in undoItems) {
                if (encodedHtml.isNotEmpty()) {
                    // 解码文本内容和光标位置
                    var htmlContent = encodedHtml
                    var selectionStart = 0
                    var selectionEnd = 0
                    
                    if (encodedHtml.contains("|SELECTION|")) {
                        val parts = encodedHtml.split("|SELECTION|")
                        htmlContent = parts[0]
                        if (parts.size > 1) {
                            val positions = parts[1].split('|')
                            if (positions.size >= 2) {
                                selectionStart = positions[0].toIntOrNull() ?: 0
                                selectionEnd = positions[1].toIntOrNull() ?: 0
                            }
                        }
                    }
                    
                    val spanned = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY)
                    val textState = TextState(
                        SpannableStringBuilder(spanned),
                        selectionStart,
                        selectionEnd
                    )
                    undoStack.push(textState)
                }
            }
        }

        // 从 SharedPreferences 恢复重做栈
        if (!redoData.isNullOrEmpty()) {
            redoStack.clear()
            val redoItems = redoData.split("\n<<<REDO_SEP>>>\n")
            for (encodedHtml in redoItems) {
                if (encodedHtml.isNotEmpty()) {
                    // 解码文本内容和光标位置
                    var htmlContent = encodedHtml
                    var selectionStart = 0
                    var selectionEnd = 0
                    
                    if (encodedHtml.contains("|SELECTION|")) {
                        val parts = encodedHtml.split("|SELECTION|")
                        htmlContent = parts[0]
                        if (parts.size > 1) {
                            val positions = parts[1].split('|')
                            if (positions.size >= 2) {
                                selectionStart = positions[0].toIntOrNull() ?: 0
                                selectionEnd = positions[1].toIntOrNull() ?: 0
                            }
                        }
                    }
                    
                    val spanned = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY)
                    val textState = TextState(
                        SpannableStringBuilder(spanned),
                        selectionStart,
                        selectionEnd
                    )
                    redoStack.push(textState)
                }
            }
        }
    }
    
    /** 对当前选择的文件应用文本颜色。 */
    private fun applyTextColor(color: Int) {
        val start = appWidgetText.selectionStart
        val end = appWidgetText.selectionEnd
        if (start != end) {
            // 字体颜色变化不会激发beforeTextChanged!!!
            saveCurrentTextStateForUndo();

            val spannable = appWidgetText.text as? Spannable ?: return
            spannable.getSpans(start, end, ForegroundColorSpan::class.java).forEach { spannable.removeSpan(it) }
            spannable.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** 将背景色和透明度应用到编辑框。 */
    private fun applyBackgroundColorAndAlpha() {
        val bgColorStr = editBgHexColor.text.toString()
        if (bgColorStr.length == 6) {
            val bgColor = Color.parseColor("#" + bgColorStr)
            val alpha = seekBgAlpha.progress
            val argbColor = Color.argb(alpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
            appWidgetText.setBackgroundColor(argbColor)
        }
    }
    
    /** 对文本选择应用或移除加粗/倾斜样式。 */
    private fun toggleStyleSpan(style: Int) {
        val start = appWidgetText.selectionStart
        val end = appWidgetText.selectionEnd
        if (start == end) return

        // 字体样式变化不会激发beforeTextChanged!!!
        saveCurrentTextStateForUndo();

        // `as?` 安全类型转换，失败时返回 null 并通过 `?: return` 提前返回
        val spannable = appWidgetText.text as? Spannable ?: return
        val spans = spannable.getSpans(start, end, StyleSpan::class.java)
        var bRemoved = false
        
        for (span in spans) {
            if (span.style == style) { 
                spannable.removeSpan(span); bRemoved = true 
            }
        }

        if (!bRemoved) {
            spannable.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** 对文本选择应用或移除下划线样式。 */
    private fun toggleUnderlineSpan() {
        val start = appWidgetText.selectionStart
        val end = appWidgetText.selectionEnd
        if (start == end) return

        // 下划线样式变化不会激发beforeTextChanged!!!
        saveCurrentTextStateForUndo();

        val spannable = appWidgetText.text as? Spannable ?: return
        val spans = spannable.getSpans(start, end, UnderlineSpan::class.java)
        var bRemoved = false
        
        for (span in spans) {
            spannable.removeSpan(span)
            bRemoved = true
        }

        if (!bRemoved) {
            spannable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** 局部(如果选中)或全局(如果没有选中)更改字体大小。 */
    private fun changeEditingTextFontSize(deltaSp: Float) {
        val selectionStart = appWidgetText.selectionStart
        val selectionEnd = appWidgetText.selectionEnd
        if (selectionStart != selectionEnd) 
        {
            val deltaPx = (deltaSp * resources.displayMetrics.density * resources.configuration.fontScale).toInt()
            
            // `as?` 是 Kotlin 的安全类型转换，如果转换失败则返回 null，而不是抛出异常
            val spannable = appWidgetText.text as? Spannable ?: return

            // 1. 获取选择范围第一个字符的当前大小（px）
            val firstCharSpans = spannable.getSpans(selectionStart, selectionStart + 1, AbsoluteSizeSpan::class.java)
            
            // Kotlin 的 `if` 是表达式，可以作为值赋给变量
            val currentSize: Int = if (firstCharSpans.isNotEmpty()) {
                firstCharSpans[0].size
            } 
            else {
                appWidgetText.textSize.toInt()
            }

            // 2. 计算新大小：使用 `coerceIn` 确保数值在指定范围内，无需手动 if/else
            val newSize = (currentSize + deltaPx).coerceIn(8, 256)

            // 3. 获取选择范围内及边界上的所有AbsoluteSizeSpan
            val allSpans = spannable.getSpans(0, spannable.length, AbsoluteSizeSpan::class.java)
            
            // 4. 处理需要拆分的span（选择范围边界上的span）
            for (span in allSpans) {  // Kotlin 的 for-in 语法，自动调用 iterator()
                val spanStart = spannable.getSpanStart(span)
                val spanEnd = spannable.getSpanEnd(span)
                val spanSize = span.size
                
                // 检查是否需要拆分
                val needsSplitStart = spanStart < selectionStart && spanEnd > selectionStart
                val needsSplitEnd = spanStart < selectionEnd && spanEnd > selectionEnd
                
                if (needsSplitStart || needsSplitEnd) {
                    // 移除原span
                    spannable.removeSpan(span)
                    
                    if (needsSplitStart && needsSplitEnd) {
                        // 选择范围完全在span内部，需要拆分成3段
                        if (spanStart < selectionStart) {
                            spannable.setSpan(AbsoluteSizeSpan(spanSize, false), spanStart, selectionStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        if (selectionEnd < spanEnd) {
                            spannable.setSpan(AbsoluteSizeSpan(spanSize, false), selectionEnd, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    } else if (needsSplitStart) {
                        // 选择范围起始在span内部，拆分成2段
                        if (spanStart < selectionStart) {
                            spannable.setSpan(AbsoluteSizeSpan(spanSize, false), spanStart, selectionStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    } else if (needsSplitEnd) {
                        // 选择范围结束在span内部，拆分成2段
                        if (selectionEnd < spanEnd) {
                            spannable.setSpan(AbsoluteSizeSpan(spanSize, false), selectionEnd, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
            }
            
            // 5. 移除选择范围内完全包含的所有span
            val spansToRemove = spannable.getSpans(selectionStart, selectionEnd, AbsoluteSizeSpan::class.java)
            for (span in spansToRemove) {
                val spanStart = spannable.getSpanStart(span)
                val spanEnd = spannable.getSpanEnd(span)
                // 只移除完全在选择范围内的span（边界上的已经在上面处理了）
                if (spanStart >= selectionStart && spanEnd <= selectionEnd) {
                    spannable.removeSpan(span)
                }
            }
            
            // 6. 为整个选择范围创建一个新的span
            spannable.setSpan(AbsoluteSizeSpan(newSize, false), selectionStart, selectionEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            // 7. 刷新显示：保存光标位置后重新设置文本，触发重新布局
            setTextStateDirectly(spannable, selectionStart, selectionEnd)
        } 
        else 
        {
            // 应用全局字体大小 if NO selection
            // `coerceIn` 确保字体大小在合理范围内
            currentFontGlobalSize = (currentFontGlobalSize + deltaSp).coerceIn(8.0f, 96.0f)
            appWidgetText.textSize = currentFontGlobalSize
        }
    }
}