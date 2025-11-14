plugins {
    `java-library`
}

dependencies {
    // EDC dependencies needed for the extension
    implementation("org.eclipse.edc:control-plane-spi:0.14.1")
    implementation("org.eclipse.edc:transfer-spi:0.14.1")
    implementation("org.eclipse.edc:data-plane-spi:0.14.1")
    implementation("org.eclipse.edc:runtime-metamodel:0.14.1")
}