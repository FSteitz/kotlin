import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

description = "Kotlin JVM metadata manipulation library"

// This library does not have stable API yet, so we're prepending "0." to the version number
version = "0.$version"

plugins {
    kotlin("jvm")
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

val shadows by configurations.creating {
    isTransitive = false
}
configurations.getByName("compileOnly").extendsFrom(shadows)
configurations.getByName("testCompile").extendsFrom(shadows)

dependencies {
    compile(project(":kotlin-stdlib"))
    shadows(project(":kotlinx-metadata"))
    shadows(project(":core:metadata"))
    shadows(project(":core:metadata.jvm"))
    shadows(protobufLite())
    testCompile(commonDep("junit:junit"))
}

noDefaultJar()

val shadowJar = task<ShadowJar>("shadowJar") {
    callGroovy("manifestAttributes", manifest, project)
    manifest.attributes["Implementation-Version"] = version

    from(the<JavaPluginConvention>().sourceSets["main"].output)
    exclude("**/*.proto")
    configurations = listOf(shadows)

    val artifactRef = outputs.files.singleFile
    runtimeJarArtifactBy(this, artifactRef)
    addArtifact("runtime", this, artifactRef)
}

sourcesJar {
    for (dependency in shadows.dependencies) {
        if (dependency is ProjectDependency) {
            val javaPlugin = dependency.dependencyProject.convention.findPlugin(JavaPluginConvention::class.java)
            if (javaPlugin != null) {
                from(javaPlugin.sourceSets["main"].allSource)
            }
        }
    }
}

javadocJar()

publish()

projectTest {
    workingDir = rootDir
}
