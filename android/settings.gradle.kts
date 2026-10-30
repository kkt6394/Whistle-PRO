pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WhistlePRO"
// 공개 저장소에는 엔진(:core)과 기록·동기화 규칙(:session)만 있다. 앱 모듈(:wear, :mobile)은 비공개
include(":core", ":session")
