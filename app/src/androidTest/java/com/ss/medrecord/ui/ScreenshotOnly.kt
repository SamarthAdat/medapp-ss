package com.ss.medrecord.ui

/**
 * Marks a test that exists to produce screenshots rather than to assert
 * anything.
 *
 * `connectedAndroidTest` filters these out (see `notAnnotation` in
 * app/build.gradle.kts). They render whole screens and capture full-size
 * bitmaps, which is heavy enough to have crashed the instrumentation process
 * once in a full run - and a tool that checks nothing has no business turning
 * a green suite red.
 *
 * To run them anyway, name the class explicitly:
 *
 * ```
 * ./gradlew installDebug installDebugAndroidTest
 * adb shell am instrument -w -e class com.ss.medrecord.ui.DesignCaptureTest \
 *     com.ss.medrecord.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /storage/emulated/0/Android/data/com.ss.medrecord/files/
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ScreenshotOnly
