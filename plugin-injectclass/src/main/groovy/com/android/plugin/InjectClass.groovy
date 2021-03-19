package com.android.plugin

import com.google.common.io.ByteStreams
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Created by liuk on 2019/3/27
 */
class InjectClass {

    private static ClassPool pool = ClassPool.getDefault()

    static void injectDir(Project project, File outputDir, String injectClass, String injectCode) {

        pool.appendClassPath(outputDir.absolutePath)
        pool.appendClassPath(project.android.bootClasspath[0].toString())

        if (outputDir.isDirectory()) {
            // 遍历文件夹
            outputDir.eachFileRecurse { File file ->
                if (file.isFile() && file.name.endsWith(".class")) {
                    String fileName = file.absolutePath.replace(outputDir.absolutePath, "")
                            .replace("\\", ".").substring(1)
                    String className = injectClass + ".class"
                    if (fileName == className) {
                        Logger.error("------>修改dir目录中的class文件")
                        Logger.error("file : " + file.name + " --> " + file.absolutePath)
                        Logger.error("injectClass : " + injectClass)
                        injectDirClass(outputDir.absolutePath, injectClass, injectCode)
                    }
                }
            }
        }

    }

    static void injectJar(Project project, String srcPath, String outputPath, String injectClass, String injectCode) {
        Logger.error("srcPath : " + srcPath)
        Logger.error("outputPath : " + outputPath)
        // 添加jar包路径
        pool.appendClassPath(srcPath)
        pool.appendClassPath(project.android.bootClasspath[0].toString())
        // 读取jar包
        JarFile jarFile = new JarFile(srcPath)
        // 定义jar包输出流
        FileOutputStream fos = null
        JarOutputStream jos = null
        try {
            fos = new FileOutputStream(new File(outputPath))
            jos = new JarOutputStream(fos)
            Enumeration<JarEntry> entrys = jarFile.entries()
            while (entrys.hasMoreElements()) {
                // 读取jar包中的元素
                JarEntry jarEntry = entrys.nextElement()
                String jarEntryName = jarEntry.name
                Logger.error("jarEntry : " + jarEntryName)
                InputStream jis = jarFile.getInputStream(jarEntry)
                // 向jar包写入entry
                jos.putNextEntry(new JarEntry(jarEntry.name))
                String fileName = jarEntryName.replace('/', '.')
                String className = injectClass + ".class"
                if (fileName == className) {
                    Logger.error("injectClass : " + injectClass)
                    injectJarClass(jos, injectClass, injectCode)
                } else {
                    ByteStreams.copy(jis, jos)
                }
            }
        } finally {
            if (jos != null)
                jos.close()
            if (fos != null)
                fos.close()
        }
    }

    private static void injectDirClass(String path, String className, String injectCode) {
        CtClass ctClass = pool.getCtClass(className)
        // 解冻
        if (ctClass.isFrozen()) ctClass.defrost()
        // 获取到OnCreate方法
        CtMethod ctMethod = ctClass.getDeclaredMethod("onCreate")
        // 在方法开始注入代码
        ctMethod.insertBefore(injectCode)
        ctClass.writeFile(path)
        ctClass.detach()// 释放
    }


    private static void injectJarClass(JarOutputStream jos, String className, String injectCode) {
        CtClass ctClass = pool.getCtClass(className)
        // 解冻
        if (ctClass.isFrozen()) ctClass.defrost()
        // 获取到OnCreate方法
        CtMethod ctMethod = ctClass.getDeclaredMethod("onCreate")
        // 在方法开始注入代码
        ctMethod.insertBefore(injectCode)
        jos.write(ctClass.toBytecode())
        ctClass.detach()// 释放
    }

}

