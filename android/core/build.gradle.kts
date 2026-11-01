import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Логика приложения без Android: расчёты жизни, модели, напоминания, страны.
// Проверяется обычными тестами: ./gradlew :core:test
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

sourceSets {
    test {
        // Тесты читают те же файлы карты и границ, что и приложения.
        resources.srcDirs("../../Shared/Resources", "../../MementoMori/Resources")
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
