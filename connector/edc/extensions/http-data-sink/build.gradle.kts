plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.control.plane.spi)
    implementation(libs.edc.transfer.spi)
    implementation(libs.edc.data.plane.spi)
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.edc.http.spi)
    implementation(libs.edc.data.plane.core)
    implementation("org.eclipse.edc:data-plane-http-spi:${libs.versions.edc.get()}")
}
