package mdd.uniandes.generatedao.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;

import mdd.uniandes.generatedao.annotations.TableSerializable;

import java.io.IOException;
import java.io.Writer;
import java.util.*;


@SupportedAnnotationTypes(
        {"mdd.uniandes.generatedao.annotations.TableSerializable"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class AutoGenerateProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.size() == 0) {
            return false;
        }

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(TableSerializable.class);


        List<String> uniqueIdCheckList = new ArrayList<>();

        for (Element element : elements) {
        	TableSerializable autoImplement = element.getAnnotation(TableSerializable.class);

            if (element.getKind() != ElementKind.INTERFACE) {
                error("The annotation @TableSerializable can only be applied on interfaces: ",
                        element);

            } else {
                boolean error = false;

                if (uniqueIdCheckList.contains(autoImplement.name())) {
                    error("TableSerializale#as should be uniquely defined", element);
                    error = true;
                }

                error = !checkIdValidity(autoImplement.name(), element);

                if (!error) {
                    uniqueIdCheckList.add(autoImplement.name());
                    try {
                    	//Twig template
                    	generateDAOClassTwig(autoImplement, element);
                    	generateEntityClassTwig(autoImplement, element);
                    	//Java code
                    	//generateDAOClass(autoImplement, element);
                    	//generateEntityClass(autoImplement, element);
                    } catch (Exception e) {
                        error(e.getMessage(), null);
                    }
                }
            }
        }
        return false;
    }
    
    
    private void generateDAOClassTwig(TableSerializable autoImplement, Element element)throws Exception {
    	String pkg = getPackageName(element);
        //delegate some processing to our FieldInfo class
        FieldInfo fieldInfo = FieldInfo.get(element);
        //the target interface name
        String interfaceName = getTypeName(element);
        
        LinkedHashMap< String, String> fields=fieldInfo.getFields();
        
        JtwigTemplate template = JtwigTemplate.classpathTemplate("templates/databasemanager.twig");
        JtwigModel model = JtwigModel.newModel().with("className", autoImplement.name());
        model.with("package", pkg);
        
        
        String renderModel=template.render(model);
        generateClass(pkg + "." + autoImplement.name()+"DatabaseManager", renderModel);
    	
    }
    
    private void generateEntityClassTwig(TableSerializable autoImplement, Element element)throws Exception {
    	String pkg = getPackageName(element);
        //delegate some processing to our FieldInfo class
        FieldInfo fieldInfo = FieldInfo.get(element);
        //the target interface name
        String interfaceName = getTypeName(element);
        
        LinkedHashMap< String, String> fields=fieldInfo.getFields();
        
        JtwigTemplate template = JtwigTemplate.classpathTemplate("templates/entity.twig");
        JtwigModel model = JtwigModel.newModel().with("className", autoImplement.name());
        model.with("interfaceName", interfaceName);       
        model.with("fields", fields);
        model.with("package", pkg);
        
        
        String renderModel=template.render(model);
        generateClass(pkg + "." + autoImplement.name(), renderModel);
    	
    	
    }

    

    private String getPackageName(Element element) {
        List<PackageElement> packageElements =
                ElementFilter.packagesIn(Arrays.asList(element.getEnclosingElement()));

        Optional<PackageElement> packageElement = packageElements.stream().findAny();
        return packageElement.isPresent() ?
                packageElement.get().getQualifiedName().toString() : null;

    }

    private void generateClass(String qfn, String end) throws IOException {
        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(qfn);
        Writer writer = sourceFile.openWriter();
        writer.write(end);
        writer.close();
    }

    /**
     * Checking if the class to be generated is a valid java identifier
     * Also the name should be not same as the target interface
     */
    private boolean checkIdValidity(String name, Element e) {
        boolean valid = true;
        for (int i = 0; i < name.length(); i++) {
            if (i == 0 ? !Character.isJavaIdentifierStart(name.charAt(i)) :
                    !Character.isJavaIdentifierPart(name.charAt(i))) {
                error("TableSerializale#as should be valid java " +
                        "identifier for code generation: " + name, e);
                valid = false;
            }
        }
        if (name.equals(getTypeName(e))) {
            error("TableSerializale#as should be different than the Interface name ", e);
        }
        return valid;
    }

    /**
     * Get the simple name of the TypeMirror
     */
    private static String getTypeName(Element e) {
        TypeMirror typeMirror = e.asType();
        String[] split = typeMirror.toString().split("\\.");
        return split.length > 0 ? split[split.length - 1] : null;
    }

    private void error(String msg, Element e) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
    }
}
