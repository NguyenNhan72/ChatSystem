package com.example.chatsystem.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Cac icon vector chuan theo thiet ke Messenger tren Figma.
 */
object MessengerIcons {

    /**
     * Icon soan tin nhan moi (o vuong kem cay but cheo).
     */
    val Compose: ImageVector by lazy {
        ImageVector.Builder(
            name = "Compose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(3f, 5f)
                curveTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
                horizontalLineTo(14f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(19f)
                horizontalLineTo(19f)
                verticalLineTo(10f)
                horizontalLineTo(21f)
                verticalLineTo(19f)
                curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
                horizontalLineTo(5f)
                curveTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
                close()
                moveTo(20.71f, 4.04f)
                curveTo(21.1f, 3.65f, 21.1f, 3.02f, 20.71f, 2.63f)
                lineTo(19.37f, 1.29f)
                curveTo(18.98f, 0.9f, 18.35f, 0.9f, 17.96f, 1.29f)
                lineTo(16.13f, 3.12f)
                lineTo(19.88f, 6.87f)
                lineTo(20.71f, 4.04f)
                close()
                moveTo(15.06f, 4.19f)
                lineTo(8f, 11.25f)
                verticalLineTo(15f)
                horizontalLineTo(11.75f)
                lineTo(18.81f, 7.94f)
                lineTo(15.06f, 4.19f)
                close()
            }
        }.build()
    }

    val Camera: ImageVector by lazy {
        ImageVector.Builder(
            name = "Camera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(9f, 2f)
                lineTo(7.17f, 4f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
                verticalLineTo(18f)
                curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                horizontalLineTo(20f)
                curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                verticalLineTo(6f)
                curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
                horizontalLineTo(16.83f)
                lineTo(15f, 2f)
                horizontalLineTo(9f)
                close()
                moveTo(12f, 17f)
                curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
                curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
                curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
                curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
                close()
            }
        }.build()
    }

