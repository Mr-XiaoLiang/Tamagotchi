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
val settings: ImageVector
    get() {
        if (_settings != null) {
            return _settings!!
        }
        _settings =
            ImageVector.Builder(
                name = "settings",
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
                        moveTo(9.08f, 22.2f)
                        lineTo(8.66f, 18.94f)
                        quadTo(8.39f, 18.83f, 8.14f, 18.68f)
                        reflectiveQuadTo(7.65f, 18.36f)
                        lineTo(4.62f, 19.64f)
                        lineTo(1.69f, 14.57f)
                        lineTo(4.31f, 12.58f)
                        quadTo(4.29f, 12.43f, 4.29f, 12.29f)
                        reflectiveQuadTo(4.29f, 12f)
                        reflectiveQuadToRelative(0f, -0.29f)
                        reflectiveQuadTo(4.31f, 11.42f)
                        lineTo(1.69f, 9.43f)
                        lineTo(4.62f, 4.37f)
                        lineTo(7.66f, 5.65f)
                        quadTo(7.9f, 5.48f, 8.15f, 5.33f)
                        reflectiveQuadTo(8.66f, 5.07f)
                        lineTo(9.08f, 1.8f)
                        horizontalLineToRelative(5.85f)
                        lineToRelative(0.41f, 3.28f)
                        quadToRelative(0.28f, 0.11f, 0.53f, 0.26f)
                        reflectiveQuadToRelative(0.49f, 0.32f)
                        lineTo(19.38f, 4.37f)
                        lineToRelative(2.93f, 5.06f)
                        lineToRelative(-2.63f, 1.99f)
                        quadToRelative(0.02f, 0.15f, 0.02f, 0.29f)
                        reflectiveQuadToRelative(0f, 0.29f)
                        reflectiveQuadToRelative(-0f, 0.29f)
                        reflectiveQuadToRelative(-0.04f, 0.29f)
                        lineToRelative(2.63f, 1.99f)
                        lineToRelative(-2.94f, 5.07f)
                        lineTo(16.34f, 18.36f)
                        quadToRelative(-0.24f, 0.17f, -0.49f, 0.32f)
                        reflectiveQuadToRelative(-0.51f, 0.26f)
                        lineTo(14.92f, 22.2f)
                        horizontalLineTo(9.08f)
                        close()
                        moveToRelative(1.99f, -2.28f)
                        horizontalLineTo(12.9f)
                        lineToRelative(0.36f, -2.64f)
                        quadToRelative(0.78f, -0.2f, 1.46f, -0.59f)
                        reflectiveQuadToRelative(1.23f, -0.96f)
                        lineToRelative(2.47f, 1.03f)
                        lineToRelative(0.9f, -1.59f)
                        lineTo(17.19f, 13.55f)
                        quadToRelative(0.13f, -0.36f, 0.18f, -0.76f)
                        reflectiveQuadTo(17.42f, 12f)
                        reflectiveQuadTo(17.37f, 11.2f)
                        reflectiveQuadTo(17.19f, 10.45f)
                        lineTo(19.33f, 8.83f)
                        lineTo(18.41f, 7.24f)
                        lineTo(15.95f, 8.29f)
                        quadTo(15.4f, 7.7f, 14.72f, 7.3f)
                        reflectiveQuadTo(13.26f, 6.71f)
                        lineTo(12.93f, 4.07f)
                        horizontalLineTo(11.08f)
                        lineTo(10.75f, 6.7f)
                        quadTo(9.95f, 6.9f, 9.27f, 7.29f)
                        reflectiveQuadTo(8.04f, 8.26f)
                        lineTo(5.58f, 7.24f)
                        lineTo(4.67f, 8.83f)
                        lineTo(6.8f, 10.41f)
                        quadTo(6.67f, 10.8f, 6.62f, 11.19f)
                        reflectiveQuadTo(6.56f, 12f)
                        quadToRelative(0f, 0.41f, 0.06f, 0.79f)
                        reflectiveQuadTo(6.8f, 13.57f)
                        lineToRelative(-2.13f, 1.6f)
                        lineToRelative(0.91f, 1.59f)
                        lineTo(8.04f, 15.72f)
                        quadTo(8.59f, 16.3f, 9.28f, 16.7f)
                        reflectiveQuadToRelative(1.47f, 0.6f)
                        lineToRelative(0.32f, 2.63f)
                        close()
                        moveTo(12.03f, 15.5f)
                        quadToRelative(1.45f, 0f, 2.47f, -1.03f)
                        reflectiveQuadTo(15.53f, 12f)
                        reflectiveQuadTo(14.51f, 9.52f)
                        reflectiveQuadTo(12.03f, 8.5f)
                        quadToRelative(-1.47f, 0f, -2.48f, 1.02f)
                        reflectiveQuadTo(8.53f, 12f)
                        reflectiveQuadToRelative(1.02f, 2.47f)
                        reflectiveQuadToRelative(2.48f, 1.03f)
                        close()
                        moveTo(12f, 12f)
                        close()
                    }
                }
                .build()
        return _settings!!
    }

private var _settings: ImageVector? = null