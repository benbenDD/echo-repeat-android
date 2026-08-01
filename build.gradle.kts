buildscript {
    val useLocalProxy = System.getenv("ECHO_LOCAL_MAVEN_PROXY") == "1"
    repositories {
        if (useLocalProxy) {
            maven { url = uri("http://127.0.0.1:4873/maven2"); isAllowInsecureProtocol = true }
            maven { url = uri("http://127.0.0.1:4873/google"); isAllowInsecureProtocol = true }
        } else {
            google()
            mavenCentral()
        }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    }
}


