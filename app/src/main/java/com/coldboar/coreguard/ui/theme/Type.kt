package com.coldboar.coreguard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.R

val SyneFamily = FontFamily(
    Font(R.font.syne_bold, FontWeight.Bold),
    Font(R.font.syne_extrabold, FontWeight.ExtraBold)
)

val ManropeFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold)
)

val CoreGuardTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp
    )
)
