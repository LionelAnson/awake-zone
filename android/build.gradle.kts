plugins {
    id("com.android.application") version "8.13.2" apply false
    kotlin("android") version "2.2.21" apply false
    kotlin("jvm") version "2.2.21" apply false
}
allprojects {
    dependencyLocking { lockAllConfigurations() }
    tasks.withType<Test>().configureEach {
        maxHeapSize = "512m"
        jvmArgs("-Dfile.encoding=UTF-8")
    }
}
