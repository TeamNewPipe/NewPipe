# SPDX-License-Identifier: GPL-3.0-or-later
# Only the dedicated AndroidJUnit4 compatibility test APK uses these rules.
# Its inherited test libraries reference optional desktop/JUnit5 integrations that
# are unavailable on Android and unused by PipePipePackageCompatibilityTest.

# JNA's optional AWT window/component helpers.
-dontwarn java.awt.Component
-dontwarn java.awt.Window

# ByteBuddy's optional desktop Java-agent integration.
-dontwarn java.lang.instrument.ClassDefinition
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn java.lang.instrument.IllegalClassFormatException
-dontwarn java.lang.instrument.Instrumentation
-dontwarn java.lang.instrument.UnmodifiableClassException

# AssertJ's optional desktop thread dumps and XML formatter.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.ThreadInfo
-dontwarn java.lang.management.ThreadMXBean
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# AssertJ's optional JUnit5 soft-assertion integration; this suite uses JUnit4.
-dontwarn org.junit.jupiter.api.TestInstance$Lifecycle
-dontwarn org.junit.jupiter.api.extension.AfterEachCallback
-dontwarn org.junit.jupiter.api.extension.AfterTestExecutionCallback
-dontwarn org.junit.jupiter.api.extension.BeforeEachCallback
-dontwarn org.junit.jupiter.api.extension.ExtensionConfigurationException
-dontwarn org.junit.jupiter.api.extension.ExtensionContext$Namespace
-dontwarn org.junit.jupiter.api.extension.ExtensionContext$Store
-dontwarn org.junit.jupiter.api.extension.ExtensionContext
-dontwarn org.junit.jupiter.api.extension.ParameterContext
-dontwarn org.junit.jupiter.api.extension.ParameterResolutionException
-dontwarn org.junit.jupiter.api.extension.ParameterResolver
-dontwarn org.junit.jupiter.api.extension.TestInstancePostProcessor
-dontwarn org.junit.jupiter.api.parallel.Execution
-dontwarn org.junit.jupiter.api.parallel.ExecutionMode
-dontwarn org.junit.platform.commons.annotation.Testable
-dontwarn org.junit.platform.commons.support.AnnotationSupport
-dontwarn org.junit.platform.commons.support.HierarchyTraversalMode
-dontwarn org.junit.platform.commons.support.ReflectionSupport
-dontwarn org.opentest4j.MultipleFailuresError
