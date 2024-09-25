# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

-dontwarn androidx.compose.ui.draw.PainterElement
-dontwarn androidx.compose.ui.draw.PainterModifierNodeElement
-dontwarn androidx.compose.ui.platform.AndroidComposeView
-dontwarn androidx.compose.ui.graphics.painter.Painter
#-dontwarn coil.compose.ContentPainterModifier
#-dontwarn coil3.compose.ContentPainterModifier
-keepclasseswithmembernames class * {
    androidx.compose.ui.graphics.painter.Painter painter;
}
-keepnames class * extends androidx.compose.ui.graphics.painter.Painter
-keepnames class androidx.compose.ui.draw.PainterModifierNodeElement
-keepnames class androidx.compose.ui.draw.PainterElement
-keepnames class androidx.compose.ui.platform.AndroidComposeView
