package fr.inria.diversify.dspot;

import fr.inria.diversify.util.Log;
import org.junit.BeforeClass;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.SpoonClassNotFoundException;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 */

public class AmplificationChecker {

    public static boolean isCase(CtLiteral literal) {
        return literal.getParent(CtCase.class) != null;
    }

    public static boolean isAssert(CtStatement stmt) {
        return stmt instanceof CtInvocation && isAssert((CtInvocation) stmt);
    }

    public static boolean isAssert(CtInvocation invocation) {
        try {
            Class cl = invocation.getExecutable().getDeclaringType().getActualClass();
            String mthName = invocation.getExecutable().getSimpleName();
            return (mthName.startsWith("assert") || mthName.contains("fail"))
                    || isAssertInstance(cl);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAssertInstance(Class cl) {
        if (cl.equals(org.junit.Assert.class) || cl.equals(junit.framework.Assert.class))
            return true;
        Class superCl = cl.getSuperclass();
        return superCl != null && isAssertInstance(superCl);
    }

    public static boolean canBeAdded(CtInvocation invocation) {
        return !invocation.toString().startsWith("super(") && invocation.getParent() instanceof CtBlock;
    }

    public static boolean isArray(CtTypeReference type) {
        return type.toString().contains("[]");
    }

    public static boolean isPrimitive(CtTypeReference type) {
        try {
            return type.unbox().isPrimitive();
        } catch (SpoonClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isInAssert(CtLiteral lit) {
        return lit.getParent(CtInvocation.class) != null &&
                AmplificationChecker.isAssert(lit.getParent(CtInvocation.class));
    }

    public static boolean isTest(CtMethod<?> candidate) {
        CtClass<?> parent = candidate.getParent(CtClass.class);
        if (candidate.getAnnotation(org.junit.Ignore.class) != null) {
            return false;
        }
        if (candidate.isImplicit()
                || candidate.getVisibility() == null
                || !candidate.getVisibility().equals(ModifierKind.PUBLIC)
                || candidate.getBody() == null
                || candidate.getBody().getStatements().size() == 0) {
            return false;
        }
        return candidate.getParameters().isEmpty() &&
                (candidate.getAnnotation(org.junit.Test.class) != null ||
                ((candidate.getSimpleName().contains("test") ||
                candidate.getSimpleName().contains("should")) && !isTestJUnit4(parent)));
    }

    private static boolean isTestJUnit4(CtClass<?> classTest) {
        return classTest.getMethods().stream()
                .anyMatch(ctMethod ->
                        ctMethod.getAnnotation(org.junit.Test.class) != null
                );
    }

    public static boolean isTest(CtMethod candidate, String relativePath) {
        try {
            if (!relativePath.isEmpty() && candidate.getPosition() != null
                    && candidate.getPosition().getFile() != null
                    && !candidate.getPosition().getFile().toString().contains(relativePath)) {
                return false;
            }
        } catch (Exception e) {
            Log.warn("Error during check of position of " + candidate.getSimpleName());
            return false;
        }
        return isTest(candidate);
    }

    //TODO we will use a Name Convention, i.e. contains Mock on Annotation
    private static final TypeFilter<CtAnnotation> mockedAnnotationFilter = new TypeFilter<CtAnnotation>(CtAnnotation.class) {
        @Override
        public boolean matches(CtAnnotation element) {
            return element.toString().contains("Mock");
        }
    };

    //TODO it might not be the best way to do
    private static final Predicate<CtType<?>> gotReferencesToMockito = (ctType ->
            ctType.getElements(new TypeFilter<>(CtTypeReference.class))
                    .stream()
                    .anyMatch(ctTypeReference ->
                            ctTypeReference.getPackage() != null &&
                                    ctTypeReference.getPackage().getSimpleName().contains("mock"))
    );

    public static boolean isMocked(CtType<?> test) {
        return gotReferencesToMockito.test(test) ||
                !test.getElements(mockedAnnotationFilter).isEmpty();
    }
}
