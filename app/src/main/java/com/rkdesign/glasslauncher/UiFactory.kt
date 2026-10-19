package com.rkdesign.glasslauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

internal fun makeLabel(context: Context, text: String, sizeSp: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false): TextView {
    return TextView(context).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
}

internal fun makeGlassButton(context: Context, text: String, onClick: () -> Unit): TextView {
    return TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = context.getDrawable(R.drawable.bg_glass_small)
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        isClickable = true
        isFocusable = true
        setOnClickListener { animateTap(this); onClick() }
    }
}

internal fun animateTap(view: View) {
    if (!LauncherStore.get(view.context).getBoolean("animations", true)) return
    view.animate().cancel()
    view.scaleX = 0.97f
    view.scaleY = 0.97f
    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150L).start()
}

internal fun makeAppTile(
    context: Context,
    app: LaunchableApp,
    labelVisible: Boolean = true,
    iconSizeDp: Int = 44,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): View {
    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(context.dp(2), context.dp(3), context.dp(2), context.dp(2))
        isClickable = true
        isFocusable = true
        contentDescription = app.label
        setOnClickListener { animateTap(this); onClick() }
        if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
    }
    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(context.dp(iconSizeDp), context.dp(iconSizeDp))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageDrawable(app.icon)
        contentDescription = app.label
    }
    column.addView(icon)
    if (labelVisible) {
        val title = TextView(context).apply {
            text = app.label
            textSize = 10.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, context.dp(4), 0, 0)
        }
        column.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(18)))
    }
    return column
}

internal data class LaunchableApp(
    val label: String,
    val packageName: String,
    val icon: Drawable
)
