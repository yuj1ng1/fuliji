package io.github.yuj1ng1.mycampus.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Small project-owned outlines; avoid shipping the entire extended icon catalogue. */
object CampusIcons {
    val Home = outline("Home", "M3,10 L12,3 L21,10 M5,9 V21 H10 V15 H14 V21 H19 V9")
    val Calendar = outline("Calendar", "M6,3 V7 M18,3 V7 M4,5 H20 V21 H4 Z M4,10 H20 M8,14 H9 M12,14 H13 M16,14 H17 M8,18 H9 M12,18 H13")
    val Exam = outline("Exam", "M7,3 H17 Q19,3 19,5 V21 H5 V5 Q5,3 7,3 Z M9,3 V6 H15 V3 M9,11 H15 M9,15 H15 M9,18 H12")
    val Grades = outline("Grades", "M4,4 H20 V20 H4 Z M8,15 V12 M12,15 V8 M16,15 V10")
    val Settings = outline("Settings", "M8,5 H21 M3,5 H4 M13,12 H21 M3,12 H9 M8,19 H21 M3,19 H4 M4,3 H8 V7 H4 Z M9,10 H13 V14 H9 Z M4,17 H8 V21 H4 Z")
    val Link = outline("Link", "M10,14 L14,10 M8,16 L6,18 Q3,21 1,18 Q-1,16 2,13 L6,9 Q9,6 12,9 M12,15 Q15,18 18,15 L22,11 Q25,8 22,5 Q19,2 16,5 L14,7")
    val Arrow = outline("Arrow", "M9,5 L16,12 L9,19")
    val Add = outline("Add", "M12,5 V19 M5,12 H19")
    val Shield = outline("Shield", "M12,3 L20,6 V12 Q19,18 12,22 Q5,18 4,12 V6 Z M8,12 L11,15 L16,10")
    val School = outline("Campus", "M3,9 L12,4 L21,9 L12,14 Z M6,11 L6,17 Q12,21 18,17 L18,11 M21,9 L21,17")
    val Wallet = outline("Wallet", "M4,6 H19 Q21,6 21,8 V19 H4 Q2,19 2,17 V6 Q2,4 4,4 H18 V6 M21,11 H16 V16 H21 M17.5,13.5 H18")
    val Bolt = outline("Electricity", "M13,2 L4,14 H11 L10,22 L20,9 H13 Z")

    private fun outline(name: String, data: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}
