# https://developer.android.com/build/shrink-code

## Helps debug release versions
-dontobfuscate

## Rules for NewPipeExtractor
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
## Rules for Rhino and Rhino Engine
-keep class org.mozilla.javascript.* { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**
# Rules for jsoup
# Ignore intended-to-be-optional re2j classes - only needed if using re2j for jsoup regex
# jsoup safely falls back to JDK regex if re2j not on classpath, but has concrete re2j refs
# See https://github.com/jhy/jsoup/issues/2459 - may be resolved in future, then this may be removed
-dontwarn com.google.re2j.**

## Rules for ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }

## Rules for OkHttp. Copy pasted from https://github.com/square/okhttp
-dontwarn okhttp3.**
-dontwarn okio.**

## See https://github.com/TeamNewPipe/NewPipe/pull/1441
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

## For some reason NotificationModeConfigFragment wasn't kept (only referenced in a preference xml)
-keep class org.schabi.newpipe.settings.notifications.** { *; }
