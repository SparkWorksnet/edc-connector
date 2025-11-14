plugins {
    `java-library`
    alias(libs.plugins.edc.build)
}

repositories {
    mavenCentral()
}

val edcVersion = libs.versions.edc

allprojects {
    apply(plugin = "org.eclipse.edc.edc-build")

    configure<org.eclipse.edc.plugins.edcbuild.extensions.BuildExtension> {
        publish.set(false)
    }

    configure<CheckstyleExtension> {
        configFile = rootProject.file("resources/checkstyle-config.xml")
        configDirectory.set(rootProject.file("resources"))
    }

    tasks.test {
        testLogging {
            showStandardStreams = true
        }
    }

}


