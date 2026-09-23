# Production-Grade Java Calculator

A robust, extensible, and mathematically precise command-line calculator written in Java.

---

## 🛠️ Key Architectural Highlights (Senior Engineer Perspective)

1. **Arbitrary-Precision Arithmetic (`BigDecimal` & `MathContext`)**:
   - Standard `double` and `float` suffer from binary floating-point rounding inaccuracies under IEEE 754 (e.g., `0.1 + 0.2 = 0.30000000000000004`).
   - Uses `BigDecimal` with configured `RoundingMode.HALF_UP` and `MathContext.DECIMAL128` to guarantee exact decimal arithmetic and prevent infinite decimal expansion exceptions (e.g., `1 / 3`).

2. **Dijkstra's Shunting-Yard Algorithm & Reverse Polish Notation (RPN)**:
   - Full mathematical expression support with proper operator precedence, associativity (including right-associative exponentiation `^`), parentheses grouping `( )`, and unary negation (`-`).

3. **Strategy Pattern for Extensibility (Open-Closed Principle)**:
   - Operators implement a `BinaryOperator` strategy and are registered dynamically in `OperatorRegistry`. New operations can be registered at runtime without modifying parser or evaluation logic.

4. **Production Quality Features**:
   - **Auto-Chaining**: Entering `+ 10` automatically resolves to `ans + 10`.
   - **State Management**: `ans` variable and calculation history with timestamps.
   - **Defensive Error Handling**: Meaningful syntax and arithmetic error messages instead of raw stack traces.

---

## 🚀 How to Run

### Compile & Execute Application:
```bash
# Navigate to directory
cd C:\Users\nandi\.gemini\antigravity\scratch\java-calculator

# Compile
javac CalculatorApp.java

# Run
java com.calculator.CalculatorApp
```

### Run Automated Test Suite:
```bash
javac CalculatorAppTest.java
java com.calculator.CalculatorAppTest
```
