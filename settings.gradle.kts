plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.4.0"
    kotlin("jvm") version "1.9.20" apply false
}

include (
     "utgang-pdl",
     "utgang-formidlingsgruppe"
 )