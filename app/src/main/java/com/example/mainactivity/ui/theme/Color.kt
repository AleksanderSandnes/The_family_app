package com.example.mainactivity.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand — refined indigo / violet
val Indigo50 = Color(0xFFEEF0FF)
val Indigo100 = Color(0xFFE0E3FF)
val Indigo200 = Color(0xFFC4C9FF)
val Indigo300 = Color(0xFF9CA3FF)
val Indigo500 = Color(0xFF6366F1)
val Indigo600 = Color(0xFF5457E8)
val Indigo700 = Color(0xFF4338CA)
val Violet500 = Color(0xFF8B5CF6)
val Violet600 = Color(0xFF7C3AED)

// Accent
val Pink500 = Color(0xFFEC4899)
val Teal500 = Color(0xFF14B8A6)
val Amber500 = Color(0xFFF59E0B)
val Emerald500 = Color(0xFF10B981)
val Rose500 = Color(0xFFF43F5E)

// Neutrals (light)
val Slate900 = Color(0xFF0B1020)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Canvas = Color(0xFFF7F8FC)
val SurfaceLight = Color(0xFFFFFFFF)

// Neutrals (dark)
val Ink = Color(0xFF0A0E1A)
val InkSurface = Color(0xFF141A2A)
val InkSurfaceVariant = Color(0xFF1E2638)
val InkBorder = Color(0xFF2A3349)
val InkText = Color(0xFFE8EBF5)
val InkTextMuted = Color(0xFF9AA4BE)

// Signature gradients
val BrandGradient = Brush.linearGradient(listOf(Indigo600, Violet600))
val BrandGradientSoft = Brush.linearGradient(listOf(Indigo500, Violet500))

fun heroGradient(dark: Boolean): Brush =
    if (dark) Brush.linearGradient(listOf(Color(0xFF3730A3), Color(0xFF6D28D9)))
    else Brush.linearGradient(listOf(Indigo600, Violet600))
