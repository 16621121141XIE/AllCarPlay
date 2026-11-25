package com.andrerinas.openheadunit.connection

import android.content.Context
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

/**
 * Which native libraries the ICCOA SDK loads, and whether this device actually has them.
 *
 * The SDK dex calls `System.loadLibrary("usbio")` from `UsbNative.<clinit>` and
 * `System.loadLibrary("ovmsink")` from `SinkNative.<clinit>`. These two ship for arm64-v8a only;
 * on a 32-bit or x86 unit the loader throws inside static initialization, which is unrecoverable,
 * so we check first and turn that crash into an honest message.
 *
 * The check must not rely on `nativeLibraryDir` alone: this APK is packed with
 * `extractNativeLibs=false`, so libs live inside the APK and that directory stays empty on every
 * device — that produced a bogus "CPU can't run the projection stack" on perfectly capable 64-bit
 * head units. So look at the installed directory *and* at the APK's own `lib/<abi>/` entries.
 */
object NativeStack {

    /** Libraries the SDK loads by name, in the order it loads them. */
    val requiredLibraries = listOf("libovmsink.so", "libusbio.so")

    /** Pure part of [missingLibraries], so the rule can be unit-tested without a Context. */
    fun missingFrom(present: Collection<String>): List<String> =
        requiredLibraries.filter { it !in present }

    fun missingLibraries(context: Context): List<String> =
        missingFrom(availableLibraries(context))

    /** Names of the SDK libraries this device can actually load, from the APK or the lib dir. */
    fun availableLibraries(context: Context): Set<String> {
        val names = mutableSetOf<String>()

        context.applicationInfo.nativeLibraryDir?.let { dir ->
            File(dir).list()?.let { names += it }
        }

        // 64-bit is the only ABI these libs exist for; a 32-bit unit can never load them.
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" } ?: return names

        val apk = File(context.applicationInfo.sourceDir)
        runCatching {
            ZipFile(apk).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") }
                    .forEach { names += it.name.substringAfterLast('/') }
            }
        }
        // Split APKs (if any) carry their own lib entries.
        context.applicationInfo.splitSourceDirs?.forEach { path ->
            runCatching {
                ZipFile(File(path)).use { zip ->
                    zip.entries().asSequence()
                        .filter { it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") }
                        .forEach { names += it.name.substringAfterLast('/') }
                }
            }
        }
        return names
    }
}
