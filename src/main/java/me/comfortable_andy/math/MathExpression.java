package me.comfortable_andy.math;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represents a mathematical expression, e.g. 2 + 2 * 9^2 / cos(0)
 *
 * @author AndyNoob
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
@RequiredArgsConstructor
@ToString
@Accessors(chain = true)
public class MathExpression {

    @Getter
    private final List<Part> parts;
    private final Map<String, Double> variables;

    public MathExpression(List<Part> parts) {
        this.parts = parts;
        this.variables = new ConcurrentHashMap<>();
    }

    public double evaluate() {
        /*
         keep order of operation in mind
         1. parenthesis
         2. exponents
         3. multiplication & division
         4. addition & subtraction
        */

        // first ensure list is valid
        if (!isValid()) throw new IllegalStateException("Invalid expression");

        // then evaluate all evaluable parts

        final List<Double> evaluated = new ArrayList<>();
        // the size of this list should be 1 less than the evaluated list
        final List<Part.Operator> operators = new ArrayList<>();

        for (Part part : this.parts) {
            if (part instanceof Part.Evaluable) {
                evaluated.add(((Part.Evaluable) part).evaluate(this.variables));
            } else {
                operators.add((Part.Operator) part);
            }
        }

        for (Part.Operator.OperatorType type : Part.Operator.OperatorType.values()) {
            // the constants in the operator enum are ordered by their precedence in
            // order of operation
            pass(type, evaluated, operators);
        }

        return evaluated.get(0);
    }

    private void pass(Part.Operator.OperatorType type, List<Double> evaluated, List<Part.Operator> operators) {
        for (int i = 0; i < operators.size(); i++) {
            if (operators.get(i).operator() != type) continue;

            final double left = evaluated.get(i);
            final double right = evaluated.get(i + 1);
            final double result = operators.get(i)
                    .operator()
                    .getCombiner()
                    .apply(left, right);

            // collapse the 2 corresponding values into one
            evaluated.set(i, result);
            evaluated.remove(i + 1);

            // remove operator and decrement 'i',
            // so that 'i' is the same in the next iteration
            operators.remove(i--);
        }
    }

    public boolean isValid() {
        boolean shouldHaveEvaluable = true;

        for (Part part : this.parts) {
            if (part instanceof Part.Evaluable) {
                if (!shouldHaveEvaluable) return false;
            } else if (shouldHaveEvaluable) return false;
            shouldHaveEvaluable = !shouldHaveEvaluable;
        }

        return true;
    }

    public MathExpression setVariable(String name, double val) {
        this.variables.put(name, val);
        return this;
    }

    public Double removeVariable(String name) {
        return this.variables.remove(name);
    }

    public static MathExpression parse(String expression) {
        return parse(expression, new ConcurrentHashMap<>());
    }

    public static MathExpression parse(String expression, Map<String, Double> variables) {
        final String operators = Arrays.stream(Part.Operator.OperatorType.values())
                .map(Part.Operator.OperatorType::getSymbol)
                .map(chara -> "\\" + chara)
                .collect(Collectors.joining());
        final Pattern pattern = Pattern.compile("([A-Za-z]+)|(\\(.+\\))|([" + operators + "])|(-?\\d+(?>\\.\\d+|\\.)?)");
        final Matcher matcher = pattern.matcher(expression);

        final List<Part> parts = new ArrayList<>();

        Part last = null;
        String onHold = null;

        while (matcher.find()) {
            final String group = matcher.group();

            if (group == null) continue;

            final Part part;

            final Double number = tryParse(group);

            if (number != null)
                part = new Part.Number(number);
            else if (group.startsWith("(")) {
                // is a parenthesis
                // could be a standalone,
                // or connected to a function, if there's a name on hold
                // which means if an input wants to multiply a variable by a parenthesis
                // there must be an operator in between, or else it would be treated as
                // a function.
                final String parenthesisStr = group.substring(1, group.length() - 1);
                final MathExpression inner = MathExpression.parse(parenthesisStr, variables);

                final Part.Parenthesis parenthesis = new Part.Parenthesis(inner);
                if (onHold == null) {
                    part = parenthesis;
                    if (last instanceof Part.Evaluable)
                        parts.add(new Part.Operator(Part.Operator.OperatorType.MULTIPLY));
                }
                else {
                    part = new Part.Function(onHold, parenthesis);
                    onHold = null;
                }
            } else if (group.length() == 1 && operators.contains(group))
                part = new Part.Operator(Part.Operator.OperatorType.valueOfSymbol(group.charAt(0)));
            else {
                // is a name
                // could be a variable or a function name
                if (onHold != null) {
                    // relaxing a bit here and say 2 names separated by blanks means multiplication
                    parts.add(new Part.Variable(onHold));
                    parts.add(new Part.Operator(Part.Operator.OperatorType.MULTIPLY));
                    parts.add(new Part.Variable(group));
                    onHold = null;
                } else {
                    if (last instanceof Part.Number) {
                        // something like "2a"
                        parts.add(new Part.Operator(Part.Operator.OperatorType.MULTIPLY));
                        parts.add(new Part.Variable(group)
                        );
                    } else onHold = group;
                }
                continue;
            }

            if (onHold != null) {
                // part could be a number or operator
                if (part instanceof Part.Number)
                    throw new IllegalStateException("Cannot have variable directly before a number at index " + matcher.start() + " (" + group + ")");

                parts.add(new Part.Variable(onHold));
                onHold = null;
            }

            if (last == null) {
                parts.add(part);
                last = part;
            } else {
                if (last.validNextParts().contains(part.getClass())) {
                    parts.add(part);
                    last = part;
                } else
                    throw new IllegalStateException("Unexpected token at index " + matcher.start() + ", expecting " + last.validNextParts().stream().map(Class::getSimpleName).collect(Collectors.joining(" or ")) + " (" + group + ")");
            }
        }

        if (onHold != null) parts.add(last = new Part.Variable(onHold));
        if (last != null && !last.isComplete())
            throw new IllegalStateException("Incomplete expression, ended with " + last);

        return new MathExpression(parts, variables);
    }

