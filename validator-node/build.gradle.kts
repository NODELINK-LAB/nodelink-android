plugins { id("org.jetbrains.kotlin.jvm"); application }
kotlin { jvmToolchain(17) }
application { mainClass.set("org.nodelink.validator.NodeKt") }
dependencies { implementation(project(":ledger-core")) }