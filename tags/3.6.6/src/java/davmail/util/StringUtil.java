/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package davmail.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Various string handling methods
 */
public final class StringUtil {
    private StringUtil() {
    }

    /**
     * Return the sub string between startDelimiter and endDelimiter or null.
     *
     * @param value          String value
     * @param startDelimiter start delimiter
     * @param endDelimiter   end delimiter
     * @return token value
     */
    public static String getToken(String value, String startDelimiter, String endDelimiter) {
        String token = null;
        if (value != null) {
            int startIndex = value.indexOf(startDelimiter);
            if (startIndex >= 0) {
                startIndex += startDelimiter.length();
                int endIndex = value.indexOf(endDelimiter, startIndex);
                if (endIndex >= 0) {
                    token = value.substring(startIndex, endIndex);
                }
            }
        }
        return token;
    }

    /**
     * Return the sub string between startDelimiter and endDelimiter or null,
     * look for last token in string.
     *
     * @param value          String value
     * @param startDelimiter start delimiter
     * @param endDelimiter   end delimiter
     * @return token value
     */
    public static String getLastToken(String value, String startDelimiter, String endDelimiter) {
        String token = null;
        if (value != null) {
            int startIndex = value.lastIndexOf(startDelimiter);
            if (startIndex >= 0) {
                startIndex += startDelimiter.length();
                int endIndex = value.indexOf(endDelimiter, startIndex);
                if (endIndex >= 0) {
                    token = value.substring(startIndex, endIndex);
                }
            }
        }
        return token;
    }

    /**
     * Return the sub string between startDelimiter and endDelimiter with newToken.
     *
     * @param value          String value
     * @param startDelimiter start delimiter
     * @param endDelimiter   end delimiter
     * @param newToken       new token value
     * @return token value
     */
    public static String replaceToken(String value, String startDelimiter, String endDelimiter, String newToken) {
        String result = null;
        if (value != null) {
            int startIndex = value.indexOf(startDelimiter);
            if (startIndex >= 0) {
                startIndex += startDelimiter.length();
                int endIndex = value.indexOf(endDelimiter, startIndex);
                if (endIndex >= 0) {
                    result = value.substring(0, startIndex) + newToken + value.substring(endIndex);
                }
            }
        }
        return result;
    }

    /**
     * Join values with given separator.
     *
     * @param values    value set
     * @param separator separator
     * @return joined values
     */
    public static String join(Set<String> values, String separator) {
        if (values != null && !values.isEmpty()) {
            StringBuilder result = new StringBuilder();
            for (String value : values) {
                if (result.length() > 0) {
                    result.append(separator);
                }
                result.append(value);
            }
            return result.toString();
        } else {
            return null;
        }
    }

    private static final Pattern AMP_PATTERN = Pattern.compile("&");
    private static final Pattern LT_PATTERN = Pattern.compile("<");
    private static final Pattern GT_PATTERN = Pattern.compile(">");

    private static final Pattern ENCODED_AMP_PATTERN = Pattern.compile("&amp;");
    private static final Pattern ENCODED_LT_PATTERN = Pattern.compile("&lt;");
    private static final Pattern ENCODED_GT_PATTERN = Pattern.compile("&gt;");

    /**
     * Need to encode xml for iCal
     *
     * @param name decoded name
     * @return name encoded name
     */
    public static String xmlEncode(String name) {
        String result = name;
        if (name.indexOf('&') >= 0) {
            result = AMP_PATTERN.matcher(result).replaceAll("&amp;");
        }
        if (name.indexOf('<') >= 0) {
            result = LT_PATTERN.matcher(result).replaceAll("&lt;");
        }
        if (name.indexOf('>') >= 0) {
            result = GT_PATTERN.matcher(result).replaceAll("&gt;");
        }
        return result;
    }

    /**
     * Need to decode xml for iCal
     *
     * @param name encoded name
     * @return name decoded name
     */
    public static String xmlDecode(String name) {
        String result = name;
        if (name.indexOf("&amp;") >= 0) {
            result = ENCODED_AMP_PATTERN.matcher(result).replaceAll("&");
        }
        if (name.indexOf("&lt;") >= 0) {
            result = ENCODED_LT_PATTERN.matcher(result).replaceAll("<");
        }
        if (name.indexOf("&gt;") >= 0) {
            result = ENCODED_GT_PATTERN.matcher(result).replaceAll(">");
        }
        return result;
    }

}