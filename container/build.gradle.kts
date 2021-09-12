import io.guthix.js5.registerPublication

plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation(deps.tukaani.xz)
    implementation(deps.apache.compress)
}

kotlin { explicitApi() }

registerPublication(name = "js5-container", description = "A low level API for modifying Jagex Store 5 caches")