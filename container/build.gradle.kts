import io.guthix.js5.registerPublication

plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation(libs.tukaani.xz)
    implementation(libs.apache.compress)
}

kotlin { explicitApi() }

registerPublication(name = "js5-container", description = "A low level API for modifying Jagex Store 5 caches")