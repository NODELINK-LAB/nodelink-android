plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace="org.nodelink"; compileSdk=35; defaultConfig { applicationId="org.nodelink"; minSdk=29; targetSdk=35; versionCode=1; versionName="0.1" } }
dependencies { implementation("com.journeyapps:zxing-android-embedded:4.3.0"); implementation("org.web3j:crypto:4.9.8") }
android { compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 } }
kotlin { jvmToolchain(17) }
android { packaging { resources { excludes += setOf("META-INF/DISCLAIMER", "META-INF/versions/9/OSGI-INF/MANIFEST.MF") } } }
dependencies { implementation(project(":identity-core")); implementation(project(":network-core")); implementation(project(":ledger-core")) }