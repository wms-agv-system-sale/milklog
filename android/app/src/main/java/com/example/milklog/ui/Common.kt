package com.example.milklog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/** 画面归一化坐标（0~1）与屏幕坐标之间的换算（等比填满模式） */
class PreviewGeometry(
    private val frameW: Float,
    private val frameH: Float,
    private val viewW: Float,
    private val viewH: Float
) {
    private val scale: Float =
        if (frameW > 0f && frameH > 0f && viewW > 0f && viewH > 0f) max(viewW / frameW, viewH / frameH) else 1f

    val displayW: Float = frameW * scale
    val displayH: Float = frameH * scale
    private val originX: Float = (viewW - displayW) / 2f
    private val originY: Float = (viewH - displayH) / 2f

    fun pointX(x: Double): Float = originX + x.toFloat() * displayW

    fun pointY(y: Double): Float = originY + y.toFloat() * displayH

    fun bandLeftX(left: Double): Float = pointX(left)

    fun bandRightX(right: Double): Float = pointX(right)

    fun bandTop(): Float = pointY(0.0)

    fun bandBottom(): Float = pointY(1.0)

    /** 屏幕坐标 -> 归一化坐标（越界返回 null） */
    fun normalizedX(x: Float): Double? {
        if (displayW <= 0f) return null
        val value = (x - originX) / displayW
        if (value < 0f || value > 1f) return null
        return value.toDouble()
    }

    fun normalizedY(y: Float): Double? {
        if (displayH <= 0f) return null
        val value = (y - originY) / displayH
        if (value < 0f || value > 1f) return null
        return value.toDouble()
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MetricTile(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}
