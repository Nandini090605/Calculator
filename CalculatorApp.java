package com.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Production-ready, extensible Expression Calculator in Java.
 *
 * Designed with Senior SE best practices:
 * - Arbitrary-precision arithmetic using {@link BigDecimal} and {@link MathContext} (prevents IEEE 754 floating point errors).
 * - Separation of Concerns: Lexer (Tokenizer), Parser (Shunting-Yard / RPN), Service, Domain Models, and CLI Presentation.
 * - Strategy Pattern for extensible operator registration (Open-Closed Principle).
 * - Interactive REPL with state tracking ('ans' variable, auto-chaining, history, undo/clear).
 * - Resilient exception handling and input validation.
 */
public class CalculatorApp {

    // =========================================================================
    // 1. DOMAIN MODELS & OPERATOR STRATEGY
    // =========================================================================

    public enum Associativity {
        LEFT, RIGHT
    }

    /**
     * Strategy interface for binary operations.
     */
    public interface BinaryOperator {
        String getSymbol();
        int getPrecedence();
        Associativity getAssociativity();
        BigDecimal apply(BigDecimal left, BigDecimal right);
    }

    /**
     * Standard arithmetic operations registry.
     */
    public static final class OperatorRegistry {
        private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
        private static final int DEFAULT_SCALE = 10;
        private static final Map<String, BinaryOperator> OPERATORS = new HashMap<>();

        static {
            register(new BaseBinaryOperator("+", 1, Associativity.LEFT, BigDecimal::add));
            register(new BaseBinaryOperator("-", 1, Associativity.LEFT, BigDecimal::subtract));
            register(new BaseBinaryOperator("*", 2, Associativity.LEFT, (a, b) -> a.multiply(b, MATH_CONTEXT)));
            register(new BaseBinaryOperator("/", 2, Associativity.LEFT, (a, b) -> {
                if (b.compareTo(BigDecimal.ZERO) == 0) {
                    throw new ArithmeticException("Division by zero is undefined.");
                }
                return a.divide(b, DEFAULT_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
            }));
            register(new BaseBinaryOperator("%", 2, Associativity.LEFT, (a, b) -> {
                if (b.compareTo(BigDecimal.ZERO) == 0) {
                    throw new ArithmeticException("Modulo by zero is undefined.");
                }
                return a.remainder(b, MATH_CONTEXT);
            }));
            register(new BaseBinaryOperator("^", 3, Associativity.RIGHT, (a, b) -> {
                try {
                    // Try exact integer power first for maximum precision
                    int exponent = b.intValueExact();
                    if (exponent < 0) {
                        BigDecimal positivePow = a.pow(-exponent, MATH_CONTEXT);
                        return BigDecimal.ONE.divide(positivePow, DEFAULT_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
                    }
                    return a.pow(exponent, MATH_CONTEXT).stripTrailingZeros();
                } catch (ArithmeticException e) {
                    // Fractional exponent fallback via Math.pow
                    double result = Math.pow(a.doubleValue(), b.doubleValue());
                    if (Double.isNaN(result) || Double.isInfinite(result)) {
                        throw new ArithmeticException("Result of exponentiation is undefined or out of range.");
                    }
                    return BigDecimal.valueOf(result).stripTrailingZeros();
                }
            }));
        }

        public static void register(BinaryOperator op) {
            OPERATORS.put(op.getSymbol(), op);
        }

        public static boolean isOperator(String symbol) {
            return OPERATORS.containsKey(symbol);
        }

        public static BinaryOperator get(String symbol) {
            return OPERATORS.get(symbol);
        }

        public static Set<String> getRegisteredSymbols() {
            return Collections.unmodifiableSet(OPERATORS.keySet());
        }

        private static class BaseBinaryOperator implements BinaryOperator {
            private final String symbol;
            private final int precedence;
            private final Associativity associativity;
            private final BiFunction<BigDecimal, BigDecimal, BigDecimal> operation;

            public BaseBinaryOperator(String symbol, int precedence, Associativity associativity,
                                      BiFunction<BigDecimal, BigDecimal, BigDecimal> operation) {
                this.symbol = symbol;
                this.precedence = precedence;
                this.associativity = associativity;
                this.operation = operation;
            }

            @Override public String getSymbol() { return symbol; }
            @Override public int getPrecedence() { return precedence; }
            @Override public Associativity getAssociativity() { return associativity; }
            @Override public BigDecimal apply(BigDecimal left, BigDecimal right) { return operation.apply(left, right); }
        }
    }

    // =========================================================================
    // 2. TOKENIZER & SHUNTING-YARD EVALUATOR
    // =========================================================================

    public enum TokenType {
        NUMBER, OPERATOR, UNARY_MINUS, LPAREN, RPAREN
    }

    public static final class Token {
        private final TokenType type;
        private final String value;

        public Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }

        public TokenType getType() { return type; }
        public String getValue() { return value; }

        @Override
        public String toString() {
            return String.format("Token(%s, '%s')", type, value);
        }
    }

