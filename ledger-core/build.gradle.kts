plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies { implementation("org.web3j:crypto:4.9.8"); testImplementation("junit:junit:4.13.2") }