package com.calculator;

import java.math.BigDecimal;

/**
 * Self-contained Unit and Regression Test suite for {@link CalculatorApp}.
 * Demonstrates test-driven design without external framework dependencies.
 */
public class CalculatorAppTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("Running CalculatorApp Test Suite...\n");

        testBasicArithmetic();
        testOperatorPrecedence();
        testParentheses();
        testPrecisionBigDecimal();
        testUnaryMinus();
        testExponentiationAssociativity();
        testDivisionByZero();
        testMismatchedParentheses();
        testAnsChaining();

        System.out.println("\n-------------------------------------------");
        System.out.printf("Test Results: %d Passed, %d Failed%n", testsPassed, testsFailed);
        System.out.println("-------------------------------------------");
        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void testBasicArithmetic() {
        assertEval("10 + 25", "35", "Basic addition");
        assertEval("50 - 18.5", "31.5", "Basic subtraction with decimal");
        assertEval("12 * 12", "144", "Basic multiplication");
        assertEval("10 / 4", "2.5", "Basic division");
        assertEval("14 % 4", "2", "Modulo");
    }

    private static void testOperatorPrecedence() {
        // Multiplication takes precedence over addition: 2 + (3 * 4) = 14
        assertEval("2 + 3 * 4", "14", "Precedence: * over +");
        // Modulo and division take precedence over subtraction
        assertEval("20 - 6 / 2", "17", "Precedence: / over -");
    }

    private static void testParentheses() {
        assertEval("(2 + 3) * 4", "20", "Parentheses override precedence");
        assertEval("((5 + 5) * (3 - 1)) / 4", "5", "Nested parentheses");
    }

    private static void testPrecisionBigDecimal() {
        // Classic IEEE 754 floating point trap: 0.1 + 0.2 = 0.30000000000000004 in standard double
        assertEval("0.1 + 0.2", "0.3", "Arbitrary precision 0.1 + 0.2 == 0.3");
        // Division with non-terminating decimal (1/3) should not crash
        BigDecimal result = CalculatorApp.ExpressionEvaluator.evaluate("1 / 3", null);
        assertTrue(result.toString().startsWith("0.3333333333"), "Non-terminating decimal 1/3 scale handling");
    }

    private static void testUnaryMinus() {
        assertEval("-5 + 10", "5", "Leading unary minus");
        assertEval("10 * -2", "-20", "Unary minus after operator");
        assertEval("-(4 + 6)", "-10", "Unary minus on parenthesized expression");
    }

    private static void testExponentiationAssociativity() {
        // Exponentiation is right-associative: 2 ^ 3 ^ 2 = 2 ^ (3 ^ 2) = 2 ^ 9 = 512
        assertEval("2 ^ 3 ^ 2", "512", "Right associativity of exponentiation (^)");
    }

    private static void testDivisionByZero() {
        try {
            CalculatorApp.ExpressionEvaluator.evaluate("10 / 0", null);
            fail("Expected ArithmeticException for division by zero");
        } catch (ArithmeticException e) {
            pass("Division by zero properly threw ArithmeticException: " + e.getMessage());
        }
    }

    private static void testMismatchedParentheses() {
        try {
            CalculatorApp.ExpressionEvaluator.evaluate("(2 + 3 * 5", null);
            fail("Expected IllegalArgumentException for mismatched opening parenthesis");
        } catch (IllegalArgumentException e) {
            pass("Mismatched opening parenthesis detected: " + e.getMessage());
        }

        try {
            CalculatorApp.ExpressionEvaluator.evaluate("2 + 3) * 5", null);
            fail("Expected IllegalArgumentException for mismatched closing parenthesis");
        } catch (IllegalArgumentException e) {
            pass("Mismatched closing parenthesis detected: " + e.getMessage());
        }
    }

    private static void testAnsChaining() {
        CalculatorApp.CalculatorService service = new CalculatorApp.CalculatorService();
        BigDecimal r1 = service.calculate("10 + 5");
        assertEquals("15", r1.toPlainString(), "First calculation step");

        // Chain with ans explicitly
        BigDecimal r2 = service.calculate("ans * 2");
        assertEquals("30", r2.toPlainString(), "Explicit 'ans' usage");

        // Chain with auto-prefix operator
        BigDecimal r3 = service.calculate("+ 20");
        assertEquals("50", r3.toPlainString(), "Auto-prefix operator with 'ans'");
    }

    // Helper assertions
    private static void assertEval(String expr, String expected, String testName) {
        try {
            BigDecimal actual = CalculatorApp.ExpressionEvaluator.evaluate(expr, null);
            assertEquals(expected, actual.toPlainString(), testName);
        } catch (Exception e) {
            fail(testName + " failed with exception: " + e.getMessage());
        }
    }

    private static void assertEquals(String expected, String actual, String testName) {
        if (expected.equals(actual)) {
            pass(testName + " [Expected: " + expected + ", Got: " + actual + "]");
        } else {
            fail(testName + " [Expected: " + expected + ", Got: " + actual + "]");
        }
    }

    private static void assertTrue(boolean condition, String testName) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName + " condition was false");
        }
    }

    private static void pass(String msg) {
        testsPassed++;
        System.out.println("  [PASS] " + msg);
    }

    private static void fail(String msg) {
        testsFailed++;
        System.err.println("  [FAIL] " + msg);
    }
}
