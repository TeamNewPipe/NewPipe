# Test-only process-state cleanup for actual external DEX installation/restore tests.
# The production release retains the public SDK, but not these private test targets.
-keep class org.schabi.newpipe.plugin.PluginManager { *; }

# The runner shares this app dependency, so AGP leaves it out of the test APK.
# Preserve its entry points for AndroidJUnitRunner in the minified test host.
-keep class androidx.tracing.Trace { *; }

# The selected fixture calls these host entry points from its separate test APK.
# Retain their signatures while still optimizing their implementations.
-keep,allowoptimization class org.schabi.newpipe.plugin.AndroidPluginPreferences {
    public <init>(android.content.Context, java.lang.String, java.util.List, java.lang.String);
    public android.content.SharedPreferences getSharedPreferences();
    public java.lang.String getString(java.lang.String);
    public static java.lang.String fileName(java.lang.String, java.lang.String);
    public static final org.schabi.newpipe.plugin.AndroidPluginPreferences$Companion Companion;
}

-keep,allowoptimization class org.schabi.newpipe.plugin.AndroidPluginPreferences$Companion {
    public java.lang.String fileName(java.lang.String, java.lang.String);
}

-keep,allowoptimization class org.schabi.newpipe.profiles.ProfileManager {
    public static final org.schabi.newpipe.profiles.ProfileManager INSTANCE;
    public static java.lang.String getActiveProfileId(android.content.Context);
    public static org.schabi.newpipe.profiles.ProfileRecord createProfile(android.content.Context, java.lang.String, java.lang.String, java.lang.String);
    public static org.schabi.newpipe.profiles.ProfileRecord createProfile$default(...);
}

-keep,allowoptimization class org.schabi.newpipe.profiles.ProfileRecord {
    public java.lang.String getId();
}

-keep,allowoptimization class org.schabi.newpipe.plugin.PluginManager$InstalledPlugin {
    public java.lang.String getFileName();
}

-keep,allowoptimization class org.schabi.newpipe.plugin.PluginRuntimeLoader {
    public static java.lang.ClassLoader parentForPlugins(android.content.Context);
    public static boolean ownsClassName(java.lang.String);
}

-keep,allowoptimization class org.schabi.newpipe.util.PluginObjectInputStream {
    public <init>(java.io.InputStream);
}

-keep,allowoptimization class org.schabi.newpipe.util.PluginStateClassLoader {
    public static java.lang.ClassLoader withPlugins(java.lang.ClassLoader);
}

-keep,allowoptimization class org.schabi.newpipe.util.SerializedCache {
    public static org.schabi.newpipe.util.SerializedCache getInstance();
    public java.lang.String put(java.io.Serializable, java.lang.Class);
    public java.lang.Object take(java.lang.String, java.lang.Class);
}

-keep,allowoptimization class androidx.preference.PreferenceManager {
    public static android.content.SharedPreferences getDefaultSharedPreferences(android.content.Context);
}
