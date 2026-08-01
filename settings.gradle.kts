val useLocalProxy = System.getenv("ECHO_LOCAL_MAVEN_PROXY") == "1"
pluginManagement {
    repositories { gradlePluginPortal(); google(); mavenCentral() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useLocalProxy) {
            maven { url = uri("http://127.0.0.1:4873/maven2"); isAllowInsecureProtocol = true }
            maven { url = uri("http://127.0.0.1:4873/google"); isAllowInsecureProtocol = true }
        } else {
            google()
            mavenCentral()
        }
    }
}
rootProject.name = "EchoEnglish"
include(":app")
