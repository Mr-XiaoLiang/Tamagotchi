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
val tune: ImageVector
    get() {
        if (_tune != null) {
            return _tune!!
        }
        _tune =
            ImageVector.Builder(
                name = "tune",
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
                        pathFillType = PathFillType.NonZero,
                    ) {
                        moveTo(10.95f, 21.19f)
                        verticalLineTo(15f)
                        horizontalLineToRelative(2.19f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(8f)
                        verticalLineToRelative(2.19f)
                        horizontalLineToRelative(-8f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(10.95f)
                        close()
                        moveToRelative(-8.09f, -2f)
                        verticalLineTo(17f)
                        horizontalLineTo(9.05f)
                        verticalLineToRelative(2.19f)
                        horizontalLineTo(2.86f)
                        close()
                        moveToRelative(4f, -4.1f)
                        verticalLineToRelative(-2f)
                        horizontalLineToRelative(-4f)
                        verticalLineTo(10.91f)
                        horizontalLineToRelative(4f)
                        verticalLineToRelative(-2f)
                        horizontalLineTo(9.05f)
                        verticalLineToRelative(6.18f)
                        horizontalLineTo(6.86f)
                        close()
                        moveToRelative(4.09f, -2f)
                        verticalLineTo(10.91f)
                        horizontalLineTo(21.14f)
                        verticalLineToRelative(2.18f)
                        horizontalLineTo(10.95f)
                        close()
                        moveTo(14.95f, 9f)
                        verticalLineTo(2.81f)
                        horizontalLineToRelative(2.19f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(4f)
                        verticalLineTo(7f)
                        horizontalLineToRelative(-4f)
                        verticalLineTo(9f)
                        horizontalLineTo(14.95f)
                        close()
                        moveTo(2.86f, 7f)
                        verticalLineTo(4.81f)
                        horizontalLineTo(13.05f)
                        verticalLineTo(7f)
                        horizontalLineTo(2.86f)
                        close()
                    }
                }
                .build()
        return _tune!!
    }

private var _tune: ImageVector? = null