    val Edit: ImageVector by lazy {
        ImageVector.Builder(
            name = "Edit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(3f, 17.25f)
                verticalLineTo(21f)
                horizontalLineTo(6.75f)
                lineTo(17.81f, 9.94f)
                lineTo(14.06f, 6.19f)
                lineTo(3f, 17.25f)
                close()
                moveTo(20.71f, 7.04f)
                curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
                lineTo(18.37f, 3.29f)
                curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
                lineTo(15.13f, 5.12f)
                lineTo(18.88f, 8.87f)
                lineTo(20.71f, 7.04f)
                close()
            }
        }.build()
    }

    val Phone: ImageVector by lazy {
        ImageVector.Builder(
            name = "Phone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6.62f, 10.79f)
                curveTo(8.06f, 13.62f, 10.38f, 15.94f, 13.21f, 17.38f)
                lineTo(15.41f, 15.18f)
                curveTo(15.69f, 14.9f, 16.08f, 14.82f, 16.43f, 14.93f)
                curveTo(17.55f, 15.3f, 18.75f, 15.5f, 20f, 15.5f)
                curveTo(20.55f, 15.5f, 21f, 15.95f, 21f, 16.5f)
                verticalLineTo(20f)
                curveTo(21f, 20.55f, 20.55f, 21f, 20f, 21f)
                curveTo(10.61f, 21f, 3f, 13.39f, 3f, 4f)
                curveTo(3f, 3.45f, 3.45f, 3f, 4f, 3f)
                horizontalLineTo(7.5f)
                curveTo(8.05f, 3f, 8.5f, 3.45f, 8.5f, 4f)
                curveTo(8.5f, 5.25f, 8.7f, 6.45f, 9.07f, 7.57f)
                curveTo(9.18f, 7.92f, 9.1f, 8.31f, 8.82f, 8.59f)
                lineTo(6.62f, 10.79f)
                close()
            }
        }.build()
    }

    val VideoCall: ImageVector by lazy {
        ImageVector.Builder(
            name = "VideoCall",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(17f, 10.5f)
                verticalLineTo(7f)
                curveTo(17f, 6.45f, 16.55f, 6f, 16f, 6f)
                horizontalLineTo(4f)
                curveTo(3.45f, 6f, 3f, 6.45f, 3f, 7f)
                verticalLineTo(17f)
                curveTo(3f, 17.55f, 3.45f, 18f, 4f, 18f)
                horizontalLineTo(16f)
                curveTo(16.55f, 18f, 17f, 17.55f, 17f, 17f)
                verticalLineTo(13.5f)
                lineTo(21f, 17.5f)
                verticalLineTo(6.5f)
                lineTo(17f, 10.5f)
                close()
            }
        }.build()
    }

    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "Info",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
                moveTo(13f, 17f)
                horizontalLineTo(11f)
                verticalLineTo(11f)
                horizontalLineTo(13f)
                verticalLineTo(17f)
                close()
                moveTo(13f, 9f)
                horizontalLineTo(11f)
                verticalLineTo(7f)
                horizontalLineTo(13f)
                verticalLineTo(9f)
                close()
            }
        }.build()
    }

    val Photo: ImageVector by lazy {
        ImageVector.Builder(
            name = "Photo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(21f, 19f)
                verticalLineTo(5f)
                curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
                horizontalLineTo(5f)
                curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                verticalLineTo(19f)
                curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                close()
                moveTo(8.5f, 13.5f)
                lineTo(11f, 16.51f)
                lineTo(14.5f, 12f)
                lineTo(19f, 18f)
                horizontalLineTo(5f)
                lineTo(8.5f, 13.5f)
                close()
            }
        }.build()
    }

    val Mic: ImageVector by lazy {
        ImageVector.Builder(
            name = "Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 14f)
                curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
                verticalLineTo(5f)
                curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
                curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
                verticalLineTo(11f)
                curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
                close()
                moveTo(17f, 11f)
                curveTo(17f, 13.76f, 14.76f, 16f, 12f, 16f)
                curveTo(9.24f, 16f, 7f, 13.76f, 7f, 11f)
                horizontalLineTo(5f)
                curveTo(5f, 14.53f, 7.61f, 17.43f, 11f, 17.93f)
                verticalLineTo(21f)
                horizontalLineTo(13f)
                verticalLineTo(17.93f)
                curveTo(16.39f, 17.43f, 19f, 14.53f, 19f, 11f)
                horizontalLineTo(17f)
                close()
            }
        }.build()
    }

    val ThumbUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "ThumbUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(1f, 21f)
                horizontalLineTo(5f)
                verticalLineTo(9f)
                horizontalLineTo(1f)
                verticalLineTo(21f)
                close()
                moveTo(23f, 10f)
                curveTo(23f, 8.9f, 22.1f, 8f, 21f, 8f)
                horizontalLineTo(14.69f)
                lineTo(15.64f, 3.43f)
                lineTo(15.67f, 3.11f)
                curveTo(15.67f, 2.7f, 15.5f, 2.32f, 15.23f, 2.05f)
                lineTo(14.17f, 1f)
                lineTo(7.59f, 7.59f)
                curveTo(7.22f, 7.95f, 7f, 8.45f, 7f, 9f)
                verticalLineTo(19f)
                curveTo(7f, 20.1f, 7.9f, 21f, 9f, 21f)
                horizontalLineTo(18f)
                curveTo(18.83f, 21f, 19.54f, 20.5f, 19.84f, 19.78f)
                lineTo(22.86f, 12.73f)
                curveTo(22.95f, 12.5f, 23f, 12.26f, 23f, 12f)
                verticalLineTo(10f)
                close()
            }
        }.build()
    }

    val Smile: ImageVector by lazy {
        ImageVector.Builder(
            name = "Smile",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.47f, 22f, 11.99f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(12f, 20f)
                curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
                curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
                curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
                close()
                moveTo(15.5f, 11f)
                curveTo(16.33f, 11f, 17f, 10.33f, 17f, 9.5f)
                curveTo(17f, 8.67f, 16.33f, 8f, 15.5f, 8f)
                curveTo(14.67f, 8f, 14f, 8.67f, 14f, 9.5f)
                curveTo(14f, 10.33f, 14.67f, 11f, 15.5f, 11f)
                close()
                moveTo(8.5f, 11f)
                curveTo(9.33f, 11f, 10f, 10.33f, 10f, 9.5f)
                curveTo(10f, 8.67f, 9.33f, 8f, 8.5f, 8f)
                curveTo(7.67f, 8f, 7f, 8.67f, 7f, 9.5f)
                curveTo(7f, 10.33f, 7.67f, 11f, 8.5f, 11f)
                close()
                moveTo(12f, 17.5f)
                curveTo(14.33f, 17.5f, 16.31f, 16.04f, 17.11f, 14f)
                horizontalLineTo(6.89f)
                curveTo(7.69f, 16.04f, 9.67f, 17.5f, 12f, 17.5f)
                close()
            }
        }.build()
    }

    val People: ImageVector by lazy {
        ImageVector.Builder(
            name = "People",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(16f, 11f)
                curveTo(17.66f, 11f, 18.99f, 9.66f, 18.99f, 8f)
                curveTo(18.99f, 6.34f, 17.66f, 5f, 16f, 5f)
                curveTo(14.34f, 5f, 13f, 6.34f, 13f, 8f)
                curveTo(13f, 9.66f, 14.34f, 11f, 16f, 11f)
                close()
                moveTo(8f, 11f)
                curveTo(9.66f, 11f, 10.99f, 9.66f, 10.99f, 8f)
                curveTo(10.99f, 6.34f, 9.66f, 5f, 8f, 5f)
                curveTo(6.34f, 5f, 5f, 6.34f, 5f, 8f)
                curveTo(5f, 9.66f, 6.34f, 11f, 8f, 11f)
                close()
                moveTo(8f, 13f)
                curveTo(5.67f, 13f, 1f, 14.17f, 1f, 16.5f)
                verticalLineTo(19f)
                horizontalLineTo(15f)
                verticalLineTo(16.5f)
                curveTo(15f, 14.17f, 10.33f, 13f, 8f, 13f)
                close()
                moveTo(16f, 13f)
                curveTo(15.71f, 13f, 15.38f, 13.02f, 15.03f, 13.05f)
                curveTo(16.19f, 13.89f, 17f, 15.02f, 17f, 16.5f)
                verticalLineTo(19f)
                horizontalLineTo(23f)
                verticalLineTo(16.5f)
                curveTo(23f, 14.17f, 18.33f, 13f, 16f, 13f)
                close()
            }
        }.build()
    }

    val Discover: ImageVector by lazy {
        ImageVector.Builder(
            name = "Discover",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
                moveTo(14.19f, 14.19f)
                lineTo(6f, 18f)
                lineTo(9.81f, 9.81f)
                lineTo(18f, 6f)
                lineTo(14.19f, 14.19f)
                close()
                moveTo(12f, 10.9f)
                curveTo(11.39f, 10.9f, 10.9f, 11.39f, 10.9f, 12f)
                curveTo(10.9f, 12.61f, 11.39f, 13.1f, 12f, 13.1f)
                curveTo(12.61f, 13.1f, 13.1f, 12.61f, 13.1f, 12f)
                curveTo(13.1f, 11.39f, 12.61f, 10.9f, 12f, 10.9f)
                close()
            }
        }.build()
    }

    val ChatBubble: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChatBubble",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(20f, 2f)
                horizontalLineTo(4f)
                curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
                verticalLineTo(22f)
                lineTo(6f, 18f)
                horizontalLineTo(20f)
                curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
                verticalLineTo(4f)
                curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
                close()
            }
        }.build()
    }

    val GroupAdd: ImageVector by lazy {
        ImageVector.Builder(
            name = "GroupAdd",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(8f, 10f)
                curveTo(9.66f, 10f, 11f, 8.66f, 11f, 7f)
                curveTo(11f, 5.34f, 9.66f, 4f, 8f, 4f)
                curveTo(6.34f, 4f, 5f, 5.34f, 5f, 7f)
                curveTo(5f, 8.66f, 6.34f, 10f, 8f, 10f)
                close()
                moveTo(8f, 12f)
                curveTo(5.33f, 12f, 0f, 13.34f, 0f, 16f)
                verticalLineTo(18f)
                horizontalLineTo(16f)
                verticalLineTo(16f)
                curveTo(16f, 13.34f, 10.67f, 12f, 8f, 12f)
                close()
                moveTo(18f, 10f)
                verticalLineTo(7f)
                horizontalLineTo(16f)
                verticalLineTo(10f)
                horizontalLineTo(13f)
                verticalLineTo(12f)
                horizontalLineTo(16f)
                verticalLineTo(15f)
                horizontalLineTo(18f)
                verticalLineTo(12f)
                horizontalLineTo(21f)
                verticalLineTo(10f)
                horizontalLineTo(18f)
                close()
            }
        }.build()
    }
}
