plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.control.plane.spi)
    implementation(libs.edc.transfer.spi)
    implementation(libs.edc.data.plane.spi)
    implementation(libs.edc.runtime.metamodel)

    // MinIO client
    implementation(libs.minio.io)
}