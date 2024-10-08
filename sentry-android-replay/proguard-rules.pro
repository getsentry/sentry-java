# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Rules to detect Images/Icons and mask them
-dontwarn androidx.compose.ui.graphics.painter.Painter
-keepnames class * extends androidx.compose.ui.graphics.painter.Painter
-keepclasseswithmembernames class * {
    androidx.compose.ui.graphics.painter.Painter painter;
}
# Rules to detect Text colors and if they have Modifier.fillMaxWidth to later mask them
-dontwarn androidx.compose.ui.graphics.ColorProducer
-dontwarn androidx.compose.foundation.layout.FillElement
-keepnames class androidx.compose.foundation.layout.FillElement
-keepclasseswithmembernames class * {
    androidx.compose.ui.graphics.ColorProducer color;
}
# Rules to detect a compose view to parse its hierarchy
-dontwarn androidx.compose.ui.platform.AndroidComposeView
-keepnames class androidx.compose.ui.platform.AndroidComposeView
# Rules to detect a media player view to later mask it
-dontwarn androidx.media3.ui.PlayerView
-keepnames class androidx.media3.ui.PlayerView
# Rules to detect a ExoPlayer view to later mask it
-dontwarn com.google.android.exoplayer2.ui.PlayerView
-keepnames class com.google.android.exoplayer2.ui.PlayerView
-dontwarn com.google.android.exoplayer2.ui.StyledPlayerView
-keepnames class com.google.android.exoplayer2.ui.StyledPlayerView
