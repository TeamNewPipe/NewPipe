package org.schabi.newpipe.settings.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Set;

/**
 * An {@link ObjectInputStream} that only allows preferences-related types to be deserialized, to
 * prevent injections. The only allowed types are: all primitive types, all boxed primitive types,
 * null, strings. HashMap, HashSet and arrays of previously defined types are also allowed. Sources:
 * <a href="https://wiki.sei.cmu.edu/confluence/display/java/SER00-J.+Enable+serialization+compatibility+during+class+evolution">
 * cmu.edu
 * </a>,
 * <a href="https://cheatsheetseries.owasp.org/cheatsheets/Deserialization_Cheat_Sheet.html#harden-your-own-javaioobjectinputstream">
 * OWASP cheatsheet
 * </a>,
 * <a href="https://commons.apache.org/proper/commons-io/apidocs/src-html/org/apache/commons/io/serialization/ValidatingObjectInputStream.html#line-118">
 * Apache's {@code ValidatingObjectInputStream}
 * </a>
 */
public class PreferencesObjectInputStream extends ObjectInputStream {

    /**
     * Primitive types, strings and other built-in types do not pass through resolveClass() but
     * instead have a custom encoding; see
     * <a href="https://docs.oracle.com/javase/6/docs/platform/serialization/spec/protocol.html#10152">
     * official docs</a>.
     */
    private static final Set<String> CLASS_WHITELIST = Set.of(
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Short",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Void",
            "java.util.HashMap",
            "java.util.HashSet"
    );

    public PreferencesObjectInputStream(final InputStream in) throws IOException {
        super(in);
    }

    @Override
    protected Class<?> resolveClass(final ObjectStreamClass desc)
            throws ClassNotFoundException, IOException {
        if (CLASS_WHITELIST.contains(desc.getName())) {
            return super.resolveClass(desc);
        } else {
            throw new ClassNotFoundException("Class not allowed: " + desc.getName());
        }
    }
}
