package com.linghuye.memowidget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * 一个自定义的 EditText，每当文本选择或光标位置发生变化时，它都会通知监听器。
 * 这用于根据当前光标位置处的文本样式来更新格式工具栏的按钮状态（例如，加粗、倾斜的高亮显示）。
 */
class SelectionAwareEditText : AppCompatEditText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    /**
     * 每当选择或光标位置发生变化时触发的回调。
     */
    var onSelectionChangedListener: (() -> Unit)? = null

    /**
     * 当选择或光标位置发生变化时由系统调用。
     * 我们重写此方法以触发我们的自定义监听器。
     */
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke()
    }
}
