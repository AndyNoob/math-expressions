package me.comfortable_andy.math;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
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
        if (this.parts.isEmpty()) throw new IllegalStateException("Empty parts list");
        if (!isValid()) throw new IllegalStateException("Invalid expression, missing operators");

        // then evaluate all evaluable parts

        final List<Double> evaluated = new ArrayList<>();
        // the size of this list should be 1 less than the evaluated list
        final List<Operator> operators = new ArrayList<>();

        for (Part part : this.parts) {
            if (part instanceof Part.Evaluable) {
                evaluated.add(((Part.Evaluable) part).evaluate(this.variables));
            } else {
                operators.add((Operator) part);
            }
        }

        for (EnumSet<OperatorType> types : OperatorType.getByOrder()) {
            // the constants in the operator enum are ordered by their precedence in
            // order of operation
            pass(types, evaluated, operators);
        }

        return evaluated.get(0);
    }

    private void pass(EnumSet<OperatorType> types, List<Double> evaluated, List<Operator> operators) {
        for (int i = 0; i < operators.size(); i++) {
            if (!types.contains(operators.get(i).operator())) continue;

            final double left = evaluated.get(i);
            final double right = evaluated.get(i + 1);
            final OperatorType operator = operators.get(i)
                    .operator();
            final double result = operator
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
        if (expression.isBlank())
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
                if (!last.isEmpty() && last.get(last.size() - 1) instanceof Variable variable) {
                    last.remove(last.size() - 1);
                    last.add(new Function(variable.name, splitComma(current).stream().map(list -> new MathExpression(list, variables)).collect(Collectors.toList())));
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
        final Pattern pattern = Pattern.compile("([A-Za-z0-9]+)|,|(-?\\\\d+(?>\\\\.\\\\d+|\\\\.)?)|[" + operators + "]");
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
                POWER('^', Math::pow, 0),
                MULTIPLY('*', (a, b) -> a * b, 1),
                DIVIDE('/', (a, b) -> a / b, 1),
                ADD('+', Double::sum, 2),
                SUBTRACT('-', (a, b) -> a - b, 2),
                ;

                private final char symbol;
                private final BiFunction<Double, Double, Double> combiner;
                private final int executionIndex;

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

        record Function(String name, List<MathExpression> expressions) implements Part, Evaluable {

            public Function(String name, List<MathExpression> expressions) {
                this.name = name;
                this.expressions = expressions;
                this.expressions.removeIf(expression -> expression.getParts().isEmpty());
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

        record Comma() implements Part {

            @Override
            public List<Class<? extends Part>> validNextParts() {
                return List.of(Parenthesis.class, Number.class, Variable.class, Function.class);
            }

            @Override
            public boolean isComplete() {
                return false;
            }
        }

    }

}
