package com.android.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.ide.common.internal.WaitableExecutor
import com.android.utils.FileUtils
import org.apache.commons.codec.digest.DigestUtils
import org.apache.http.util.TextUtils
import org.gradle.api.Project

import java.util.concurrent.Callable

/**
 * Created by liuk on 2019/4/15
 */
class InjectClassTransform extends Transform {

    WaitableExecutor waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool()
    Project project
    String dirClass
    String dirCode
    String jarName
    String jarClass
    String jarCode

    InjectClassTransform(Project project) {
        this.project = project
    }

    /**
     * Task名称：TransformClassesWith + getName() + For + buildTypes
     * 生成目录：build/intermediates/transforms/MyTransform/
     */
    @Override
    String getName() {
        return "PreDexInjectCode"
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
//        Logger.error("======begin transform======")
//        Logger.error("是否增量编译: " + isIncremental)
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
            // 遍历jar文件，但不做操作
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
//        Logger.error("======end transform======")
    }

    void processJarInput(TransformOutputProvider outputProvider, JarInput jarInput, boolean isIncremental) {
        String jarInputName = jarInput.name
        String srcPath = jarInput.file.absolutePath
        if (jarInputName.endsWith(".jar"))
            jarInputName = jarInputName.substring(0, jarInputName.length() - 4)
        String md5Hex = DigestUtils.md5Hex(srcPath)
        // 此下一个Transform输入数据的路径
        File outputFile = outputProvider.getContentLocation(jarInputName + "-" + md5Hex, jarInput.contentTypes, jarInput.scopes, Format.JAR)
        // 处理需要修改的jar包
        if (!TextUtils.isEmpty(jarName) && !TextUtils.isEmpty(jarClass) && !TextUtils.isEmpty(jarCode)) {
            if (jarInput.name.startsWith(jarName)) {
                Logger.error("------>修改jar包中的class文件")
                Logger.error("jar : " + jarInput.name + " --> " + outputFile.absolutePath)
                InjectClass.injectJar(project, srcPath, outputFile.absolutePath, jarClass, jarCode)
                return
            }
        }
        if (isIncremental) {         // 增量编译
            if (jarInput.status != Status.NOTCHANGED)
                Logger.error("jar : " + jarInput.name + " --> " + jarInput.status)
            switch (jarInput.status) {
                case Status.NOTCHANGED:
                    break
                case Status.ADDED:
                case Status.CHANGED:
                    // Changed 状态需要先删除之前的
                    if (jarInput.status == Status.CHANGED)
                        FileUtils.deleteIfExists(outputFile)
                    // 真正transform的地方
                    FileUtils.copyFile(jarInput.file, outputFile)
                    break
                case Status.REMOVED:
                    // 移除Removed
                    FileUtils.deleteIfExists(outputFile)
                    break
            }
        } else {
            // 将修改过的字节码copy到指定目录，就可以实现编译期间干预字节码
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
                    Logger.error("dir : " + srcFile.name + " --> " + status)
                    Logger.error("srcFile path : " + srcFile.path)
                    Logger.error("outputFile Path : " + targetFile.path)
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
        // 处理dir中需要修改的class文件
        if (!TextUtils.isEmpty(dirClass) && !TextUtils.isEmpty(dirCode)) {
            InjectClass.injectDir(project, outputFile, dirClass, dirCode)
        }
    }

}
