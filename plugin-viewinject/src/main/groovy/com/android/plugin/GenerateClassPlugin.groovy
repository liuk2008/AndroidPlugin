package com.android.plugin

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by liuk on 2019/3/27
 */
class GenerateClassPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def variants = project.android.applicationVariants
        project.logger.error("====== 引入 " + GenerateClassPlugin.name + " 插件 ======")
        def classTransform = new GenerateClassTransform(project)
        variants.all { BaseVariant variant ->
            def packageName = variant.generateBuildConfig.buildConfigPackageName.get()
            project.logger.error("====== packageName: " + packageName + " ======")
            classTransform.packageName = packageName
        }
        // 注册一个Transform
        project.android.registerTransform(classTransform)
    }
}
