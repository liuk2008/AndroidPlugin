package com.android.plugin

import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import javassist.ClassPath
import javassist.ClassPool
import javassist.CtClass
import javassist.CtField
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream


/*
 * 读取R.jar包，生成Id.class文件
 * Gradle插件升级后，R.class文件不再输出，只存在于R.jar包中，无法通过读取 R.class文件的内部类Id.class，生成新的Id.class
 * 解决办法：
 *      1、读取R.jar包中的 R.class文件，获取内部类Id.class内容
 *      2、在R.jar目录下，生成指定的 com.viewinject.bindview.Id.class
 *      3、将R.jar包中的class文件，复制在transform目录下，生成新的jar包
 *      4、将新生成的 Id.class，输出到jar包中
 *
 * Created by liuk on 2019/3/27
 */

class GenerateClass {

    // 初始化类池
    private final static ClassPool pool = ClassPool.getDefault()

    static void generateIdForJar(Project project, String packageName, String srcPath, String outputPath) {

        // 将当前路径加入类池 , 不然找不到这个类
        ClassPath classPath = pool.appendClassPath(srcPath)

        // 定义Class类
        CtClass rCtClass = null, idClass = null
        // 定义jar包输出流
        FileOutputStream fos = null
        JarOutputStream jos = null

        try {
            //  读取 R.jar 包，加载 R.class 文件
            String className = packageName + ".R" + "\$id"
            rCtClass = pool.getOrNull(className)
            if (rCtClass == null)
                return

            // 获取 R.class 文件中的内部类，生成Id.class
            idClass = pool.makeClass("com.viewinject.bindview.Id")
            project.logger.error("====== 扫描 " + rCtClass.name + " 文件 ======")
            // 解冻
            if (rCtClass.isFrozen()) rCtClass.defrost()
            // 会提示 R.jar: 另一个程序正在使用此文件，进程无法访问。
            CtField[] ctFields = rCtClass.getDeclaredFields()
            ctFields.each { CtField ctField ->
                String name = ctField.name
                int value = ctField.constantValue
                String field = "  public static final int " + name + " = " + value + ";"
                CtField idField = CtField.make(field, idClass)
                idClass.addField(idField)
                project.logger.error("id : " + name + " --> " + value)
            }
            project.logger.error("====== 共计" + ctFields.length + "个id ======")

            // 设置 jar 包输出路径
            fos = new FileOutputStream(new File(outputPath))
            jos = new JarOutputStream(fos)
            project.logger.error("====== 指定目录：======" + outputPath)
            // 读取 jar包
            JarFile jarFile = new JarFile(srcPath)
            Enumeration<JarEntry> entries = jarFile.entries()

            project.logger.error("====== 添加 Id.class 文件 ======")
            // 将指定目录下新 Id.class，输出到jar包中
            jos.putNextEntry(new JarEntry("com/viewinject/bindview/Id.class"))
            jos.write(idClass.toBytecode())
            project.logger.error("====== 生成 com.viewject.bindview.Id.class 文件 ======")

            // 遍历jar包，在指定目录生成新的jar包
            while (entries.hasMoreElements()) {
                // 读取jar包中的元素
                JarEntry jarEntry = entries.nextElement()
                InputStream jis = jarFile.getInputStream(jarEntry)
                // 向jar包写入entry
                jos.putNextEntry(new JarEntry(jarEntry.name))
                // 向指定路径下的jar包复制class文件
                ByteStreams.copy(jis, jos)
                jis.close()
            }
            jarFile.close()
            project.logger.error("====== 生成指定目录的 jar 包 ======")

        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            // 从ClassPool中释放，避免内存溢出
            if (rCtClass != null)
                rCtClass.detach()
            if (idClass != null)
                idClass.detach()
            // 释放Jar包输出流
            if (jos != null)
                jos.close()
            if (fos != null)
                fos.close()
            pool.removeClassPath(classPath)
        }

    }

    static void generateIdForDir(Project project, String packageName, String path) {
        CtClass rCtClass = null
        CtClass idClass = null
        try {

//            加入android.jar ， 不然找不到android相关的所有类
//            String androidJarPath = project.android.bootClasspath[0].toString()
//            pool.appendClassPath(androidJarPath)

//            将当前路径加入类池 , 不然找不到这个类
            pool.appendClassPath(path)

//            加载 R.class 文件
            String className = packageName + ".R"
            rCtClass = pool.getOrNull(className)
            if (rCtClass == null)
                return

//            修改class文件时，需要解冻
//            if (rCtClass.isFrozen()) ctClass.defrost()

//            删除旧文件
            String idPath = path + "\\com\\viewinject\\bindview\\Id.class"
            File file = new File(idPath)
            FileUtils.deleteIfExists(file)

//            获取R.class文件中的内部类
            CtClass[] rCtClasses = rCtClass.getNestedClasses()
            idClass = pool.makeClass("com.viewinject.bindview.Id")

            // 创建新Id.class
            rCtClasses.each { CtClass innerClass ->
                if ((className + "\$id").equals(innerClass.name)) {
                    project.logger.error("R.jar : -----> " + path)
                    project.logger.error("====== 扫描 " + innerClass.name + " 文件 ======")
                    CtField[] ctFields = innerClass.getDeclaredFields()
                    ctFields.each { CtField ctField ->
                        String name = ctField.name
                        int value = ctField.constantValue
                        String field = "  public static final int " + name + " = " + value + ";"
                        CtField idField = CtField.make(field, idClass)
                        idClass.addField(idField)
                        project.logger.error("id : " + name + " --> " + value)
                    }
                    project.logger.error("====== 共计" + ctFields.length + "个id ======")
                }
                innerClass.detach()
            }
            /**
             * 如果一个 CtClass 对象通过 writeFile(), toClass(), toBytecode() 被转换成一个类文件，
             * 此 CtClass 对象会被冻结起来，不允许再修改。因为一个类只能被 JVM 加载一次。
             */
            idClass.writeFile(path)
            project.logger.error("====== 生成 com.viewject.bindview.Id.class 文件 ======")
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            // 从ClassPool中释放，避免内存溢出
            if (rCtClass != null)
                rCtClass.detach()
            if (idClass != null)
                idClass.detach()
        }
    }

}

