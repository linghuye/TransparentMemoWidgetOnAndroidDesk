package com.linghuye.memowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import android.appwidget.AppWidgetHostView
import android.content.ComponentName
/**
 * 桌面插件 (App Widget) 功能的实现。
 */
class MemoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(ctx, appWidgetManager, appWidgetId, false)
        }
    }

    override fun onDeleted(ctx: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            deleteTitlePref(ctx, appWidgetId)
        }
    }

    // 当 Widget 大小改变时调用，确保我们能传给 Service 最新的高度。
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        updateAppWidget(context, appWidgetManager, appWidgetId, false)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}

internal fun updateAppWidget(ctx: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, isConfigureChanged: Boolean) {
    // RemoteViews只是个数据包，不是实际的View
    val remoteListViews = RemoteViews(ctx.packageName, R.layout.memo_widget_layout)

    // 背景颜色
    val nAlpha = loadBgAlphaPref(ctx, appWidgetId)
    val bgColor = loadBgColorPref(ctx, appWidgetId)
    val finalBgColor = Color.argb(nAlpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
    remoteListViews.setInt(R.id.widget_container, "setBackgroundColor", finalBgColor)
    
    // 获取当前 Widget 的高度并转换为像素，传给 Service
    // 手机总是竖屏,直接用OPTION_APPWIDGET_MAX_HEIGHT。如果是横屏，需要分别处理。
    val appWidgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId)
    val appWidgetHeightDp = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100)
    var appWidgetHeightPx = (ctx.resources.displayMetrics.density * appWidgetHeightDp).toInt()
    
    // OEM产商会定制padding，需要加上
    val paddingRect = AppWidgetHostView.getDefaultPaddingForWidget(ctx, ComponentName(ctx, MemoWidgetProvider::class.java), null)
    appWidgetHeightPx += paddingRect.top;// // This is a magic number

    // 设置用于Adapter启动RemoteMemoWidgetService的Intent
    // 这个Intent会传递给RemoteMemoWidgetService，
    // RemoteMemoWidgetService会根据这个Intent获取Widget的ID和高度
    val listviewServiceIntent = Intent(ctx, RemoteMemoWidgetService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        putExtra("widget_height_px", appWidgetHeightPx)
        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
    }
    remoteListViews.setRemoteAdapter(R.id.widget_list_view, listviewServiceIntent)
    
    // 设置用于点击配置界面启动的PendingIntent
    // 这个Intent会传递给MemoWidgetConfigureActivity，
    // MemoWidgetConfigureActivity会根据这个Intent获取Widget的ID
    val onClickIntent = Intent(ctx, MemoWidgetConfigureActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
    val onClickPendingIntent = PendingIntent.getActivity(ctx, appWidgetId, onClickIntent, 
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    // ListView 的点击模板和容器点击
    remoteListViews.setPendingIntentTemplate(R.id.widget_list_view, onClickPendingIntent)
    
    // 通知数据刷新
    android.util.Log.d("yelinghuye", "widget[$appWidgetId] notifyAppWidgetViewDataChanged")
    appWidgetManager.updateAppWidget(appWidgetId, remoteListViews)
    if(isConfigureChanged)
    {
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
    }
}
