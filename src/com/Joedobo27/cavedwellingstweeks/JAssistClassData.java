package com.Joedobo27.cavedwellingstweeks;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

@SuppressWarnings("unused")
class JAssistClassData {

    private CtClass ctClass;
    private ClassFile classFile;
    private ConstPool constPool;

    JAssistClassData(String classPath, ClassPool classPool) throws NotFoundException {
        ctClass = classPool.get(classPath);
        classFile = ctClass.getClassFile();
        constPool = classFile.getConstPool();
    }

    CtClass getCtClass() {
        return ctClass;
    }

    ClassFile getClassFile() {
        return classFile;
    }

    ConstPool getConstPool() {
        return constPool;
    }

    public void constantPoolPrint(String destinationPath) throws FileNotFoundException {
        Path printPath = Paths.get(destinationPath);
        PrintWriter out = new PrintWriter(printPath.toFile());
        constPool.print(out);
        out.close();
    }
}
