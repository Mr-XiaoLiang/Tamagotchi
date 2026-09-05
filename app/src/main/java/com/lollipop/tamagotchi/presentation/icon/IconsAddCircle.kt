package com.lollipop.tamagotchi.presentation.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val add_circle: ImageVector
    get() {
        if (_add_circle != null) {
            return _add_circle!!
        }
        _add_circle =
            ImageVector.Builder(
                name = "add_circle",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
                .apply {
                    path(
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        stroke = null,
                        strokeAlpha = 1f,
                        strokeLineWidth = 1f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Bevel,
                        strokeLineMiter = 1f,
                        pathFillType = PathFillType.Companion.NonZero,
                    ) {
                        moveTo(10.95f, 17.05f)
                        horizontalLineToRelative(2.1f)
                        verticalLineToRelative(-4f)
                        horizontalLineToRelative(4f)
                        verticalLineToRelative(-2.1f)
                        horizontalLineToRelative(-4f)
                        verticalLineToRelative(-4f)
                        horizontalLineToRelative(-2.1f)
                        verticalLineToRelative(4f)
                        horizontalLineToRelative(-4f)
                        verticalLineToRelative(2.1f)
                        horizontalLineToRelative(4f)
                        verticalLineToRelative(4f)
                        close()
                        moveTo(12f, 22.2f)
                        quadToRelative(-2.12f, 0f, -3.98f, -0.8f)
                        reflectiveQuadTo(4.78f, 19.22f)
                        reflectiveQuadTo(2.6f, 15.98f)
                        reflectiveQuadTo(1.8f, 12f)
                        reflectiveQuadTo(2.6f, 8.02f)
                        reflectiveQuadTo(4.78f, 4.78f)
                        reflectiveQuadTo(8.02f, 2.6f)
                        reflectiveQuadTo(12f, 1.8f)
                        reflectiveQuadToRelative(3.98f, 0.8f)
                        reflectiveQuadToRelative(3.24f, 2.18f)
                        reflectiveQuadTo(21.4f, 8.02f)
                        reflectiveQuadTo(22.2f, 12f)
                        reflectiveQuadToRelative(-0.8f, 3.98f)
                        reflectiveQuadToRelative(-2.18f, 3.24f)
                        reflectiveQuadTo(15.98f, 21.4f)
                        reflectiveQuadTo(12f, 22.2f)
                        close()
                        moveToRelative(0f, -2.28f)
                        quadToRelative(3.33f, 0f, 5.63f, -2.3f)
                        reflectiveQuadTo(19.93f, 12f)
                        reflectiveQuadTo(17.63f, 6.37f)
                        reflectiveQuadTo(12f, 4.07f)
                        reflectiveQuadTo(6.37f, 6.37f)
                        reflectiveQuadTo(4.07f, 12f)
                        reflectiveQuadToRelative(2.3f, 5.63f)
                        reflectiveQuadTo(12f, 19.93f)
                        close()
                        moveTo(12f, 12f)
                        close()
                    }
                }
                .build()
        return _add_circle!!
    }

private var _add_circle: ImageVector? = null