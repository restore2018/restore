package hk.polyu.comp.jaid.assertagent;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class AssertTransformer implements ClassFileTransformer {
    ByteCodeOperator byteCodeOperator = new ByteCodeOperator();

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className.contains("junit") && className.contains("Assert")) {
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
            try {
                CtClass cc = poolParent.makeClass(new java.io.ByteArrayInputStream(b));

                for (CtMethod ctMethod : cc.getMethods()) {
                    if (ctMethod.getName().contains("assert")) {
                        ctMethod.insertBefore("{hk.polyu.comp.jaid.tester.Tester.assertInvoked(\""+ctMethod.getLongName()+"\");}");
                    }
                }
                b = cc.toBytecode();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (CannotCompileException e) {
                e.printStackTrace();
            }
            return b;
        }
    }

}
