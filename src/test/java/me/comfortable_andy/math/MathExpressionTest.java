package me.comfortable_andy.math;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static com.diogonunes.jcolor.Ansi.colorize;
import static com.diogonunes.jcolor.Attribute.*;
import static org.junit.jupiter.api.Assertions.*;

public class MathExpressionTest {

    private static final Map<String, Double> SIMPLE_EXPRESSIONS = Map.of(
            "1 + 1", 2.0,
            "1 + 1 * 2", 3.0,
            "1 + 1 * 2 / 2", 2.0,
            "1 + 1 * 2 / 2 ^ 2", 1.5,
            "1 + 1 * 2 / 2 ^ 2 - 1", 0.5,
            "cos(0)", 1.0,
            "cos(0) + 1", 2.0,
            "cos(0) + 1 * 2", 3.0,
            "cos(0) + 1 * 2 / 2", 2.0,
            "cos(0) + 1 * 2 / 2 ^ 2", 1.5
    );

    private static final Map<String, Double> HARDER_EXPRESSIONS = Map.of(
            "acos(cos(0))", 0.0,
            "asin(sin(0))", 0.0,
            "sin(x)", 0.0,
            "2^(sin(pi))", 1.0,
            "sin(toRadians(77))", 0.9743700648
    );

    @Test
    public void testEvaluate() {
        final Map<String, Double> testing = new HashMap<>();
        testing.putAll(SIMPLE_EXPRESSIONS);
        testing.putAll(HARDER_EXPRESSIONS);
        final List<Map.Entry<String, Double>> entries = new ArrayList<>(testing.entrySet());
        Collections.shuffle(entries);
        for (Map.Entry<String, Double> entry : entries) {
            final long time = System.currentTimeMillis();
            System.out.println("Received expression: " + colorize(entry.getKey(), BRIGHT_BLACK_BACK()));
            final MathExpression expression = MathExpression.parse(entry.getKey());
            System.out.println("Expression parsed: " + syntaxHighlight(expression));
            expression.setVariable("x", 0);
            System.out.println("Expecting: " + colorize(String.valueOf(entry.getValue()), BRIGHT_BLACK_BACK()));
            final double evaluated = expression.evaluate();
            System.out.println("Evaluated: " + colorize(String.valueOf(evaluated), BLACK_TEXT(), BRIGHT_WHITE_BACK()));
            assertTrue(Math.abs(entry.getValue() - evaluated) < 0.000001);
            System.out.println("Done in " +  colorize(System.currentTimeMillis() - time + "", BOLD()) + "ms");
            System.out.println();
        }
    }

    private static String syntaxHighlight(MathExpression expression) {
        return expression.getParts().stream().map(part -> {
            if (part instanceof MathExpression.Part.Number number) return colorize(number.number() + "", BRIGHT_WHITE_TEXT());
            else if (part instanceof MathExpression.Part.Operator operator) return colorize(operator.operator().getSymbol() + "", BOLD());
            else if (part instanceof MathExpression.Part.Parenthesis parenthesis) return "(" + syntaxHighlight(parenthesis.expression()) + ")";
            else if (part instanceof MathExpression.Part.Function function) return colorize(function.name(), ITALIC()) + "(" + syntaxHighlight(function.parenthesis().expression()) + ")";
            else if (part instanceof MathExpression.Part.Variable variable) return colorize(variable.name(), BRIGHT_WHITE_TEXT());
            return "UNKNOWN_PART";
        }).collect(Collectors.joining(" "));
    }

}