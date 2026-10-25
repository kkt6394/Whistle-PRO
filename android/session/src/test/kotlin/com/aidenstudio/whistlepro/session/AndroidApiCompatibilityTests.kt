package com.aidenstudio.whistlepro.session

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * :session·:core는 순수 JVM 모듈이라 Android lint의 API 수준 검사(NewApi)를 받지 않는다. JDK 17에는 있지만 폰 minSdk 26의
 * Android에는 없는 `java.*` 메서드를 쓰면 에뮬레이터(API 36)·JVM 테스트는 멀쩡하고 Android 12·13 실기기에서만 `NoSuchMethodError`로
 * 튕긴다 (2026-10-25: `LocalDate.ofInstant`(API 34)로 노트10 시즌 통계 튕김). 컴파일된 클래스가 참조하는 `java.*`·`javax.*`
 * 메서드·클래스를 API 26 `android.jar`(stub)와 대조한다. 워치(minSdk 30)도 같은 모듈을 쓰므로 26 기준이면 둘 다 안전하다.
 *
 * `android.jar` 위치는 Gradle이 시스템 속성 `androidJar`로 준다(`sdkmanager "platforms;android-26"`로 설치).
 */
class AndroidApiCompatibilityTests {
    @Test fun sessionAndCoreReferenceOnlyApi26JavaMembers() {
        val jarPath = System.getProperty("androidJar") ?: fail("시스템 속성 androidJar가 없다 (session/build.gradle.kts tasks.test)")
        val jar = File(jarPath)
        assertTrue(jar.isFile, "API 26 android.jar가 없다: $jarPath — `sdkmanager \"platforms;android-26\"`로 설치")
        val android = AndroidStubs(JarFile(jar))

        val classes = ourClassFiles()
        assertTrue(classes.isNotEmpty(), "검사할 클래스가 없다 (클래스패스: ${System.getProperty("java.class.path")})")

        val missing = sortedSetOf<String>()
        for ((name, bytes) in classes) {
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(access: Int, mName: String, desc: String, sig: String?, ex: Array<out String>?): MethodVisitor =
                    object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(opcode: Int, owner: String, method: String, mDesc: String, itf: Boolean) {
                            if (!android.covers(owner)) return
                            if (!android.hasMethod(owner, method, mDesc)) missing += "${owner.replace('/', '.')}.$method$mDesc  ← $name.$mName"
                        }

                        override fun visitFieldInsn(opcode: Int, owner: String, field: String, fDesc: String) {
                            if (!android.covers(owner)) return
                            if (!android.hasField(owner, field)) missing += "${owner.replace('/', '.')}.$field  ← $name.$mName"
                        }

                        override fun visitTypeInsn(opcode: Int, type: String) {
                            if (android.covers(type) && !android.hasClass(type)) missing += "${type.replace('/', '.')}  ← $name.$mName"
                        }
                    }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        assertTrue(missing.isEmpty(), "Android API 26에 없는 java.* 참조 ${missing.size}개 (실기기에서 NoSuchMethodError):\n" + missing.joinToString("\n"))
    }

    /** 테스트 클래스패스에서 :session·:core의 본 코드(클래스 디렉터리·jar)만 고른다 (테스트 클래스·외부 라이브러리는 아님) */
    private fun ourClassFiles(): List<Pair<String, ByteArray>> {
        val androidRoot = File(System.getProperty("androidRoot") ?: fail("시스템 속성 androidRoot가 없다")).canonicalPath
        val result = mutableListOf<Pair<String, ByteArray>>()
        for (entry in System.getProperty("java.class.path").split(File.pathSeparator)) {
            val file = File(entry)
            val path = runCatching { file.canonicalPath }.getOrNull() ?: continue
            if (!path.startsWith(androidRoot)) continue
            val module = path.removePrefix(androidRoot).trimStart(File.separatorChar).substringBefore(File.separatorChar)
            if (module != "session" && module != "core") continue
            if (path.contains("${File.separator}test${File.separator}") || path.endsWith("-test.jar")) continue
            if (file.isDirectory) {
                file.walk().filter { it.isFile && it.extension == "class" }.forEach { result += it.relativeTo(file).path.removeSuffix(".class") to it.readBytes() }
            } else if (file.isFile && file.extension == "jar") {
                JarFile(file).use { jar ->
                    jar.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { result += it.name.removeSuffix(".class") to jar.getInputStream(it).readBytes() }
                }
            }
        }
        return result
    }

    /** android.jar의 stub 클래스: 이름 → 상위 클래스·인터페이스·메서드(name+desc)·필드 */
    private class AndroidStubs(private val jar: JarFile) {
        private class Stub(val superName: String?, val interfaces: List<String>, val methods: Set<String>, val fields: Set<String>)

        private val cache = HashMap<String, Stub?>()

        /** 검사 대상: `java.*`·`javax.*` (배열은 제외) */
        fun covers(internalName: String): Boolean = internalName.startsWith("java/") || internalName.startsWith("javax/")

        fun hasClass(name: String): Boolean = stub(name) != null

        fun hasMethod(owner: String, method: String, desc: String): Boolean = hierarchy(owner).any { "$method$desc" in it.methods }

        fun hasField(owner: String, field: String): Boolean = hierarchy(owner).any { field in it.fields }

        /** 자신 → 상위 클래스 → 인터페이스 (인터페이스의 default 메서드·Object 메서드까지). 모르는 클래스면 빈 목록 */
        private fun hierarchy(name: String): List<Stub> {
            val seen = LinkedHashMap<String, Stub>()
            fun walk(n: String) {
                if (n in seen) return
                val s = stub(n) ?: return
                seen[n] = s
                s.superName?.let(::walk)
                s.interfaces.forEach(::walk)
                if (s.superName == null && n != "java/lang/Object") walk("java/lang/Object")
            }
            walk(name)
            return seen.values.toList()
        }

        private fun stub(name: String): Stub? = cache.getOrPut(name) {
            val entry = jar.getJarEntry("$name.class") ?: return@getOrPut null
            val methods = HashSet<String>()
            val fields = HashSet<String>()
            var superName: String? = null
            var interfaces: List<String> = emptyList()
            ClassReader(jar.getInputStream(entry).readBytes()).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visit(version: Int, access: Int, n: String, sig: String?, sup: String?, itf: Array<out String>?) {
                    superName = sup
                    interfaces = itf?.toList().orEmpty()
                }

                override fun visitMethod(access: Int, n: String, desc: String, sig: String?, ex: Array<out String>?): MethodVisitor? {
                    methods += "$n$desc"
                    return null
                }

                override fun visitField(access: Int, n: String, desc: String, sig: String?, value: Any?) = null.also { fields += n }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            Stub(superName, interfaces, methods, fields)
        }
    }
}
