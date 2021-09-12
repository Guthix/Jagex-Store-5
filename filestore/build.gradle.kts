import io.guthix.js5.registerPublication

//import io.guthix.js5.registerPublication

dependencies {
    implementation(libs.bouncycastle)
    api(project(":container"))
    testImplementation(testFixtures(project(":container")))
}

kotlin { explicitApi() }

registerPublication(name = "js5-filestore", description = "A library for modifying Jagex Store 5 files")