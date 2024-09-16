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
        final int searchForMask = (searchForInt << 24)
                | (searchForInt << 16)
                | (searchForInt << 8)
                | searchForInt;

        final int xorResult = searchIn ^ searchForMask;
        final int searchInMasked = (xorResult - 0x01010101) & ~xorResult & 0x80808080;

        return (Integer.BYTES - 1) - (Integer.numberOfTrailingZeros(searchInMasked) / Byte.SIZE);
    }

    public static int indexOf(final long searchIn, final byte searchFor) {
        final long searchForLong = (long) searchFor;
        final long searchForMask = (searchForLong << 56)
                | (searchForLong << 48)
                | (searchForLong << 40)
                | (searchForLong << 32)
                | (searchForLong << 24)
                | (searchForLong << 16)
                | (searchForLong << 8)
                | searchForLong;

        final long xorResult = searchIn ^ searchForMask;
        final long searchInMasked = (xorResult - 0x0101010101010101L) & ~xorResult & 0x8080808080808080L;

        return (Long.BYTES - 1) - (Long.numberOfTrailingZeros(searchInMasked) / Byte.SIZE);
    }
}
