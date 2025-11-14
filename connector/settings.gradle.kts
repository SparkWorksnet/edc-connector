rootProject.name = "connector"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}
include(":edc:extensions:transfer-recovery")
include(":edc:extensions:http-streaming-datasource")
include(":edc:extensions:http-data-sink")
include(":edc:connectors:ac3-uc1")
