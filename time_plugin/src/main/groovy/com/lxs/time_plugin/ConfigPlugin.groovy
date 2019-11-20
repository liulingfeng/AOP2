import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import org.gradle.api.Plugin
import org.gradle.api.Project

class ConfigPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        if (!project.plugins.hasPlugin(AppPlugin.class)) {
            throw GradleException("this plugin is not application")
        }

        def android = project.extensions.getByType(AppExtension.class)

        android.applicationVariants.all { type ->
            def buildConfigTask = project.tasks.create("DemoBuildConfig${type.name.capitalize()}")
            buildConfigTask.doLast {
                createJavaConfig()
            }
            def generateBuildConfigTask = project.tasks.getByName(type.variantData.scope.taskContainer.generateBuildConfigTask?.name)
            buildConfigTask.dependsOn(generateBuildConfigTask)
            generateBuildConfigTask.finalizedBy(buildConfigTask)
        }
    }

    void createJavaConfig() {

    }
}