package com.slinky.katas.internal.tracing;

public final class StepMarkerFixture implements StepMarkerTest.Calls {

    @Override
    public int gcd(int a, int b) {
        while (b != 0) {
            int remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }

    @Override
    public int sumTo(int n) {
        return sumStatic(n);
    }

    @Override
    public double average(long total, int count) {
        return (double) total / count;
    }

    @Override
    public boolean isLetter(char symbol) {
        boolean letter = Character.isLetter(symbol);
        return letter;
    }

    @Override
    public String shout(String word) {
        String loud = word.toUpperCase();
        return loud;
    }

    @Override
    public void store(int value) {
        int copy = value;
    }

    @Override
    public boolean isSame(Object left, Object right) {
        if (left == right) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isMissing(Object value) {
        if (value == null) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isBetween(int x, int low, int high) {
        if (low < x && x < high) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isPositive(int x) {
        if ((x > 0 ? 1 : 0) == 1) {
            return true;
        }
        return false;
    }

    @Override
    public int countUp(int times) {
        int count = 0;
        for (int i = 0; i < times; i++) {
            if (i >= 0) {
                count++;
            } else {
                count--;
            }
        }
        return count;
    }

    private static int sumStatic(int n) {
        int total = 0;
        for (int i = 1; i <= n; i++) {
            total += i;
        }
        return total;
    }

}
