package com.lxs.time_plugin;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * @author liuxiaoshuai
 * @date 2019-11-18
 * @desc
 * @email liulingfeng@mistong.com
 */
public class LifecycleClassVisitor extends ClassVisitor implements Opcodes {
    private String className;
    public LifecycleClassVisitor(ClassVisitor cv) {
        super(Opcodes.ASM5, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.className = name;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        System.out.println("方法:" + name + "签名:" + desc + "访问标志:" + access);
        MethodVisitor mv = super.visitMethod(access, name, desc, signature,
                exceptions);
        if ("<init>".equals(name)) {
            //构造方法不处理
            return mv;
        }

        return new MethodAdapterVisitor(api, mv, access, name, desc,className);
    }
}
