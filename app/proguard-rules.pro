# https://developer.android.com/build/shrink-code

## Helps debug release versions
-dontobfuscate

## Rules for NewPipeExtractor
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }

## Keep extractor image APIs that are accessed reflectively by ExtractorImageCompat.
## Debug builds work without these rules, but release shrinking can remove otherwise
## unused thumbnail/avatar fallback accessors before Coil receives an image URL.
-keep class org.schabi.newpipe.extractor.Image { *; }
-keepclassmembers class org.schabi.newpipe.extractor.InfoItem {
    public void setThumbnailUrl(java.lang.String);
    public java.lang.String getThumbnailUrl();
}
-keepclassmembers class org.schabi.newpipe.extractor.stream.StreamInfo {
    public java.util.List getThumbnails();
    public void setThumbnails(java.util.List);
    public java.lang.String getThumbnailUrl();
    public void setThumbnailUrl(java.lang.String);
    public java.util.List getUploaderAvatars();
    public void setUploaderAvatars(java.util.List);
    public java.lang.String getUploaderAvatarUrl();
    public void setUploaderAvatarUrl(java.lang.String);
    public java.util.List getSubChannelAvatars();
    public void setSubChannelAvatars(java.util.List);
    public java.lang.String getSubChannelAvatarUrl();
    public void setSubChannelAvatarUrl(java.lang.String);
    public java.util.List getRelatedItems();
    public void setRelatedItems(java.util.List);
}
-keepclassmembers class org.schabi.newpipe.extractor.stream.StreamInfoItem {
    public java.util.List getThumbnails();
    public void setThumbnails(java.util.List);
    public java.lang.String getThumbnailUrl();
    public void setThumbnailUrl(java.lang.String);
    public java.util.List getUploaderAvatars();
    public void setUploaderAvatars(java.util.List);
    public java.lang.String getUploaderAvatarUrl();
    public void setUploaderAvatarUrl(java.lang.String);
}
-keepclassmembers class org.schabi.newpipe.extractor.comments.CommentsInfoItem {
    public java.lang.String getUploaderAvatarUrl();
    public void setUploaderAvatarUrl(java.lang.String);
    public java.util.Collection getPictures();
    public void setPictures(java.util.Collection);
}

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

## Rules for ExoPlayer
-keep class androidx.media3.** { *; }

## Rules for OkHttp. Copy pasted from https://github.com/square/okhttp
-dontwarn okhttp3.**
-dontwarn okio.**

## SLF4J looks for an optional runtime binding class. The app does not ship one.
-dontwarn org.slf4j.impl.StaticLoggerBinder

## Netty probes for optional desktop logging and BlockHound integrations.
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.google.protobuf.nano.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.jboss.marshalling.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.osgi.annotation.bundle.**
-dontwarn reactor.blockhound.**
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn sun.security.x509.**

## jvm-libp2p assembles its transport, security, multiplexing, and protocol
## pipeline dynamically and does not publish Android consumer R8 rules. It also
## depends on Netty runtime factories, pipeline handlers, and channel types that
## are selected reflectively. Debug builds work without this, but release R8 can
## remove members that are only reached by Netty/libp2p at pairing time.
-keep class io.libp2p.** { *; }
-keep class com.southernstorm.noise.** { *; }
-keep class io.netty.** { *; }

## See https://github.com/TeamNewPipe/NewPipe/pull/1441
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

## For some reason NotificationModeConfigFragment wasn't kept (only referenced in a preference xml)
-keep class org.schabi.newpipe.settings.notifications.** { *; }

# Prevent R8 from stripping or renaming Protobuf internal fields
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

## Keep Kotlinx Serialization classes
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.schabi.newpipe.**$$serializer { *; }
-keepclassmembers class org.schabi.newpipe.** {
    *** Companion;
}
-keepclasseswithmembers class org.schabi.newpipe.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# See https://github.com/TeamNewPipe/NewPipe/issues/13508
-keep class org.ocpsoft.prettytime.i18n.Resources* { *; }

# External service packages link against these public APIs at runtime. Their callers
# are absent from the app's R8 input, so retain the complete published plugin surface.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class org.apache.commons.lang3.** { *; }
-dontwarn org.apache.commons.lang3.**
# The WizeStream native template also compiles against the host Kotlin standard library.
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
