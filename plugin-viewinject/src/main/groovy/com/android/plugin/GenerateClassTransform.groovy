package com.android.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.ide.common.internal.WaitableExecutor
import com.android.utils.FileUtils
import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.Project

import java.util.concurrent.Callable

/**
 * 读取R.class中的id值
 *      1、低版本Gradle插件，会在指定目录生成R.class，通过读取 R.class文件中的内部类Id.class，在指定目录生成新的Id.class
 *      2、高版本Gradle插件，R.class文件不再输出，只存在于R.jar包中，无法通过读取R.class文件的内部类Id.class，生成新的Id.class
 *          解决办法：
 *              1、读取R.jar包中的 R.class文件，获取内部类Id.class内容
 *              2、在R.jar目录下，生成指定的 com.viewinject.bindview.Id.class
 *              3、将R.jar包中的class文件，复制在transform目录下，生成新的jar包
 *              4、将新生成的 Id.class，输出到jar包中
 *
 * Created by liuk on 2019/4/15
 */
class GenerateClassTransform extends Transform {

    WaitableExecutor waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool()
    Project project
    String packageName

    GenerateClassTransform(Project project) {
        this.project = project
    }

    /**
     * Task名称：TransformClassesWith + getName() + For + buildTypes
     * 生成目录：build/intermediates/transforms/MyTransform/
     */
    @Override
    String getName() {
        return "PreDexGenerateClass"
    }

    /**
     * 需要处理的数据类型：
     * CONTENT_CLASS：表示处理java的class文件，可能是 jar 包也可能是目录
     * CONTENT_RESOURCES：表示处理java的资源
     */
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    /**
     * 指定Transform的作用范围
     *     PROJECT                       只有项目内容
     *     PROJECT_LOCAL_DEPS            只有项目的本地依赖(本地jar,aar)
     *     SUB_PROJECTS                  只有子项目
     *     SUB_PROJECTS_LOCAL_DEPS       只有子项目的本地依赖(本地jar,aar)
     *     PROVIDED_ONLY                 只提供本地或远程依赖项
     *     EXTERNAL_LIBRARIES            只有外部库
     *     TESTED_CODE                   测试代码
     */
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     * 指明当前Transform是否支持增量编译
     */
    @Override
    boolean isIncremental() {
        return true
    }

