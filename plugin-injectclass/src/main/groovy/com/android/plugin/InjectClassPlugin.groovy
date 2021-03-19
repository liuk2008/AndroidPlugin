package com.android.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by liuk on 2019/3/27
 */
class InjectClassPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        Logger.project = project
        Logger.error("====== 引入 " + InjectClassPlugin.name + " 插件 ======")
        def classTransform = new InjectClassTransform(project)
        project.android.registerTransform(classTransform)
        // 自定义Extension，Gradle脚本中通过Extension传递一些配置参数给自定义插件
        def extension = project.extensions.create("injectClass", InjectClassExtension)
        project.afterEvaluate { // project 配置完成后回调
            classTransform.dirClass = extension.dirClass
            classTransform.dirCode = extension.dirCode
            classTransform.jarName = extension.jarName
            classTransform.jarClass = extension.jarClass
            classTransform.jarCode = extension.jarCode
        }
    }
}

class InjectClassExtension {
    String dirClass
    String dirCode
    String jarName
    String jarClass
    String jarCode
}
