package com.stepwatch.app

import android.graphics.Color
import androidx.core.content.ContextCompat

object ColorUtil {
    fun primary(ctx: android.content.Context) = ContextCompat.getColor(ctx, R.color.lemon_primary)
    fun dim(ctx: android.content.Context) = ContextCompat.getColor(ctx, R.color.lemon_text_dim)
    fun surface(ctx: android.content.Context) = ContextCompat.getColor(ctx, R.color.lemon_surface)
}