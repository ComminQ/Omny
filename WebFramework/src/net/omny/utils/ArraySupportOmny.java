package net.omny.utils;

public class ArraySupportOmny {

    public static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    /**
     * Computes a new array length given an array's current length, a minimum growth
     * amount, and a preferred growth amount. The computation is done in an
     * overflow-safe
     * fashion.
     *
     * This method is used by objects that contain an array that might need to be
     * grown
     * in order to fulfill some immediate need (the minimum growth amount) but would
     * also
     * like to request more space (the preferred growth amount) in order to
     * accommodate
     * potential future needs. The returned length is usually clamped at the soft
     * maximum
     * length in order to avoid hitting the JVM implementation limit. However, the
     * soft
     * maximum will be exceeded if the minimum growth amount requires it.
     *
     * If the preferred growth amount is less than the minimum growth amount, the
     * minimum growth amount is used as the preferred growth amount.
     *
     * The preferred length is determined by adding the preferred growth amount to
     * the
     * current length. If the preferred length does not exceed the soft maximum
     * length
     * (SOFT_MAX_ARRAY_LENGTH) then the preferred length is returned.
     *
     * If the preferred length exceeds the soft maximum, we use the minimum growth
     * amount. The minimum required length is determined by adding the minimum
     * growth
     * amount to the current length. If the minimum required length exceeds
     * Integer.MAX_VALUE,
     * then this method throws OutOfMemoryError. Otherwise, this method returns the
     * greater of
     * the soft maximum or the minimum required length.
     *
     * Note that this method does not do any array allocation itself; it only does
     * array
     * length growth computations. However, it will throw OutOfMemoryError as noted
     * above.
     *
     * Note also that this method cannot detect the JVM's implementation limit, and
     * it
     * may compute and return a length value up to and including Integer.MAX_VALUE
     * that
     * might exceed the JVM's implementation limit. In that case, the caller will
     * likely
     * attempt an array allocation with that length and encounter an
     * OutOfMemoryError.
     * Of course, regardless of the length value returned from this method, the
     * caller
     * may encounter OutOfMemoryError if there is insufficient heap to fulfill the
     * request.
     *
     * @param oldLength  current length of the array (must be nonnegative)
     * @param minGrowth  minimum required growth amount (must be positive)
     * @param prefGrowth preferred growth amount
     * @return the new array length
     * @throws OutOfMemoryError if the new length would exceed Integer.MAX_VALUE
     */
    public static int newLength(int oldLength, int minGrowth, int prefGrowth) {
        // preconditions not checked because of inlining
        // assert oldLength >= 0
        // assert minGrowth > 0

        int prefLength = oldLength + Math.max(minGrowth, prefGrowth); // might overflow
        if (0 < prefLength && prefLength <= SOFT_MAX_ARRAY_LENGTH) {
            return prefLength;
        } else {
            // put code cold in a separate method
            return hugeLength(oldLength, minGrowth);
        }
    }

    private static int hugeLength(int oldLength, int minGrowth) {
        int minLength = oldLength + minGrowth;
        if (minLength < 0) { // overflow
            throw new OutOfMemoryError(
                    "Required array length " + oldLength + " + " + minGrowth + " is too large");
        } else if (minLength <= SOFT_MAX_ARRAY_LENGTH) {
            return SOFT_MAX_ARRAY_LENGTH;
        } else {
            return minLength;
        }
    }

}