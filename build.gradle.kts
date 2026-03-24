/**
 * Root Build Configuration
 *
 * This is the top-level build file for the CrowdLink project.
 * It defines the common plugins and configurations applied to all modules (subprojects)
 * in the application to ensure consistency.
 */
// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
