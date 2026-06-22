plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.control.plane.spi)
    implementation("org.eclipse.edc:asset-spi:${libs.versions.edc.get()}")
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.edc.web.spi)
    implementation(libs.jakarta.rsApi)
}