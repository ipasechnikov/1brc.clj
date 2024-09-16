package onebrc.java;

public class BitwiseHelpers {
    public static int isEqual(final byte a, final byte b) {
        final int xorResult = a ^ b;
        return ((xorResult | -xorResult) >>> 31) ^ 1 & 1;
    }

    public static int isDigit(final byte a) {
        final int digitRange = 9;
        final int diff = a - '0';
        return (diff | (digitRange - diff)) >>> 31 ^ 1;
    }

    public static int branchlessMin(final int a, final int b) {
        final int diff = a - b;
        return b + (diff & (diff >> 31));
    }

    public static int branchlessMax(final int a, final int b) {
        final int diff = a - b;
        return a - (diff & (diff >> 31));
    }

    public static int logicalNot(final int a) {
        return a ^ 1;
    }

    public static int indexOf(final int searchIn, final byte searchFor) {
        final int searchForInt = (int) searchFor;
        final int searchForMask = (searchForInt << 24) | (searchForInt << 16) | (searchForInt << 8) | searchForInt;

        final int xorResult = searchIn ^ searchForMask;
        final int searchInMasked = (xorResult - 0x01_01_01_01) & ~xorResult & 0x80_80_80_80;

        return (Integer.BYTES - 1) - (Integer.numberOfTrailingZeros(searchInMasked) / 8);
    }
}