    private static Double tryParse(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public interface Part {

        interface Evaluable {

            double evaluate(Map<String, Double> variables);

        }

        List<Class<? extends Part>> validNextParts();

        boolean isComplete();

        record Number(double number) implements Part, Evaluable {

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return List.of(Operator.class, Parenthesis.class);
            }

            @Override
            public boolean isComplete() {
                return true;
            }

            @Override
            public double evaluate(Map<String, Double> variables) {
                return this.number;
            }
        }

        record Operator(OperatorType operator) implements Part {

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return List.of(Number.class, Parenthesis.class);
            }

            @Override
            public boolean isComplete() {
                return false;
            }

            @Getter
            @RequiredArgsConstructor
            enum OperatorType {
                // these are in order of operation
                POWER('^', Math::pow),
                MULTIPLY('*', (a, b) -> a * b),
                DIVIDE('/', (a, b) -> a / b),
                ADD('+', Double::sum),
                SUBTRACT('-', (a, b) -> a - b),
                ;

                private final char symbol;
                private final BiFunction<Double, Double, Double> combiner;

                public static OperatorType valueOfSymbol(char c) {
                    for (OperatorType type : values()) {
                        if (type.symbol == c) return type;
                    }
                    throw new IllegalArgumentException("Could not find operator for symbol " + c);
                }
            }
        }

        record Parenthesis(MathExpression expression) implements Part, Evaluable {

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return List.of(Operator.class, Parenthesis.class);
            }

            @Override
            public boolean isComplete() {
                return true;
            }

            @Override
            public double evaluate(Map<String, Double> variables) {
                return this.expression.evaluate();
            }
        }

        record Function(String name, Parenthesis parenthesis) implements Part, Evaluable {

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return List.of(Operator.class);
            }

            @Override
            public boolean isComplete() {
                return true;
            }

            @Override
            public double evaluate(Map<String, Double> variables) {
                final double evaluatedParenthesis = this.parenthesis().evaluate(variables);
                try {
                    final Method mathMethod = Math.class.getMethod(this.name, double.class);
                    return (double) mathMethod.invoke(null, evaluatedParenthesis);
                } catch (ReflectiveOperationException e) {
                    return this.parenthesis().evaluate(variables);
                }
            }
        }

        record Variable(String name) implements Part, Evaluable {

            private static final Map<String, Double> BUILT_IN = new ConcurrentHashMap<>();

            static {
                Arrays.stream(Math.class.getFields()).filter(field -> Modifier.isPublic(field.getModifiers())).forEach(field -> {
                    try {
                        BUILT_IN.put(field.getName().toLowerCase(), (Double) field.get(null));
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Could not retrieve the field " + field + " from " + Math.class.getName(), e);
                    }
                });
            }

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return List.of(Operator.class);
            }

            @Override
            public boolean isComplete() {
                return true;
            }

            @Override
            public double evaluate(Map<String, Double> variables) {
                final Double val = variables.containsKey(this.name) ? variables.get(this.name) : BUILT_IN.get(this.name.toLowerCase());
                if (val == null)
                    throw new IllegalStateException("Variable " + this.name + " not found in " + variables + " or built in variables " + BUILT_IN);
                return val;
            }

        }

    }

}
