package fr.inria.diversify.dspot.amplifier;

import fr.inria.diversify.dspot.amplifier.value.ValueCreator;
import fr.inria.diversify.utils.AmplificationHelper;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.stream.Collectors;

public class ReplacementAmplifier implements Amplifier {

    @Override
    public List<CtMethod> apply(CtMethod testMethod) {
        return testMethod.getElements(new TypeFilter<CtLocalVariable>(CtLocalVariable.class) {
            @Override
            public boolean matches(CtLocalVariable element) {
                return !element.getSimpleName().contains("DSPOT");
            }
        }).stream()
                .map(ctLocalVariable -> {
                    final CtMethod clone = testMethod.clone();
                    final CtLocalVariable localVariable = clone.getElements(new TypeFilter<>(CtLocalVariable.class))
                            .stream()
                            .filter(ctLocalVariable1 -> ctLocalVariable1.equals(ctLocalVariable))
                            .findFirst()
                            .get();
                    CtExpression<?> ctExpression = ValueCreator.generateRandomValue(ctLocalVariable.getType(), localVariable.getAssignment());
                    localVariable.setAssignment(ctExpression);
                    return clone;
                }).collect(Collectors.toList());
    }

    @Override
    public CtMethod applyRandom(CtMethod testMethod) {
        return null;
    }

    @Override
    public void reset(CtType testClass) {
        AmplificationHelper.reset();
    }
}