    public static final class ExpressionEvaluator {

        /**
         * Tokenizes an arithmetic expression, properly resolving unary minus and decimal numbers.
         */
        public static List<Token> tokenize(String expression, BigDecimal ans) {
            List<Token> tokens = new ArrayList<>();
            int i = 0;
            int n = expression.length();

            while (i < n) {
                char ch = expression.charAt(i);

                if (Character.isWhitespace(ch)) {
                    i++;
                    continue;
                }

                // Handle 'ans' variable
                if (i + 3 <= n && expression.substring(i, i + 3).equalsIgnoreCase("ans")) {
                    if (ans == null) {
                        throw new IllegalArgumentException("'ans' is not set. Perform a calculation first.");
                    }
                    tokens.add(new Token(TokenType.NUMBER, ans.toPlainString()));
                    i += 3;
                    continue;
                }

                // Parentheses
                if (ch == '(') {
                    tokens.add(new Token(TokenType.LPAREN, "("));
                    i++;
                    continue;
                }
                if (ch == ')') {
                    tokens.add(new Token(TokenType.RPAREN, ")"));
                    i++;
                    continue;
                }

                // Check for Unary Minus vs Binary Minus
                if (ch == '-') {
                    boolean isUnary = tokens.isEmpty() ||
                            tokens.get(tokens.size() - 1).getType() == TokenType.OPERATOR ||
                            tokens.get(tokens.size() - 1).getType() == TokenType.UNARY_MINUS ||
                            tokens.get(tokens.size() - 1).getType() == TokenType.LPAREN;

                    if (isUnary) {
                        // If immediately followed by a digit or decimal point, parse as negative number literal
                        if (i + 1 < n && (Character.isDigit(expression.charAt(i + 1)) || expression.charAt(i + 1) == '.')) {
                            int start = i++;
                            while (i < n && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                                i++;
                            }
                            String numStr = expression.substring(start, i);
                            validateNumberLiteral(numStr);
                            tokens.add(new Token(TokenType.NUMBER, numStr));
                            continue;
                        } else {
                            tokens.add(new Token(TokenType.UNARY_MINUS, "u-"));
                            i++;
                            continue;
                        }
                    }
                }

                // Numbers (digits & decimal points)
                if (Character.isDigit(ch) || ch == '.') {
                    int start = i;
                    while (i < n && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                        i++;
                    }
                    String numStr = expression.substring(start, i);
                    validateNumberLiteral(numStr);
                    tokens.add(new Token(TokenType.NUMBER, numStr));
                    continue;
                }

                // Binary Operators
                String singleCharOp = String.valueOf(ch);
                if (OperatorRegistry.isOperator(singleCharOp)) {
                    tokens.add(new Token(TokenType.OPERATOR, singleCharOp));
                    i++;
                    continue;
                }

                throw new IllegalArgumentException(String.format("Unexpected character '%c' at index %d.", ch, i));
            }

            return tokens;
        }

        private static void validateNumberLiteral(String s) {
            if (s.equals(".") || s.equals("-.")) {
                throw new IllegalArgumentException("Invalid number format: '" + s + "'");
            }
            long dotCount = s.chars().filter(c -> c == '.').count();
            if (dotCount > 1) {
                throw new IllegalArgumentException("Malformed number with multiple decimal points: " + s);
            }
        }

        /**
         * Evaluates token list using Dijkstra's Shunting-Yard algorithm & RPN evaluation.
         */
        public static BigDecimal evaluate(String expression, BigDecimal ans) {
            if (expression == null || expression.trim().isEmpty()) {
                throw new IllegalArgumentException("Expression cannot be empty.");
            }

            List<Token> tokens = tokenize(expression.trim(), ans);
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("No tokens found in expression.");
            }

            // Phase 1: Shunting-Yard -> Convert to Reverse Polish Notation (RPN)
            List<Token> rpnQueue = new ArrayList<>();
            Deque<Token> opStack = new ArrayDeque<>();

