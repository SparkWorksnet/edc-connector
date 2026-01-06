plugins {
    `java-library`
}

dependencies {
    // EDC dependencies needed for the extension
    implementation(libs.edc.control.plane.spi)
    implementation(libs.edc.transfer.spi)
    implementation(libs.edc.data.plane.spi)
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.edc.http.spi)
    implementation(libs.edc.data.plane.core)
    implementation(libs.edc.data.plane.http)

    // HTTP client and JSON parsing for Piveau API
    implementation(libs.okhttp)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)

    // MinIO client for MinIO streaming data source
    implementation(libs.minio.io)

    // Lombok for reducing boilerplate code
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
