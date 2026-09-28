package com.kingzcheung.xime.bitwarden

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Bitwarden 风格盾牌图标（简化矢量）。 */
object BitwardenIcons {
    val Shield: ImageVector
        get() {
            if (_shield != null) return _shield!!
            _shield = ImageVector.Builder(
                name = "BitwardenShield",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.EvenOdd,
                ) {
                    moveTo(12f, 2f)
                    lineTo(4.5f, 5f)
                    verticalLineTo(11.5f)
                    curveTo(4.5f, 16.5f, 7.6f, 20.7f, 12f, 22f)
                    curveTo(16.4f, 20.7f, 19.5f, 16.5f, 19.5f, 11.5f)
                    verticalLineTo(5f)
                    close()
                    moveTo(12f, 8.2f)
                    curveTo(10.9f, 8.2f, 10f, 9.1f, 10f, 10.2f)
                    curveTo(10f, 10.9f, 10.35f, 11.5f, 10.9f, 11.85f)
                    lineTo(10.4f, 15.6f)
                    horizontalLineTo(13.6f)
                    lineTo(13.1f, 11.85f)
                    curveTo(13.65f, 11.5f, 14f, 10.9f, 14f, 10.2f)
                    curveTo(14f, 9.1f, 13.1f, 8.2f, 12f, 8.2f)
                    close()
                }
            }.build()
            return _shield!!
        }

    /** 浏览器无网站图标时的圆形地球回退。 */
    val Globe: ImageVector
        get() {
            if (_globe != null) return _globe!!
            _globe = ImageVector.Builder(
                name = "VaultGlobe",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                // 外圆
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.6f,
                ) {
                    moveTo(12f, 3.2f)
                    curveTo(7.14f, 3.2f, 3.2f, 7.14f, 3.2f, 12f)
                    curveTo(3.2f, 16.86f, 7.14f, 20.8f, 12f, 20.8f)
                    curveTo(16.86f, 20.8f, 20.8f, 16.86f, 20.8f, 12f)
                    curveTo(20.8f, 7.14f, 16.86f, 3.2f, 12f, 3.2f)
                    close()
                }
                // 经线（竖椭圆）
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.4f,
                ) {
                    moveTo(12f, 3.2f)
                    curveTo(9.6f, 5.6f, 8.4f, 8.7f, 8.4f, 12f)
                    curveTo(8.4f, 15.3f, 9.6f, 18.4f, 12f, 20.8f)
                    moveTo(12f, 3.2f)
                    curveTo(14.4f, 5.6f, 15.6f, 8.7f, 15.6f, 12f)
                    curveTo(15.6f, 15.3f, 14.4f, 18.4f, 12f, 20.8f)
                }
                // 纬线
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.4f,
                ) {
                    moveTo(4.1f, 9.2f)
                    horizontalLineTo(19.9f)
                    moveTo(4.1f, 14.8f)
                    horizontalLineTo(19.9f)
                    moveTo(3.2f, 12f)
                    horizontalLineTo(20.8f)
                }
            }.build()
            return _globe!!
        }

    private var _shield: ImageVector? = null
    private var _globe: ImageVector? = null
}
