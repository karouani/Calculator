package com.xlythe.math;

import android.content.Context;

import org.javia.arity.Complex;
import org.javia.arity.Symbols;
import org.javia.arity.SyntaxException;

import java.util.Locale;

/**
 * Solves math problems
 *
 * Supports:
 * Basic math + functions (trig, pi)
 * Matrices
 * Hex and Bin conversion
 */
public class Solver {
    // Used for solving basic math
    private Symbols mSymbols = new Symbols();
    private BaseModule mBaseModule;
    private MatrixModule mMatrixModule;
    private GraphModule mGraphModule;
    private int mLineLength = 8;
    private Localizer mLocalizer;

    public Solver() {
        mBaseModule = new BaseModule(this);
        mMatrixModule = new MatrixModule(this);
        mGraphModule = new GraphModule(this);
    }

    /**
     * Input an equation as a string
     * ex: sin(150)
     * and get the result returned.
     * */
    public String solve(String input) throws SyntaxException {
        if(displayContainsMatrices(input)) {
            return mMatrixModule.evaluateMatrices(input);
        }

        if(input.trim().isEmpty()) {
            return "";
        }

        if(mLocalizer != null) input = mLocalizer.localize(input);

        // Drop final infix operators (they can only result in error)
        int size = input.length();
        while(size > 0 && isOperator(input.charAt(size - 1))) {
            input = input.substring(0, size - 1);
            --size;
        }

        // Convert to decimal
        String decimalInput = convertToDecimal(input);

        Complex value = mSymbols.evalComplex(decimalInput);

        String real = "";
        for(int precision = mLineLength; precision > 6; precision--) {
            real = tryFormattingWithPrecision(value.re, precision);
            if(real.length() <= mLineLength) {
                break;
            }
        }

        String imaginary = "";
        for(int precision = mLineLength; precision > 6; precision--) {
            imaginary = tryFormattingWithPrecision(value.im, precision);
            if(imaginary.length() <= mLineLength) {
                break;
            }
        }

        real = clean(mBaseModule.changeBase(real, Base.DECIMAL, mBaseModule.getBase()));
        imaginary = clean(mBaseModule.changeBase(imaginary, Base.DECIMAL, mBaseModule.getBase()));

        String result = "";
        if(value.re != 0 && value.im == 1) result = real + "+" + "i";
        else if(value.re != 0 && value.im > 0) result = real + "+" + imaginary + "i";
        else if(value.re != 0 && value.im == -1) result = real + "-" + "i";
        else if(value.re != 0 && value.im < 0) result = real + imaginary + "i"; // Implicit -
        else if(value.re != 0 && value.im == 0) result = real;
        else if(value.re == 0 && value.im == 1) result = "i";
        else if(value.re == 0 && value.im == -1) result = "-i";
        else if(value.re == 0 && value.im != 0) result = imaginary + "i";
        else if(value.re == 0 && value.im == 0) result = "0";

        if(mLocalizer != null) result = mLocalizer.relocalize(result);

        return result;
    }

    public double eval(String input) throws SyntaxException{
        return mSymbols.eval(input);
    }

    public void pushFrame() {
        mSymbols.pushFrame();
    }

    public void popFrame() {
        mSymbols.popFrame();
    }

    public void define(String var, double val) {
        mSymbols.define(var, val);
    }

    public static boolean equal(String a, String b) {
        return clean(a).equals(clean(b));
    }

    public static String clean(String equation) {
        return equation
                .replace('-', Constants.MINUS)
                .replace('/', Constants.DIV)
                .replace('*', Constants.MUL)
                .replace(Constants.INFINITY, Constants.INFINITY_UNICODE);
    }

    public static boolean isOperator(char c) {
        return ("" +
                Constants.PLUS +
                Constants.MINUS +
                Constants.DIV +
                Constants.MUL +
                Constants.POWER).indexOf(c) != -1;
    }

    public static boolean isOperator(String c) {
        return isOperator(c.charAt(0));
    }

    public static boolean isNegative(String number) {
        return number.startsWith(String.valueOf(Constants.MINUS)) || number.startsWith("-");
    }

    public static boolean isDigit(char number) {
        return String.valueOf(number).matches(Constants.REGEX_NUMBER);
    }

    boolean displayContainsMatrices(String text) {
        return getMatrixModule().isMatrix(text);
    }

    public String convertToDecimal(String input) throws SyntaxException{
        return mBaseModule.changeBase(input, mBaseModule.getBase(), Base.DECIMAL);
    }

    String tryFormattingWithPrecision(double value, int precision) throws SyntaxException {
        // The standard scientific formatter is basically what we need. We will
        // start with what it produces and then massage it a bit.
        String result = String.format(Locale.US, "%" + mLineLength + "." + precision + "g", value);
        if(result.equals(Constants.NAN)) {
            throw new SyntaxException();
        }
        String mantissa = result;
        String exponent = null;
        int e = result.indexOf('e');
        if(e != -1) {
            mantissa = result.substring(0, e);

            // Strip "+" and unnecessary 0's from the exponent
            exponent = result.substring(e + 1);
            if(exponent.startsWith("+")) {
                exponent = exponent.substring(1);
            }
            exponent = String.valueOf(Integer.parseInt(exponent));
        }

        int period = mantissa.indexOf('.');
        if(period == -1) {
            period = mantissa.indexOf(',');
        }
        if(period != -1) {
            // Strip trailing 0's
            while(mantissa.length() > 0 && mantissa.endsWith("0")) {
                mantissa = mantissa.substring(0, mantissa.length() - 1);
            }
            if(mantissa.length() == period + 1) {
                mantissa = mantissa.substring(0, mantissa.length() - 1);
            }
        }

        if(exponent != null) {
            result = mantissa + 'e' + exponent;
        } else {
            result = mantissa;
        }
        return result;
    }

    public void enableLocalization(Context context, Class r) {
        mLocalizer = new Localizer(context, r);
    }

    public void setLineLength(int length) {
        mLineLength = length;
    }

    public void setBase(Base base) {
        mBaseModule.setBase(base);
    }

    public Base getBase() {
        return mBaseModule.getBase();
    }

    public BaseModule getBaseModule() {
        return mBaseModule;
    }

    public MatrixModule getMatrixModule() {
        return mMatrixModule;
    }

    public GraphModule getGraphModule() {
        return mGraphModule;
    }
}