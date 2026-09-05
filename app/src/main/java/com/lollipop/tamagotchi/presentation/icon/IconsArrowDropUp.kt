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
public val arrow_drop_up: ImageVector
    get() {
        if (_arrow_drop_up != null) {
            return _arrow_drop_up!!
        }
        _arrow_drop_up =
            ImageVector.Builder(
                name = "arrow_drop_up",
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
                        moveTo(6.67f, 14.14f)
                        lineTo(12f, 8.81f)
                        lineToRelative(5.33f, 5.33f)
                        horizontalLineTo(6.67f)
                        close()
                    }
                }
                .build()
        return _arrow_drop_up!!
    }

private var _arrow_drop_up: ImageVector? = null