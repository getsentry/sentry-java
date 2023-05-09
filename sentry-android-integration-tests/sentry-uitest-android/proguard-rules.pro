# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

#Shrinking removes annotations and "unused classes" from test apk, so we don't shrink
-dontshrink

-dontwarn com.google.errorprone.annotations.InlineMe
-dontwarn com.google.errorprone.annotations.MustBeClosed
-dontwarn com.sun.jna.FunctionMapper
-dontwarn com.sun.jna.JNIEnv
-dontwarn com.sun.jna.LastErrorException
-dontwarn com.sun.jna.Library
-dontwarn com.sun.jna.Memory
-dontwarn com.sun.jna.Native
-dontwarn com.sun.jna.NativeLibrary
-dontwarn com.sun.jna.Platform
-dontwarn com.sun.jna.Pointer
-dontwarn com.sun.jna.Structure
-dontwarn com.sun.jna.platform.win32.Advapi32
-dontwarn com.sun.jna.platform.win32.Kernel32
-dontwarn com.sun.jna.platform.win32.Win32Exception
-dontwarn com.sun.jna.platform.win32.WinBase$OVERLAPPED
-dontwarn com.sun.jna.platform.win32.WinBase$SECURITY_ATTRIBUTES
-dontwarn com.sun.jna.platform.win32.WinDef$DWORD
-dontwarn com.sun.jna.platform.win32.WinDef$LPVOID
-dontwarn com.sun.jna.platform.win32.WinNT$ACL
-dontwarn com.sun.jna.platform.win32.WinNT$HANDLE
-dontwarn com.sun.jna.platform.win32.WinNT$SECURITY_DESCRIPTOR
-dontwarn com.sun.jna.ptr.IntByReference
-dontwarn com.sun.jna.win32.StdCallLibrary
-dontwarn com.sun.jna.win32.W32APIOptions
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn java.lang.instrument.ClassDefinition
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn java.lang.instrument.IllegalClassFormatException
-dontwarn java.lang.instrument.Instrumentation
-dontwarn java.lang.instrument.UnmodifiableClassException
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn okhttp3.Handshake$Companion
-dontwarn okhttp3.Headers$Companion
-dontwarn okhttp3.HttpUrl$Companion
-dontwarn okhttp3.Protocol$Companion
-dontwarn okhttp3.internal.concurrent.Task
-dontwarn okhttp3.internal.concurrent.TaskQueue
-dontwarn okhttp3.internal.concurrent.TaskRunner$Backend
-dontwarn okhttp3.internal.concurrent.TaskRunner$RealBackend
-dontwarn okhttp3.internal.concurrent.TaskRunner
-dontwarn okhttp3.internal.http2.ErrorCode$Companion
-dontwarn okhttp3.internal.platform.Platform$Companion
-dontwarn okhttp3.internal.ws.WebSocketExtensions$Companion
-dontwarn okhttp3.internal.ws.WebSocketExtensions
-dontwarn okio.ByteString$Companion
-dontwarn org.mockito.internal.creation.bytebuddy.inject.MockMethodDispatcher
-dontwarn org.opentest4j.AssertionFailedError
