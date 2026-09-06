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
val bubble_chart: ImageVector
    get() {
        if (_bubble_chart != null) {
            return _bubble_chart!!
        }
        _bubble_chart =
            ImageVector.Builder(
                name = "bubble_chart",
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
                        moveTo(12.33f, 20.5f)
                        quadTo(11.39f, 19.57f, 11.39f, 18.24f)
                        reflectiveQuadToRelative(0.93f, -2.27f)
                        reflectiveQuadToRelative(2.26f, -0.94f)
                        reflectiveQuadToRelative(2.27f, 0.94f)
                        reflectiveQuadToRelative(0.94f, 2.27f)
                        reflectiveQuadTo(16.86f, 20.5f)
                        reflectiveQuadToRelative(-2.27f, 0.94f)
                        reflectiveQuadTo(12.33f, 20.5f)
                        close()
                        moveToRelative(2.92f, -1.6f)
                        quadToRelative(0.27f, -0.27f, 0.27f, -0.66f)
                        reflectiveQuadTo(15.25f, 17.58f)
                        reflectiveQuadTo(14.59f, 17.31f)
                        reflectiveQuadToRelative(-0.66f, 0.27f)
                        reflectiveQuadToRelative(-0.26f, 0.66f)
                        reflectiveQuadToRelative(0.26f, 0.66f)
                        reflectiveQuadToRelative(0.66f, 0.27f)
                        reflectiveQuadTo(15.25f, 18.9f)
                        close()
                        moveTo(12.46f, 12.3f)
                        quadTo(10.8f, 10.64f, 10.8f, 8.26f)
                        reflectiveQuadTo(12.46f, 4.22f)
                        reflectiveQuadTo(16.5f, 2.56f)
                        reflectiveQuadToRelative(4.04f, 1.66f)
                        reflectiveQuadTo(22.2f, 8.26f)
                        reflectiveQuadTo(20.54f, 12.3f)
                        reflectiveQuadTo(16.5f, 13.96f)
                        reflectiveQuadTo(12.46f, 12.3f)
                        close()
                        moveToRelative(6.48f, -1.61f)
                        quadTo(19.93f, 9.71f, 19.93f, 8.26f)
                        reflectiveQuadTo(18.94f, 5.82f)
                        reflectiveQuadTo(16.5f, 4.83f)
                        reflectiveQuadTo(14.06f, 5.82f)
                        reflectiveQuadTo(13.07f, 8.26f)
                        reflectiveQuadToRelative(0.99f, 2.43f)
                        reflectiveQuadToRelative(2.44f, 0.99f)
                        reflectiveQuadToRelative(2.44f, -0.99f)
                        close()
                        moveTo(6.69f, 18.07f)
                        quadToRelative(-1.71f, 0f, -2.92f, -1.21f)
                        reflectiveQuadTo(2.56f, 13.94f)
                        reflectiveQuadTo(3.77f, 11.02f)
                        reflectiveQuadTo(6.69f, 9.81f)
                        reflectiveQuadTo(9.6f, 11.02f)
                        reflectiveQuadToRelative(1.21f, 2.91f)
                        reflectiveQuadTo(9.6f, 16.85f)
                        reflectiveQuadTo(6.69f, 18.07f)
                        close()
                        moveTo(8f, 15.25f)
                        quadTo(8.54f, 14.7f, 8.54f, 13.93f)
                        reflectiveQuadTo(8f, 12.63f)
                        reflectiveQuadTo(6.69f, 12.08f)
                        reflectiveQuadTo(5.38f, 12.63f)
                        reflectiveQuadTo(4.83f, 13.94f)
                        reflectiveQuadToRelative(0.55f, 1.31f)
                        reflectiveQuadToRelative(1.31f, 0.55f)
                        reflectiveQuadTo(8f, 15.25f)
                        close()
                        moveToRelative(6.6f, 2.99f)
                        close()
                        moveTo(16.5f, 8.25f)
                        close()
                        moveTo(6.69f, 13.93f)
                        close()
                    }
                }
                .build()
        return _bubble_chart!!
    }

private var _bubble_chart: ImageVector? = null