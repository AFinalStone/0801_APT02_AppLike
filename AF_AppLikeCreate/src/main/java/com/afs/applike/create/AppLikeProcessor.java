package com.afs.applike.create;

import com.afs.applike.annotation.AppLikeLifeCycle;

import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;


//核心的注解处理类，在这里我们可以扫描源代码里所有的注解，找到我们需要的注解，然后做出相应处理
public class AppLikeProcessor extends AbstractProcessor {
    private Messager mMessager;
    private Elements mElementUtils;
    private Map<String, AppLikeProxyClassCreator> mMap = new HashMap<>();

    //支持的版本
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    //2.能用来处理那些注解
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> set = new LinkedHashSet<>();
        //返回注解类的全限定类名，我们这里要识别的注解类是 AppLifeCycle
        set.add(AppLikeLifeCycle.class.getCanonicalName());
        return set;
    }

    //3.定义一个用来生成APT目录下文件的对象
    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mElementUtils = processingEnvironment.getElementUtils();
        mMessager = processingEnv.getMessager();
    }


    //所有逻辑都在这里完成
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        mMessager.printMessage(Diagnostic.Kind.NOTE, "processing...");
        if (set.isEmpty()) {
            return false;
        }
        //这里返回所有使用了 AppLifeCycle 注解的元素
        Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(AppLikeLifeCycle.class);
        mMap.clear();
        //遍历所有使用了该注解的元素
        for (Element element : elements) {
            //如果该注解不是用在类上面，直接抛出异常，该注解用在方法、字段等上面，我们是不支持的
            if (!element.getKind().isClass()) {
                throw new RuntimeException("Annotation AppLifeCycle can only be used in class.");
            }
            //强制转换为TypeElement，也就是类元素，可以获取使用该注解的类的相关信息
            TypeElement typeElement = (TypeElement) element;

            //这里检查一下，使用了该注解的类，同时必须要实现com.hm.lifecycle.api.IAppLike接口，否则会报错，因为我们要实现一个代理类
            List<? extends TypeMirror> mirrorList = typeElement.getInterfaces();
            if (mirrorList.isEmpty()) {
                throw new RuntimeException(typeElement.getQualifiedName() + " must implements interface com.afs.applike.lifecycle.IAppLike");
            }
            boolean checkInterfaceFlag = false;
            for (TypeMirror mirror : mirrorList) {
                if ("com.afs.applike.lifecycle.IAppLike".equals(mirror.toString())) {
                    checkInterfaceFlag = true;
                }
            }
            if (!checkInterfaceFlag) {
                throw new RuntimeException(typeElement.getQualifiedName() + " must implements interface com.afs.applike.lifecycle.IAppLike");
            }

            //该类的全限定类名
            String fullClassName = typeElement.getQualifiedName().toString();
            if (!mMap.containsKey(fullClassName)) {
                mMessager.printMessage(Diagnostic.Kind.NOTE, "process class name : " + fullClassName);
                //创建代理类生成器
                AppLikeProxyClassCreator creator = new AppLikeProxyClassCreator(mElementUtils, typeElement);
                mMap.put(fullClassName, creator);
            }
        }
        mMessager.printMessage(Diagnostic.Kind.NOTE, "start to generate proxy class code");
        for (Map.Entry<String, AppLikeProxyClassCreator> entry : mMap.entrySet()) {
            String className = entry.getKey();
            AppLikeProxyClassCreator creator = entry.getValue();
            mMessager.printMessage(Diagnostic.Kind.NOTE, "generate proxy class for " + className);

            //生成代理类，并写入到文件里，生成逻辑都在AppLikeProxyClassCreator里实现
            try {
                JavaFileObject jfo = processingEnv.getFiler().createSourceFile(creator.getProxyClassFullName());
                Writer writer = jfo.openWriter();
                writer.write(creator.generateJavaCode());
                writer.flush();
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return true;
    }
}