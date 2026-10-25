import java.util.Properties
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core"))
    api(libs.serialization.json)
    testImplementation(kotlin("test"))
    // 동기화 로직(suspend 함수)을 테스트에서 부른다. 본 코드는 코루틴 라이브러리를 쓰지 않는다
    // (워치 앱이 coroutines 1.9.0에 묶여 있어 :session이 더 높은 버전을 끌어오면 안 된다)
    testImplementation(libs.coroutines.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.asm)
    testRuntimeOnly(libs.junit.launcher)
}

// Android SDK 위치: local.properties의 sdk.dir, 없으면 ANDROID_HOME (README "빌드")
val androidSdk: String? = Properties().run {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
    getProperty("sdk.dir")
} ?: System.getenv("ANDROID_HOME")

tasks.test {
    useJUnitPlatform()
    // Swift가 만든 공용 픽스처 위치 (저장소 루트/shared/fixtures)
    systemProperty("fixtureDir", rootProject.projectDir.resolve("../shared/fixtures").canonicalPath)
    // AndroidApiCompatibilityTests: 이 모듈·:core가 참조하는 java.* 멤버를 폰 minSdk 26의 android.jar와 대조한다
    // (순수 JVM 모듈은 lint NewApi를 받지 않는다. `sdkmanager "platforms;android-26"`로 설치)
    systemProperty("androidJar", "${androidSdk ?: ""}/platforms/android-26/android.jar")
    systemProperty("androidRoot", rootProject.projectDir.canonicalPath)
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
