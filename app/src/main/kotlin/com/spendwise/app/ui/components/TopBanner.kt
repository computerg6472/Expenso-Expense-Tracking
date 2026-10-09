package com.spendwise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val TopBannerGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1D4ED8), // Deep Royal Blue
        Color(0xFF2563EB)  // Classic Electric Blue
    )
)

@Composable
fun TopBanner(
    modifier: Modifier = Modifier,
    bottomCornerRadius: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = bottomCornerRadius, bottomEnd = bottomCornerRadius),
        shadowElevation = 6.dp,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TopBannerGradient)
                .statusBarsPadding()
                .padding(contentPadding),
            content = content
        )
    }
}
