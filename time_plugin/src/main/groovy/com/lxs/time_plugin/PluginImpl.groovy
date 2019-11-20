package com.lxs.time_plugin

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.compress.utils.IOUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

public class PluginImpl extends Transform implements Plugin<Project> {

    @Override
    void apply(Project project) {
        //获取build.gradle中的android闭包
        def android = project.extensions.findByType(AppExtension)
        android.registerTransform(this)
    }

    @Override
    String getName() {
        //task的名字
        return "DavisPlugin"
    }

    //过滤的数据类型
    //自定义plugin只支持classes和resources
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    //指定作用域
    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return true
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        println 'DavisPlugin start'
        def startTime = System.currentTimeMillis()
        //消费型输入、引用型输入只能读
        Collection<TransformInput> inputs = transformInvocation.inputs
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider()
        def incremental = transformInvocation.incremental

        if (!incremental) {
            outputProvider.deleteAll()
        }

        inputs.each {
            //遍历文件
            it.directoryInputs.each { directoryInput ->

                if (incremental) {
                    transformIncremental(directoryInput, outputProvider)
                } else {
                    //进入两次是因为java和kotlin是分开的
                    //不是增量
                    transformUnIncremental(directoryInput, outputProvider)
                }

            }
            //遍历jar
            it.jarInputs.each { jarInput ->
                //记录待修改原始文件
                handlerJarInput(incremental, jarInput, outputProvider)
            }
        }

        def cost = (System.currentTimeMillis() - startTime) / 1000
        println '--------------- DavisPlugin visit end --------------- '
        println "DavisPlugin cost ： $cost s"
    }

    static void transformIncremental(DirectoryInput directoryInput, TransformOutputProvider outputProvider) {
        directoryInput.changedFiles.each {
            File dest = outputProvider.getContentLocation(directoryInput.name,
                    directoryInput.getContentTypes(), directoryInput.getScopes(),
                    Format.DIRECTORY)
            String srcDirPath = directoryInput.getFile().getAbsolutePath()
            String destDirPath = dest.getAbsolutePath()
            Status status = it.getValue()
            File inputFile = it.key
            //改变目标路径到插件下面/替换绝对路径这样地址就是插件的
            String destFilePath = inputFile.getAbsolutePath().replace(srcDirPath, destDirPath)
            File destFile = new File(destFilePath)
            switch (status) {
                case Status.NOTCHANGED:
                    //没有改变
                    break
                case Status.ADDED:
                case Status.CHANGED:
                    println("改变了")
                    //改变了
                    changeFile(inputFile)
                    FileUtils.copyFile(inputFile, destFile)
                    break
                case Status.REMOVED:
                    println("删除")
                    //移除对应文件
                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    break
            }
        }
    }

    static void transformUnIncremental(DirectoryInput directoryInput, TransformOutputProvider outputProvider) {
        directoryInput.file.eachFileRecurse { file ->
            changeFile(file)
        }
        File dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.getContentTypes(), directoryInput.getScopes(), Format.DIRECTORY)
        FileUtils.copyDirectory(directoryInput.getFile(), dest)
    }

    /**
     * asm插入代码
     * @param file
     */
    static void changeFile(File file) {
        def name = file.name
        if (name.endsWith(".class") && !name.startsWith("R\$")
                && "R.class" != name && "BuildConfig.class" != name) {
            ClassReader classReader = new ClassReader(file.bytes)
            //COMPUTE_MAXS自动计算所有内容
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
            ClassVisitor classVisitor = new LifecycleClassVisitor(classWriter)
            //EXPAND_FRAMES栈图以扩展格式进行访问
            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
            byte[] code = classWriter.toByteArray()
            //覆盖文件
            FileOutputStream fileOutputStream = new FileOutputStream(file.parentFile.absolutePath + File.separator + name)
            fileOutputStream.write(code)
            fileOutputStream.close()
        }
    }

    static void handlerJarInput(Boolean incremental, JarInput jarInput, TransformOutputProvider outputProvider) {
        if (jarInput.file.getAbsolutePath().endsWith(".jar")) {
            File src = jarInput.file
            def jarName = jarInput.name
            //防止重名
            def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
            println(md5Name)
            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4)
            }

            File dest = outputProvider.getContentLocation(jarName + "_" + md5Name, jarInput.contentTypes, jarInput.scopes, Format.JAR)

            if (incremental) {
                Status status = jarInput.getStatus()
                switch (status) {
                    case Status.NOTCHANGED:
                        println("jar没有改变")
                        break
                    case Status.ADDED:
                    case Status.CHANGED:
                        println("jar改变了" + src.absolutePath)
                        transformJar(src, dest)
                        break
                    case Status.REMOVED:
                        if (dest.exists()) {
                            dest.delete()
                        }
                        break
                }
            } else {
                println("不是增量编译")
                transformJar(src, dest)
            }
        }
    }

    static void transformJar(File jarFile, File dest) {
        if (jarFile) {
            def file = new JarFile(jarFile)
            Enumeration enumeration = file.entries()
            File tmpFile = new File(jarFile.getParent() + File.separator + "classes_temp.jar")
            //避免上次的缓存被重复插入
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile))
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                def entryName = jarEntry.name
                ZipEntry zipEntry = new ZipEntry(entryName)
                InputStream inputStream = file.getInputStream(jarEntry)
                jarOutputStream.putNextEntry(zipEntry)
                if (entryName.endsWith(".class") && !entryName.startsWith("R\$")
                        && "R.class" != entryName && "BuildConfig.class" != entryName) {
                    def code = changeInputStream(inputStream)
                    //覆盖文件
                    jarOutputStream.write(code)
                } else {
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                }
                inputStream.close()
                jarOutputStream.closeEntry()
            }
            jarOutputStream.close()
            file.close()
            FileUtils.copyFile(tmpFile, dest)
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
        }
    }

    /**
     * asm插入代码
     * @param inputStream
     * @return
     */
    static byte[] changeInputStream(InputStream inputStream) {
        println("开始插装")
        ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream))
        //COMPUTE_MAXS自动计算所有内容
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
        ClassVisitor classVisitor = new LifecycleClassVisitor(classWriter)
        //EXPAND_FRAMES栈图以扩展格式进行访问
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
        println("插装完成")
        return classWriter.toByteArray()
    }
}