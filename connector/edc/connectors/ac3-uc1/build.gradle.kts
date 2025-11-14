plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

dependencies {
    // Common extensions module
    implementation(project(":edc:extensions:transfer-recovery"))
    implementation(project(":edc:extensions:http-streaming-datasource"))

    implementation(libs.edc.runtime.core)
    implementation(libs.edc.connector.core)
    implementation(libs.edc.control.api.configuration)
    implementation(libs.edc.control.plane.api.client)
    implementation(libs.edc.control.plane.api)
    implementation(libs.edc.control.plane.core)
    implementation(libs.edc.token.core)
    implementation(libs.edc.dsp)
    implementation(libs.edc.http)
    implementation(libs.edc.configuration.filesystem)
    implementation(libs.edc.iam.mock)
    implementation(libs.edc.management.api)
    implementation(libs.edc.transfer.data.plane.signaling)
    implementation(libs.edc.validator.data.address.http.data)

    implementation(libs.edc.edr.cache.api)
    implementation(libs.edc.edr.store.core)
    implementation(libs.edc.edr.store.receiver)

    implementation(libs.edc.data.plane.selector.api)
    implementation(libs.edc.data.plane.selector.core)

    implementation(libs.edc.data.plane.self.registration)
    implementation(libs.edc.data.plane.signaling.api)
    implementation(libs.edc.data.plane.core)
    implementation(libs.edc.data.plane.http)
    implementation(libs.edc.data.plane.iam)

    // ========================================
    // SQL Store Extensions
    // ========================================
    implementation("org.eclipse.edc:sql-core:0.14.1")
    implementation("org.eclipse.edc:sql-pool-apache-commons:0.14.1")
    implementation("org.eclipse.edc:transaction-local:0.14.1")

    // Asset store
    implementation("org.eclipse.edc:asset-index-sql:0.14.1")

    // Contract definition store
    implementation("org.eclipse.edc:contract-definition-store-sql:0.14.1")

    // Contract negotiation store
    implementation("org.eclipse.edc:contract-negotiation-store-sql:0.14.1")

    // Transfer process store
    implementation("org.eclipse.edc:transfer-process-store-sql:0.14.1")

    // Policy store
    implementation("org.eclipse.edc:policy-definition-store-sql:0.14.1")

    // Policy monitor store (optional)
    implementation("org.eclipse.edc:policy-monitor-store-sql:0.14.1")

    // ========================================
    // MariaDB JDBC Driver
    // ========================================
    implementation("org.postgresql:postgresql:42.7.3")
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

var distTar = tasks.getByName("distTar")
var distZip = tasks.getByName("distZip")

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    archiveFileName.set("connector.jar")
    dependsOn(distTar, distZip)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
