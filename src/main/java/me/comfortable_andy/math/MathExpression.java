package me.comfortable_andy.math;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import me.comfortable_andy.math.MathExpression.Part.*;
import me.comfortable_andy.math.MathExpression.Part.Operator.OperatorType;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
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
        if (this.parts.isEmpty()) throw new IllegalStateException("Empty parts list");

        // then evaluate all evaluable parts

        final List<Double> evaluated = new ArrayList<>();
        // the size of this list should be 1 less than the evaluated list
        final List<List<Operator>> operators = new ArrayList<>();

        int index = 0;

        for (Part part : this.parts) {
            if (part instanceof Part.Evaluable) {
                evaluated.add(((Part.Evaluable) part).evaluate(this.variables));
                index++;
            } else {
                while (index >= operators.size())
                    operators.add(new ArrayList<>());

                final List<Operator> current = operators.get(index);
                final Operator adding = (Operator) part;
                if (index == 0 && current.isEmpty() && adding.operator.modifier == null)
                    throw new IllegalStateException("Cannot start expression with " + adding);
                if (!current.isEmpty() && adding.operator.modifier == null)
                    throw new IllegalStateException("Duplicate operators");
                current.add(adding);
            }
        }

        if (evaluated.size() <= 1) {
            double val = evaluated.get(0);
            final List<Operator> list;
            if (!operators.isEmpty() && (list = operators.get(0)).size() > 1)
                val = applyModifiers(val, list.subList(1, list.size()));
            return val;
        }

        for (EnumSet<OperatorType> types : OperatorType.getByOrder()) {
            // the constants in the operator enum are ordered by their precedence in
            // order of operation
            pass(types, evaluated, operators);
        }

        return evaluated.get(0);
    }

    private void pass(EnumSet<OperatorType> types, List<Double> evaluated, List<List<Operator>> operators) {
        if (evaluated.size() <= 1) return;
        for (int i = 0; i < evaluated.size() - 1; i++) {
            if (i != 0 && i >= operators.size())
                throw new IllegalStateException("Missing operator at the " + (i + 1) + " token.");

            final List<Operator> currentOperators = operators.get(i);
            final List<Operator> nextOperators = operators.get(i + 1);
            final OperatorType combiningOperator = nextOperators.get(0).operator();

            if (!types.contains(combiningOperator))
                continue;

            double left = evaluated.get(i);
            if (!currentOperators.isEmpty())
                left = applyModifiers(left, currentOperators.subList(i == 0 ? 0 : (currentOperators.size() == 1 ? currentOperators.size() : 1), currentOperators.size()));

            double right = evaluated.get(i + 1);
            if (nextOperators.size() > 1)
                right = applyModifiers(right, nextOperators.subList(1, nextOperators.size()));

            final double result = combiningOperator
                    .getCombiner()
                    .apply(left, right);

            // collapse the 2 corresponding values into one
            evaluated.set(i, result);
            evaluated.remove(i + 1);

            // remove operators and decrement 'i',
            // so that 'i' is the same in the next iteration
            operators.remove(i + 1);
            i--;

            // remove modifying operators
            if (!currentOperators.isEmpty()) {
                final Operator temp = currentOperators.get(0);
                currentOperators.clear();
                currentOperators.add(temp);
            }
        }
    }

    private double applyModifiers(double val, List<Operator> operators) {
        return val * operators.stream().mapToInt(operator -> operator.operator.modifier).reduce(1, (a, b) -> a * b);
    }

    public boolean isValid() {
        if (this.parts.isEmpty()) return false;
        boolean shouldHaveEvaluable = this.parts.get(0) instanceof Evaluable;

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

    @Override
    public String toString() {
        return this.parts.stream().map(Part::toString).collect(Collectors.joining());
    }

    public static MathExpression parse(String expression) {
        return parse(expression, new ConcurrentHashMap<>());
    }

    public static MathExpression parse(String expression, Map<String, Double> variables) {
        if (expression.trim().isEmpty())
            return new MathExpression(new ArrayList<>(), variables);

        final Pattern pattern = Pattern.compile("([()]|[^()]+)");
        final Matcher matcher = pattern.matcher(expression);

        final List<String> tokens = new ArrayList<>();
        while (matcher.find())
            tokens.add(matcher.group());

        final Stack<List<Part>> partStack = new Stack<>();

        List<Part> current = new ArrayList<>();

        for (final String token : tokens) {
            if (token == null) continue;
            if (token.equals("(")) {
                partStack.push(current);
                current = new ArrayList<>();
            } else if (token.equals(")")) {
                if (partStack.isEmpty())
                    throw new IllegalStateException("Illegal closing parenthesis");
                final List<Part> last = partStack.lastElement();
                final Part part;
                if (!last.isEmpty() && (part = last.get(last.size() - 1)) instanceof Variable) {
                    last.remove(last.size() - 1);
                    last.add(new Function(((Variable) part).name, splitComma(current).stream().map(list -> new MathExpression(list, variables)).collect(Collectors.toList())));
                } else partStack.lastElement().add(new Parenthesis(new MathExpression(current, variables)));
                current = partStack.pop();
            } else {
                current.addAll(parseSimple(token));
            }
        }

        partStack.push(current);

        if (partStack.isEmpty())
            throw new IllegalStateException("Empty expression?" + partStack);
        final List<Part> parts;
        if (partStack.size() != 1 || (parts = partStack.get(0)).isEmpty() || !parts.get(parts.size() - 1).isComplete())
            throw new IllegalStateException("Incomplete expression, " + partStack);

        return new MathExpression(parts, variables);
    }

    private static List<Part> parseSimple(String tokens) {
        final String operators = Arrays.stream(OperatorType.values())
                .map(OperatorType::getSymbol)
                .map(chara -> "\\" + chara)
                .collect(Collectors.joining());
        final Pattern pattern = Pattern.compile("([A-Za-z0-9]+)|,|(-?\\d+(?>\\\\.\\d+|\\.)?)|[" + operators + "]");
        final Matcher matcher = pattern.matcher(tokens);

        final List<Part> parts = new ArrayList<>();

        String onHold = null;
        Part last = null;

        while (matcher.find()) {
            final String token = matcher.group();
            final Double number = tryParse(token);

            final Part part;

            if (number != null)
                part = new Part.Number(number);
            else if (token.equals(","))
                part = new Comma();
            else if (token.length() == 1 && operators.contains(token))
                part = new Operator(OperatorType.valueOfSymbol(token.charAt(0)));
            else {
                // is a name
                // could be a variable or a function name
                if (onHold != null) {
                    // relaxing a bit here and say 2 names separated by blanks means multiplication
                    parts.add(new Part.Variable(onHold));
                    parts.add(new Operator(OperatorType.MULTIPLY));
                    parts.add(new Part.Variable(token));
                    onHold = null;
                } else {
                    if (last instanceof Part.Number) {
                        // something like "2a"
                        parts.add(new Operator(OperatorType.MULTIPLY));
                        parts.add(new Part.Variable(token)
                        );
                    } else onHold = token;
                }
                continue;
            }

            if (onHold != null) {
                // part could be a number or operator
                if (part instanceof Part.Number)
                    throw new IllegalStateException("Cannot have variable directly before a number at index " + matcher.start() + " (" + parts + ")");

                parts.add(last = new Part.Variable(onHold));
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
                    throw new IllegalStateException("Unexpected token at index " + matcher.start() + ", expecting " + last.validNextParts().stream().map(Class::getSimpleName).collect(Collectors.joining(" or ")) + " (" + parts + ")");
            }
        }

        // this would probably and very likely be a function
        if (onHold != null) parts.add(new Part.Variable(onHold));
        return parts;
    }

    private static List<List<Part>> splitComma(List<Part> parts) {
        final List<List<Part>> split = new ArrayList<>();
        List<Part> current = new ArrayList<>();
        for (Part part : parts) {
            if (part instanceof Comma) {
                split.add(current);
                current = new ArrayList<>();
            } else current.add(part);
        }
        split.add(current);
        return split;
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

        @EqualsAndHashCode
        @Accessors(fluent = true)
        @Getter
        final class Number implements Part, Evaluable {
            private final double number;

            public Number(double number) {
                this.number = number;
            }

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return Arrays.asList(Operator.class, Parenthesis.class);
            }

            @Override
            public boolean isComplete() {
                return true;
            }

            @Override
            public double evaluate(Map<String, Double> variables) {
                return this.number;
            }

            @Override
            public String toString() {
                return String.valueOf(this.number);
            }

        }
        
        @EqualsAndHashCode
        @Accessors(fluent = true)
        @Getter
        @RequiredArgsConstructor
        final class Operator implements Part {
            private final OperatorType operator;

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return Arrays.asList(Number.class, Parenthesis.class, Operator.class);
            }

            @Override
            public boolean isComplete() {
                return false;
            }

            @Override
            public String toString() {
                return this.operator.name();
            }


            @Getter
            @Accessors(fluent = false)
            @RequiredArgsConstructor
            public enum OperatorType {
                // these are in order of operation
                POWER('^', Math::pow, 0, null),
                MULTIPLY('*', (a, b) -> a * b, 1, null),
                DIVIDE('/', (a, b) -> a / b, 1, null),
                ADD('+', Double::sum, 2, 1),
                SUBTRACT('-', (a, b) -> a - b, 2, -1),
                ;

                private final char symbol;
                private final BiFunction<Double, Double, Double> combiner;
                private final int executionIndex;
                private final /* nullable */ Integer modifier;

                public static OperatorType valueOfSymbol(char c) {
                    for (OperatorType type : values()) {
                        if (type.symbol == c) return type;
                    }
                    throw new IllegalArgumentException("Could not find operator for symbol " + c);
                }

                public static List<EnumSet<OperatorType>> getByOrder() {
                    return Arrays.stream(OperatorType.values())
                            .sorted(Comparator.comparing(OperatorType::getExecutionIndex))
                            .reduce(new ArrayList<>(), (list, operator) -> {
                                if (list.size() == operator.getExecutionIndex())
                                    list.add(EnumSet.of(operator));
                                else
                                    list.get(operator.getExecutionIndex()).add(operator);
                                return list;
                            }, (listA, listB) -> {
                                listA.addAll(listB);
                                return listA;
                            });
                }
            }
        }

        @EqualsAndHashCode
        @Accessors(fluent = true)
        @Getter
        final class Parenthesis implements Part, Evaluable {
            private final MathExpression expression;

            public Parenthesis(MathExpression expression) {
                this.expression = expression;
            }

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return Arrays.asList(Operator.class, Parenthesis.class);
            }

            @Override
            public boolean isComplete() {
                return true;
            }

            @Override
            public double evaluate(Map<String, Double> variables) {
                return this.expression.evaluate();
            }

            @Override
            public String toString() {
                return "(" + this.expression.toString() + ")";
            }

        }

        @EqualsAndHashCode
        @Accessors(fluent = true)
        @Getter
        final class Function implements Part, Evaluable {
            private final String name;
            private final List<MathExpression> expressions;


            public Function(String name, List<MathExpression> expressions) {
                this.name = name;
                this.expressions = expressions;
                this.expressions.removeIf(expression -> expression.getParts().isEmpty());
            }

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return Collections.singletonList(Operator.class);
            }

            @Override
            public boolean isComplete() {
                return true;
            }

            @Override
            public double evaluate(Map<String, Double> variables) {
                final Object[] evaluatedValues = new Object[this.expressions.size()];
                for (int i = 0; i < this.expressions.size(); i++) {
                    evaluatedValues[i] = this.expressions.get(i).evaluate();
                }
                final Class<?>[] classes = new Class<?>[evaluatedValues.length];
                Arrays.fill(classes, double.class);
                try {
                    final Method mathMethod = Math.class.getMethod(this.name, classes);
                    return (double) mathMethod.invoke(null, evaluatedValues);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Unknown math function " + this.name + " " + Arrays.toString(classes));
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Argument mismatch for math function " + this.name + " " + Arrays.toString(classes) + " (supplied " + Arrays.toString(evaluatedValues) + ")");
                }
            }

            @Override
            public String toString() {
                return this.name + "(" + this.expressions.stream().map(MathExpression::toString).collect(Collectors.joining(", ")) + ")";
            }

        }


        @EqualsAndHashCode
        @Accessors(fluent = true)
        @Getter
        @RequiredArgsConstructor
        final class Variable implements Part, Evaluable {

            private static final Map<String, Double> BUILT_IN = new ConcurrentHashMap<>();
            private final String name;

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
                return Collections.singletonList(Operator.class);
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

            @Override
            public String toString() {
                return this.name;
            }

        }

        @EqualsAndHashCode
        @Accessors(fluent = true)
        @Getter
        final class Comma implements Part {

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return Arrays.asList(Parenthesis.class, Number.class, Variable.class, Function.class);
            }

            @Override
            public boolean isComplete() {
                return false;
            }

            @Override
            public String toString() {
                return ", ";
            }

        }

    }

}
