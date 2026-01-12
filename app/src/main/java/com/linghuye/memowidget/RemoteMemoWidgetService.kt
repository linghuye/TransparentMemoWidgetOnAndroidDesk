package com.linghuye.memowidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class RemoteMemoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return MemoWidgetFactory(applicationContext, intent)
    }
}

class MemoWidgetFactory(private val context: Context, private val intent: Intent) 
    : RemoteViewsService.RemoteViewsFactory 
{
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var fullText: CharSequence = ""
    private var fontSize: Float = 16f
    private var gravity: Int = android.view.Gravity.TOP or android.view.Gravity.START
    private var nWidgetHeightInPixel: Int = 0

    override fun onCreate() {
        appWidgetId = this.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        nWidgetHeightInPixel = this.intent.getIntExtra("widget_height_px", 0)
    }

    override fun onDataSetChanged() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        android.util.Log.d("yelinghuye", "widget[$appWidgetId] onDataSetChanged")
        val jsonData = loadTitlePref(context, appWidgetId)
        fullText = deserializeSpannable(jsonData)
        fontSize = loadFontSizePref(context, appWidgetId)
        gravity = loadGravityPref(context, appWidgetId)
        nWidgetHeightInPixel = this.intent.getIntExtra("widget_height_px", 0)
    }

    override fun getViewAt(position: Int): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.memo_widget_textview_listitem)
        val textViewId = R.id.widget_item_text

        android.util.Log.d("yelinghuye", "widget[$appWidgetId] getViewAt($position)")
        
        // 设置基础大小
        remoteViews.setTextViewTextSize(textViewId, TypedValue.COMPLEX_UNIT_SP, fontSize)
        
        // 设置完整文本
        remoteViews.setTextViewText(textViewId, fullText)
        
        // 设置对齐
        remoteViews.setInt(textViewId, "setGravity", gravity)
        
        // 设置最小高度为 Widget 高度，确保短文本对齐（如居中/底端）生效
        if (nWidgetHeightInPixel > 0) {
            remoteViews.setInt(textViewId, "setMinimumHeight", nWidgetHeightInPixel)
        }

        // 点击事件填充
        remoteViews.setOnClickFillInIntent(textViewId, Intent())
        return remoteViews
    }

    override fun onDestroy() {}
    override fun getCount(): Int = 1
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = 1L
    override fun hasStableIds(): Boolean = true
}
