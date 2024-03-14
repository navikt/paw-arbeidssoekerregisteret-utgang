plugins {
    kotlin("jvm")
}
val javaVersion: String by project
val jvmVersion = JavaVersion.valueOf("VERSION_$javaVersion")
dependencies {
    implementation(jacskon.jacksonDatatypeJsr310)
    implementation(jacskon.jacksonModuleKotlin)
    implementation(ktorClient.contentNegotiation)
    implementation(ktorClient.core)
    implementation(ktorClient.cio)
    implementation(jacskon.ktorSerialization)
    implementation(navSecurity.tokenClient)
    implementation(navCommon.tokenClient)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(jvmVersion.majorVersion)
    }
}
