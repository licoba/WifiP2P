package com.tmk.newfast.widgets

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志显示控件
 */
class LogTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        this.isVerticalScrollBarEnabled = true
        this.movementMethod = android.text.method.ScrollingMovementMethod()
    }

    fun appendText(text: String) {
        val timestamp = getCurrentTimestamp()
        val newText = "$timestamp $text"
        val currentText = this.text.toString()
        val updatedText = "$newText\n$currentText"
        this.text = updatedText
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return dateFormat.format(Date())
    }


}