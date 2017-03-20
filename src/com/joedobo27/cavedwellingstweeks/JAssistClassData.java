package com.joedobo27.cavedwellingstweeks;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.logging.Logger;

@SuppressWarnings("unused")
class JAssistClassData {

    private CtClass ctClass;
    private ClassFile classFile;
    private ConstPool constPool;
    private static HashMap<String, JAssistClassData> clazz;
    private static Logger logger = Logger.getLogger(CaveDwellingsTweaksMod.class.getName());

    JAssistClassData(String classPath, ClassPool classPool) throws NotFoundException {
        ctClass = classPool.get(classPath);
        classFile = ctClass.getClassFile();
        constPool = classFile.getConstPool();
        if (clazz == null)
            initClassInstances();
        clazz.put(ctClass.getSimpleName(), this);
    }

    private static void initClassInstances(){
        clazz = new HashMap<>();
    }

    static JAssistClassData getClazz(String name) {
        if (clazz == null)
            initClassInstances();
        return clazz.get(name);
    }

    static void voidClazz() {
        clazz.clear();
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
