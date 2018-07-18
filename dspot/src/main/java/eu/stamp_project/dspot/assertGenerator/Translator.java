package eu.stamp_project.dspot.assertGenerator;

import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 17/07/18
 *
 * This class aims at translating string to spoon node.
 * In fact, values provided by the observations are strings.
 * Here, we want to keep the semantic of the instruction
 *
 */
public class Translator {

    private final Factory factory;

    public Translator(Factory factory) {
        this.factory = factory;
    }

    /**
     * Translate the given string into a spoon node.
     * @param stringToBeTranslated
     * @return a spoon represented by the given String. This node is either a CtInvocation, either a VariableRead.
     */
    public CtExpression<?> translate(String stringToBeTranslated) {
        if (!stringToBeTranslated.contains("()")) { // this is not an invocation, it is a invocation.
            final CtVariableReference<?> variable = factory.createLocalVariableReference();
            variable.setSimpleName(stringToBeTranslated);
            return factory.createVariableRead(variable, false);
        } else {
            return buildInvocationFromString(stringToBeTranslated);
        }
    }

    public CtInvocation<?> buildInvocationFromString(String invocationAsString) {
        return this.buildInvocationFromString(invocationAsString, null);
    }

    private CtInvocation buildInvocationFromString(String invocationAsString, CtInvocation<?> subInvocation) {
        CtInvocation invocation = factory.createInvocation();
        // invocations are the form of ((TypeCast)o).getX()
        int end = invocationAsString.indexOf("()");
        int start = findMatchingIndex(invocationAsString, '.', end);
        final CtExecutableReference<?> executableReference = factory.createExecutableReference();
        executableReference.setSimpleName(invocationAsString.substring(start + 1, end));
        invocation.setExecutable(executableReference);
        if (subInvocation == null) {
            end = start - 1; // i.e. the closing parenthesis
            start = findMatchingIndex(invocationAsString, ')', end);
            final CtLocalVariableReference<?> localVariableReference = factory.createLocalVariableReference();
            localVariableReference.setSimpleName(invocationAsString.substring(start + 1, end));
            final CtVariableAccess<?> variableRead = factory.createVariableRead(localVariableReference, false);
            invocation.setTarget(variableRead);
            end = start;
        } else {
            invocation.setTarget(subInvocation);
            end = start - 1;
        }
        start = findMatchingIndex(invocationAsString, '(', end);
        final CtType<?> ctType = factory.Type().get(invocationAsString.substring(start + 1, end));
        final CtTypeReference<?> reference = ctType.getReference();
        invocation.getTarget().addTypeCast(reference);
        if (start != 1) {
            final String substringToBeRemove = invocationAsString.substring(start - 2, invocationAsString.indexOf("()") + 2);
            invocationAsString = invocationAsString.replace(substringToBeRemove, "");
            return buildInvocationFromString(invocationAsString, invocation);
        } else {
            return invocation;
        }
    }

    private int findMatchingIndex(String stringToBeMatched, char charToBeMatched, int start) {
        while (stringToBeMatched.charAt(--start) != charToBeMatched) ;
        return start;
    }

}
