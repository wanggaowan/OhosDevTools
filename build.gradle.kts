plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.wanggaowan"
version = "1.0-SNAPSHOT"

repositories {
    maven { setUrl("https://maven.aliyun.com/repository/central") }
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        local("/Users/wgw/Documents/develop/project/ide plugin/test ide/DevEco-Studio.app")
        localPlugin("/Users/wgw/Documents/develop/project/ide plugin/test ide/DevEco-Studio.app/Contents/plugins/json")
        // localPlugin("/Users/wgw/Documents/develop/project/ide plugin/test ide/DevEco-Studio.app/Contents/plugins/harmony")
        // plugins("com.intellij.modules.json:243.24978.46.36.601249")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    // 是否开启增量构建
    instrumentCode = false

    pluginConfiguration {
        group = "com.wanggaowan"
        name = "OhosDevTools"
        version = "1.0"

        ideaVersion {
            sinceBuild = "243"
            untilBuild = "10000.*"
        }

        // changeNotes = """
        //     Initial version
        // """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