            for (Token token : tokens) {
                switch (token.getType()) {
                    case NUMBER:
                        rpnQueue.add(token);
                        break;

                    case UNARY_MINUS:
                        opStack.push(token);
                        break;

                    case OPERATOR:
                        BinaryOperator currentOp = OperatorRegistry.get(token.getValue());
                        while (!opStack.isEmpty()) {
                            Token top = opStack.peek();
                            if (top.getType() == TokenType.UNARY_MINUS) {
                                rpnQueue.add(opStack.pop());
                            } else if (top.getType() == TokenType.OPERATOR) {
                                BinaryOperator topOp = OperatorRegistry.get(top.getValue());
                                boolean isHigherPrec = topOp.getPrecedence() > currentOp.getPrecedence();
                                boolean isEqualPrecAndLeftAssoc = (topOp.getPrecedence() == currentOp.getPrecedence()) &&
                                        (currentOp.getAssociativity() == Associativity.LEFT);
                                if (isHigherPrec || isEqualPrecAndLeftAssoc) {
                                    rpnQueue.add(opStack.pop());
                                } else {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                        opStack.push(token);
                        break;

                    case LPAREN:
                        opStack.push(token);
                        break;

                    case RPAREN:
                        boolean foundMatchingParen = false;
                        while (!opStack.isEmpty()) {
                            Token top = opStack.pop();
                            if (top.getType() == TokenType.LPAREN) {
                                foundMatchingParen = true;
                                break;
                            }
                            rpnQueue.add(top);
                        }
                        if (!foundMatchingParen) {
                            throw new IllegalArgumentException("Mismatched parentheses: closing ')' has no matching '('.");
                        }
                        // If a unary minus was directly applied to parenthesized group: -(...)
                        if (!opStack.isEmpty() && opStack.peek().getType() == TokenType.UNARY_MINUS) {
                            rpnQueue.add(opStack.pop());
                        }
                        break;
                }
            }

            while (!opStack.isEmpty()) {
                Token top = opStack.pop();
                if (top.getType() == TokenType.LPAREN || top.getType() == TokenType.RPAREN) {
                    throw new IllegalArgumentException("Mismatched parentheses in expression.");
                }
                rpnQueue.add(top);
            }

            // Phase 2: Evaluate RPN Queue
            Deque<BigDecimal> evalStack = new ArrayDeque<>();
            for (Token token : rpnQueue) {
                if (token.getType() == TokenType.NUMBER) {
                    evalStack.push(new BigDecimal(token.getValue()));
                } else if (token.getType() == TokenType.UNARY_MINUS) {
                    if (evalStack.isEmpty()) {
                        throw new IllegalArgumentException("Invalid syntax: missing operand for unary minus.");
                    }
                    BigDecimal operand = evalStack.pop();
                    evalStack.push(operand.negate());
                } else if (token.getType() == TokenType.OPERATOR) {
                    if (evalStack.size() < 2) {
                        throw new IllegalArgumentException("Insufficient operands for operator '" + token.getValue() + "'.");
                    }
                    BigDecimal right = evalStack.pop();
                    BigDecimal left = evalStack.pop();
                    BinaryOperator op = OperatorRegistry.get(token.getValue());
                    evalStack.push(op.apply(left, right));
                }
            }

            if (evalStack.size() != 1) {
                throw new IllegalArgumentException("Invalid expression syntax: unbalanced operands and operators.");
            }

            return evalStack.pop().stripTrailingZeros();
        }
    }

    // =========================================================================
    // 3. AUDIT & HISTORY SERVICE
    // =========================================================================

    public static final class HistoryEntry {
        private final LocalDateTime timestamp;
        private final String expression;
        private final BigDecimal result;

        public HistoryEntry(String expression, BigDecimal result) {
            this.timestamp = LocalDateTime.now();
            this.expression = expression;
            this.result = result;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public String getExpression() { return expression; }
        public BigDecimal getResult() { return result; }

        @Override
        public String toString() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            return String.format("[%s]  %-24s = %s", timestamp.format(formatter), expression, result.toPlainString());
        }
    }

    public static final class CalculatorService {
        private final List<HistoryEntry> history = new ArrayList<>();
        private BigDecimal lastAnswer = null;

        public BigDecimal calculate(String input) {
            String trimmed = input.trim();

            // Auto-chaining: if user starts with an operator (e.g. "+ 10"), auto-prefix with "ans"
            if (!trimmed.isEmpty() && OperatorRegistry.isOperator(String.valueOf(trimmed.charAt(0)))) {
                if (lastAnswer == null) {
                    throw new IllegalStateException("No previous result ('ans') available to chain operation.");
                }
                trimmed = "ans " + trimmed;
            }

            BigDecimal result = ExpressionEvaluator.evaluate(trimmed, lastAnswer);
            this.lastAnswer = result;
            this.history.add(new HistoryEntry(trimmed, result));
            return result;
        }

        public BigDecimal getLastAnswer() {
            return lastAnswer;
        }

        public List<HistoryEntry> getHistory() {
            return Collections.unmodifiableList(history);
        }

        public void clear() {
            this.history.clear();
            this.lastAnswer = null;
        }
    }

    // =========================================================================
    // 4. PRESENTATION LAYER (CLI REPL)
    // =========================================================================

    public static void main(String[] args) {
        CalculatorService service = new CalculatorService();
        Scanner scanner = new Scanner(System.in);

        printWelcomeBanner();

        while (true) {
            System.out.print("\ncalc> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            // Command handling
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                System.out.println("Exiting calculator. Goodbye!");
                break;
            } else if (input.equalsIgnoreCase("help")) {
                printHelp();
            } else if (input.equalsIgnoreCase("history")) {
                printHistory(service.getHistory());
            } else if (input.equalsIgnoreCase("clear")) {
                service.clear();
                System.out.println("Calculation history and 'ans' have been cleared.");
            } else if (input.equalsIgnoreCase("ans")) {
                if (service.getLastAnswer() != null) {
                    System.out.println("ans = " + service.getLastAnswer().toPlainString());
                } else {
                    System.out.println("ans is currently undefined.");
                }
            } else {
                // Arithmetic calculation
                try {
                    BigDecimal result = service.calculate(input);
                    System.out.println("= " + result.toPlainString());
                } catch (ArithmeticException e) {
                    System.err.println("Math Error: " + e.getMessage());
                } catch (IllegalArgumentException | IllegalStateException e) {
                    System.err.println("Syntax Error: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Unexpected Error: " + e.getMessage());
                }
            }
        }

        scanner.close();
    }

    private static void printWelcomeBanner() {
        System.out.println("==========================================================");
        System.out.println("        Modern Java Precision Calculator (v2.0)          ");
        System.out.println("==========================================================");
        System.out.println(" Supports: +, -, *, /, %, ^, parentheses ( ), and 'ans'");
        System.out.println(" Features: High-precision BigDecimal, history, auto-chain");
        System.out.println(" Type 'help' for instructions, 'exit' to quit.");
        System.out.println("==========================================================");
    }

    private static void printHelp() {
        System.out.println("\n----------------- CALCULATOR HELP -----------------");
        System.out.println("Supported Operations:");
        System.out.println("  +  : Addition          (e.g., 12.5 + 4.2)");
        System.out.println("  -  : Subtraction       (e.g., 100 - 35.5)");
        System.out.println("  *  : Multiplication    (e.g., 6.5 * 4)");
        System.out.println("  /  : Division          (e.g., 22 / 7)");
        System.out.println("  %  : Modulo/Remainder  (e.g., 10 % 3)");
        System.out.println("  ^  : Exponentiation    (e.g., 2 ^ 8 or 4 ^ 0.5)");
        System.out.println("  ( ): Grouping          (e.g., (3 + 5) * (10 - 2))");
        System.out.println("Variables & Chaining:");
        System.out.println("  ans     : Uses the previous result (e.g., ans * 2)");
        System.out.println("  + 5     : Starting with an operator automatically chains to 'ans'");
        System.out.println("Commands:");
        System.out.println("  history : View all previous calculations");
        System.out.println("  clear   : Reset history and 'ans'");
        System.out.println("  ans     : Display current 'ans' value");
        System.out.println("  help    : Show this guide");
        System.out.println("  exit    : Quit the application");
        System.out.println("---------------------------------------------------");
    }

    private static void printHistory(List<HistoryEntry> history) {
        if (history.isEmpty()) {
            System.out.println("No calculation history recorded yet.");
            return;
        }
        System.out.println("\n--- Calculation History ---");
        for (int i = 0; i < history.size(); i++) {
            System.out.printf("%2d. %s%n", (i + 1), history.get(i));
        }
        System.out.println("---------------------------");
    }
}
