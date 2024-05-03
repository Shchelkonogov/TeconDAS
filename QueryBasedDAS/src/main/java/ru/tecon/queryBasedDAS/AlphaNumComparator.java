package ru.tecon.queryBasedDAS;

import java.util.Comparator;

public class AlphaNumComparator implements Comparator<String> {

    private boolean isDigit(char ch) {
        return ((ch >= 48) && (ch <= 57));
    }

    /** Length of string is passed in for improved efficiency (only need to calculate it once) **/
    private String getChunk(String s, int sLength, int marker) {
        StringBuilder chunk = new StringBuilder();
        char c = s.charAt(marker);
        chunk.append(c);
        marker++;

        if (isDigit(c)) {
            while (marker < sLength) {
                c = s.charAt(marker);
                if (!isDigit(c)) {
                    break;
                }
                chunk.append(c);
                marker++;
            }
        } else {
            while (marker < sLength) {
                c = s.charAt(marker);
                if (isDigit(c)) {
                    break;
                }
                chunk.append(c);
                marker++;
            }
        }
        return chunk.toString();
    }

    public int compare(String o1, String o2) {
        if ((o1 == null) || (o2 == null)) {
            return 0;
        }

        int thisMarker = 0;
        int thatMarker = 0;
        int s1Length = o1.length();
        int s2Length = o2.length();

        while (thisMarker < s1Length && thatMarker < s2Length) {
            String thisChunk = getChunk(o1, s1Length, thisMarker);
            thisMarker += thisChunk.length();

            String thatChunk = getChunk(o2, s2Length, thatMarker);
            thatMarker += thatChunk.length();

            // If both chunks contain numeric characters, sort them numerically
            int result;
            if (isDigit(thisChunk.charAt(0)) && isDigit(thatChunk.charAt(0))) {
                // Simple chunk comparison by length.
                int thisChunkLength = thisChunk.length();
                result = thisChunkLength - thatChunk.length();
                // If equal, the first different number counts
                if (result == 0) {
                    for (int i = 0; i < thisChunkLength; i++) {
                        result = thisChunk.charAt(i) - thatChunk.charAt(i);
                        if (result != 0) {
                            return result;
                        }
                    }
                }
            } else {
                result = thisChunk.compareTo(thatChunk);
            }

            if (result != 0) {
                return result;
            }
        }

        return s1Length - s2Length;
    }
}