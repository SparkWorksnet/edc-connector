plugins {
    `java-library`
}

dependencies {
    // EDC dependencies needed for the extension
    implementation(libs.edc.control.plane.spi)
    implementation(libs.edc.transfer.spi)
    implementation(libs.edc.data.plane.spi)
    implementation(libs.edc.runtime.metamodel)
}
