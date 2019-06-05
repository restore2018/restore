package hk.polyu.comp.jaid.assertagent;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class TimeoutTransformer implements ClassFileTransformer {
    ByteCodeOperator byteCodeOperator = new ByteCodeOperator();

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if ((className.contains("introclassJava") || className.contains("java_testcases")) && className.toLowerCase().endsWith("test")) {
            return byteCodeOperator.transformClass(classfileBuffer);
        }
        return classfileBuffer;
    }

    class ByteCodeOperator {
        private ClassPool poolParent = null;

        ByteCodeOperator() {
            this.poolParent = ClassPool.getDefault();
        }

        byte[] transformClass(byte[] b) {
            CtClass cc = null;
            try {
                cc = poolParent.makeClass(new java.io.ByteArrayInputStream(b));

                for (CtMethod ctMethod : cc.getMethods()) {
//                    try {
                        if (ctMethod.hasAnnotation(org.junit.Test.class)) {
//                            System.out.println(ctMethod.getLongName());
//                            System.out.println(ctMethod.getAnnotation(org.junit.Test.class));
                            addAnnotation(cc, ctMethod, org.junit.Test.class.getName());
//                            System.out.println(ctMethod.getAnnotation(org.junit.Test.class));
                        }
//                    } catch (ClassNotFoundException ignored) {
//
//                    }
                }
                b = cc.toBytecode();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (CannotCompileException e) {
                e.printStackTrace();
            }

            return b;
        }

        public void addAnnotation(CtClass clazz, CtMethod ctMethod, String annotationName) {
            ClassFile cfile = clazz.getClassFile();
            ConstPool cpool = cfile.getConstPool();

            AnnotationsAttribute attr = new AnnotationsAttribute(cpool, AnnotationsAttribute.visibleTag);
            Annotation annot = new Annotation(annotationName, cpool);
            attr.addAnnotation(annot);
            ctMethod.getMethodInfo().addAttribute(attr);
        }
    }

}