    /**
     * Transform中的核心方法
     */
    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        boolean isIncremental = transformInvocation.isIncremental()
        // OutputProvider管理输出路径，如果消费型输入为空，你会发现OutputProvider == null
        TransformOutputProvider outputProvider = transformInvocation.outputProvider
        // 如果非增量，则清空旧的输出内容
        if (!isIncremental)
            outputProvider.deleteAll()
//        project.logger.error("====== begin transform ======")
//        project.logger.error("是否增量编译: " + isIncremental)
        transformInvocation.inputs.each { TransformInput input ->
            // 遍历文件夹
            input.directoryInputs.each { DirectoryInput directoryInput ->
//                processDirectoryInput(outputProvider, directoryInput, isIncremental)
                // 多线程处理文件
                waitableExecutor.execute(new Callable<Object>() {
                    @Override
                    Object call() throws Exception {
                        processDirectoryInput(outputProvider, directoryInput, isIncremental)
                        return null
                    }
                })
            }
            // 遍历jar文件
            input.jarInputs.each { JarInput jarInput ->
//                processJarInput(outputProvider, jarInput, isIncremental)
                // 异步并发处理jar/class
                waitableExecutor.execute(new Callable<Object>() {
                    @Override
                    Object call() throws Exception {
                        processJarInput(outputProvider, jarInput, isIncremental)
                        return null
                    }
                })
            }
        }
        // 等待所有任务结束
        waitableExecutor.waitForTasksWithQuickFail(true)
//        project.logger.error("====== end transform ======")
    }

    void processJarInput(TransformOutputProvider outputProvider, JarInput jarInput, boolean isIncremental) {

        String jarName = jarInput.name
        String srcPath = jarInput.file.absolutePath
        if (jarName.endsWith(".jar"))
            jarName = jarName.substring(0, jarName.length() - 4)
        String md5Hex = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
        // 此下一个Transform输入数据的路径
        File outputFile = outputProvider.getContentLocation(jarName + "-" + md5Hex, jarInput.contentTypes, jarInput.scopes, Format.JAR)

        /*
         * 读取R.jar包，生成Id.class文件
         * Gradle插件升级后，R.class文件不再输出，只存在于R.jar包中，无法通过读取 R.class文件的内部类Id.class，生成新的Id.class
         * 解决办法：
         *      1、读取R.jar包中的 R.class文件，获取内部类Id.class内容
         *      2、在R.jar目录下，生成指定的 com.viewinject.bindview.Id.class
         *      3、将R.jar包中的class文件，复制在transform目录下，生成新的jar包
         *      4、将新生成的 Id.class，输出到jar包中
         */
        if (srcPath.contains("R.jar")) {
            project.logger.error("------>修改R.jar包中的class文件")
            project.logger.error("jar : " + jarInput.name)
            project.logger.error("srcPath : " + " --> " + srcPath)
            project.logger.error("outputPath : " + " --> " + outputFile.absolutePath)
            GenerateClass.generateIdForJar(project, packageName, srcPath, outputFile.absolutePath)
            return
        }

        if (isIncremental) {   // 增量编译
            if (jarInput.status != Status.NOTCHANGED)
                project.logger.error("jar : " + jarInput.name + " --> " + jarInput.status)
            switch (jarInput.status) {
                case Status.NOTCHANGED:
                    break
                case Status.ADDED:
                case Status.CHANGED:
                    // Changed 状态需要先删除之前的
                    if (jarInput.status == Status.CHANGED)
                        FileUtils.deleteIfExists(outputFile)
                    // 真正transform的地方
                    // 将修改过的字节码copy到指定目录，就可以实现编译期间干预字节码
                    FileUtils.copyFile(jarInput.file, outputFile)
                    break
                case Status.REMOVED:
                    // 移除Removed
                    FileUtils.deleteIfExists(outputFile)
                    break
            }
        } else { // 非增量编译
            FileUtils.copyFile(jarInput.file, outputFile)
        }

    }

    void processDirectoryInput(TransformOutputProvider outputProvider, DirectoryInput directoryInput, boolean isIncremental) {
        String dirName = directoryInput.name
        File outputFile = outputProvider.getContentLocation(dirName, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
        if (isIncremental) {
            String srcDir = directoryInput.file.getAbsolutePath()
            String outputDir = outputFile.getAbsolutePath()
            Map<File, Status> fileStatusMap = directoryInput.getChangedFiles()
            for (Map.Entry<File, Status> changedFile : fileStatusMap.entrySet()) {
                File srcFile = changedFile.getKey()
                Status status = changedFile.getValue();
                String newOutputPath = srcFile.getAbsolutePath().replace(srcDir, outputDir);
                File targetFile = new File(newOutputPath)
                if (status != Status.NOTCHANGED) {
                    project.logger.error("dir : " + srcFile.name + " --> " + status)
                    project.logger.error("srcFile path : " + srcFile.path)
                    project.logger.error("outputFile Path : " + targetFile.path)
                }
                switch (status) {
                    case Status.NOTCHANGED:
                        break
                    case Status.ADDED:
                    case Status.CHANGED:
                        if (status == Status.CHANGED)
                            FileUtils.deleteIfExists(targetFile)
                        FileUtils.copyFile(srcFile, targetFile)
                        break
                    case Status.REMOVED:
                        FileUtils.deleteIfExists(targetFile)
                        break
                }
            }
        } else {
            FileUtils.copyDirectory(directoryInput.file, outputFile)
        }

        /**
         * 低版本Gradle插件，会在指定目录生成R.class，通过读取 R.class文件中的内部类Id.class，在指定目录生成新的Id.class
         */
//        GenerateClass.generateIdForDir(project, packageName, outputFile.absolutePath)
    }

}
