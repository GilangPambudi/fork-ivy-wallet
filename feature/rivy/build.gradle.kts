plugins {
    id("ivy.feature")
}

android {
    namespace = "com.ivy.rivy"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)

    implementation(libs.bundles.ktor)
    implementation(libs.androidx.work)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.google.identity.googleid)

    testImplementation(projects.shared.ui.testing)
}
