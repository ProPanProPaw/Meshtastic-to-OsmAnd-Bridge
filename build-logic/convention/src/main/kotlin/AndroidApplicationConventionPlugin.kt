/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.android.build.api.dsl.ApplicationExtension
import com.geeksville.mesh.buildlogic.configureKotlinAndroid
import com.geeksville.mesh.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import java.util.Properties

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {

            apply(plugin = "com.android.application")
            apply(plugin = "org.jetbrains.kotlin.android")
            apply(plugin = "meshtastic.android.lint")
            apply(plugin = "meshtastic.detekt")
            apply(plugin = "meshtastic.spotless")
            apply(plugin = "com.autonomousapps.dependency-analysis")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)


                val props = Properties()
                val propFile = rootProject.file("local.properties")
                if (propFile.exists()) {
                    propFile.inputStream().use { props.load(it) }
                }

                signingConfigs {
                    create("release") {
                        storeFile = props.getProperty("release.keystore.path")?.let { file(it) }
                        storePassword = props.getProperty("release.keystore.password")
                        keyAlias = props.getProperty("release.key.alias")
                        keyPassword = props.getProperty("release.key.password")
                    }
                }

                defaultConfig.targetSdk = 36
                testOptions.animationsDisabled = true

                defaultConfig {
                    targetSdk = 36
                    testInstrumentationRunner = "com.geeksville.mesh.TestRunner"
                    vectorDrawables.useSupportLibrary = true
                }

                buildTypes {
                    getByName("release") {
                        //isMinifyEnabled = true // TODO: crash
                        isMinifyEnabled = false
                        //isShrinkResources = true  // TODO: crash
                        isShrinkResources = false
                        signingConfig = signingConfigs.getByName("release")
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                    getByName("debug") {
                        applicationIdSuffix = ".debug"
                        isDebuggable = true
                        isPseudoLocalesEnabled = true
                    }
                }

                buildFeatures {
                    buildConfig = true
                }
            }
        }
    }
}
