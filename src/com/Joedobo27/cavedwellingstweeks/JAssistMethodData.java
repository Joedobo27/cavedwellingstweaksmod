package com.Joedobo27.cavedwellingstweeks;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.MethodInfo;

import java.util.Arrays;
import java.util.Objects;

@SuppressWarnings("unused")
class JAssistMethodData {

    private JAssistClassData parentClass;
    private MethodInfo methodInfo;
    private CodeAttribute codeAttribute;
    private CodeIterator codeIterator;
    private CtMethod ctMethod;


    JAssistMethodData(JAssistClassData jAssistClassData, String descriptor, String methodName) throws NullPointerException {
        parentClass = jAssistClassData;
        methodInfo = Arrays.stream(jAssistClassData.getClassFile().getMethods().toArray())
                .map((Object value) -> (MethodInfo) value)
                .filter((MethodInfo value) -> Objects.equals(value.getDescriptor(), descriptor))
                .filter((MethodInfo value) -> Objects.equals(value.getName(), methodName))
                .findFirst()
                .orElse(null);
        if (methodInfo == null)
            throw new NullPointerException();
        codeAttribute = methodInfo.getCodeAttribute();
        codeIterator = codeAttribute.iterator();
        setCtMethod(descriptor, methodName);
    }

    JAssistMethodData(@NotNull JAssistClassData jAssistClassData, int modifiers, @Nullable CtClass returnType, String mname, @Nullable CtClass[] parameters,
                      CtClass[] exceptions, String bodySource, @NotNull CtClass declaring) throws CannotCompileException {
        CtMethod ctMethod = CtNewMethod.make( modifiers, returnType, mname, parameters, exceptions, bodySource, declaring);
        jAssistClassData.getCtClass().addMethod(ctMethod);
        parentClass = jAssistClassData;
        methodInfo = Arrays.stream(jAssistClassData.getClassFile().getMethods().toArray())
                .map((Object value) -> (MethodInfo) value)
                .filter((MethodInfo value) -> Objects.equals(value.getDescriptor(), ctMethod.getSignature()))
                .filter((MethodInfo value) -> Objects.equals(value.getName(), ctMethod.getName()))
                .findFirst()
                .orElse(null);
        if (methodInfo == null)
            throw new NullPointerException();
        codeAttribute = methodInfo.getCodeAttribute();
        codeIterator = codeAttribute.iterator();

    }

    private void setCtMethod(String descriptor, String methodName) {
        boolean isPrivate = (methodInfo.getAccessFlags() & (AccessFlag.PRIVATE)) != 0;
        if (isPrivate){
            ctMethod = Arrays.stream(parentClass.getCtClass().getDeclaredMethods())
                    .filter((CtMethod value) -> Objects.equals(value.getSignature(), descriptor))
                    .filter((CtMethod value) -> Objects.equals(value.getName(), methodName))
                    .findFirst()
                    .orElse(null);
        }
        else {
            ctMethod = Arrays.stream(parentClass.getCtClass().getMethods())
                    .filter((CtMethod value) -> Objects.equals(value.getSignature(), descriptor))
                    .filter((CtMethod value) -> Objects.equals(value.getName(), methodName))
                    .findFirst()
                    .orElse(null);
        }
    }

    MethodInfo getMethodInfo() {
        return methodInfo;
    }

    CodeAttribute getCodeAttribute() {
        return codeAttribute;
    }

    CodeIterator getCodeIterator() {
        return codeIterator;
    }

    JAssistClassData getParentClass() {
        return parentClass;
    }

    CtMethod getCtMethod() {
        return ctMethod;
    }

    /**
     * This method is used to check if the JVM code matches a hash. It's primary purpose is to detected changes in WU
     * vanilla code. When Javassist is used to replace or insert there is nothing that informs a mod author
     * the code should be reviewed.
     *
     * @param ci type CodeIterator
     * @return a hash value.
     */
    static int byteCodeHashCheck(CodeIterator ci) {
        int length = ci.getCodeLength();
        int[] code = new int[length];
        for (int i=0;i<length;i++){
            code[i] = ci.byteAt(i);
        }
        return Arrays.hashCode(code);
    }

}
