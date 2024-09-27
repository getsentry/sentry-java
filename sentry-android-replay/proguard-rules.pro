# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Rules to detect Images/Icons and redact them
-dontwarn androidx.compose.ui.graphics.painter.Painter
-keepnames class * extends androidx.compose.ui.graphics.painter.Painter
-keepclasseswithmembernames class * {
    androidx.compose.ui.graphics.painter.Painter painter;
}
# Rules to detect Text colors and if they have Modifier.fillMaxWidth to later redact them
-dontwarn androidx.compose.ui.graphics.ColorProducer
-dontwarn androidx.compose.foundation.layout.FillElement
-keepnames class androidx.compose.foundation.layout.FillElement
-keepclasseswithmembernames class * {
    androidx.compose.ui.graphics.ColorProducer color;
}
# Rules to detect a compose view to parse its hierarchy
-dontwarn androidx.compose.ui.platform.AndroidComposeView
-keepnames class androidx.compose.ui.platform.AndroidComposeView
