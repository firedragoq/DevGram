plugins {
    `kotlin-dsl`
    // kotlin("jvm") version "2.1.0"
}

gradlePlugin {
    plugins {
        // DevGram: LottieMetaPlugin убран — ResLottieMeta стаббим вручную (runtime-парсинг lottie)
        register("testGenerator") {
            id = "test-generator"
            implementationClass = "com.example.TestGeneratorPlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}
/*
val checkEmojiKeyboard by tasks.registering(GenerateSchemeTask::class) {

}
*/
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
    incremental = false
}

dependencies {
    implementation(gradleApi())
    // DevGram: AGP 8.10.1 и gson убраны — были нужны только удалённому LottieMetaPlugin,
    // а AGP 8.10.1 в classpath buildSrc ломал Chaquopy 16.1.0 (packageDebugAssets).

    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.github.javaparser:javaparser-core:3.25.4")
    implementation("com.squareup:kotlinpoet:1.15.0")
}