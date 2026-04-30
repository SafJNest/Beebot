package com.safjnest.lol.utils;

import java.util.Map;

public final class AugmentUtils {

    private AugmentUtils() {}

    public static String format(String tooltip, Map<String, String> dataValues) {
        if (tooltip == null || tooltip.isEmpty()) return "";
        String stripped = stripTags(tooltip);
        return resolvePlaceholders(stripped, dataValues == null ? Map.of() : dataValues);
    }

    private static String stripTags(String desc) {
        for (int i = 0; i < desc.length(); i++) {
            if (desc.charAt(i) != '<') continue;

            for (int j = i + 1; j < desc.length(); j++) {
                if (desc.charAt(j) != '>') continue;

                String key = desc.substring(i + 1, j);
                if (key.startsWith("/")) key = key.substring(1);

                if (desc.charAt(i + 1) == '/') desc = desc.replace("</" + key + ">", "");
                else                          desc = desc.replace("<"  + key + ">", "");
                break;
            }
        }
        return desc;
    }

    private static String resolvePlaceholders(String desc, Map<String, String> dataValues) {
        for (int i = 0; i < desc.length(); i++) {
            if (desc.charAt(i) != '@') continue;

            String result = "";
            String keya = "";
            String op = "";
            for (int j = i + 1; j < desc.length(); j++) {
                if (desc.charAt(j) == '*') {
                    String key = desc.substring(i + 1, j);
                    for (int k = j + 1; k < desc.length(); k++) {
                        if (desc.charAt(k) != '@') continue;
                        keya = desc.substring(i + 1, j);
                        op   = desc.substring(j + 1, k);
                        result = applyOperator(desc.charAt(j), op, dataValues.get(key));
                        break;
                    }
                } else if (desc.charAt(j) == '@') {
                    if (result.isEmpty()) {
                        String key = desc.substring(i + 1, j);
                        String value = dataValues.get(key);
                        desc = desc.replace("@" + key + "@", value);
                    } else {
                        desc = desc.replace("@" + keya + "*" + op + "@", result);
                    }
                    break;
                }
            }
        }
        return desc;
    }

    private static String applyOperator(char operator, String operandLiteral, String dataValue) {
        if (operandLiteral == null || dataValue == null) return "";
        try {
            double operand = Double.parseDouble(operandLiteral);
            double data    = Double.parseDouble(dataValue);
            return switch (operator) {
                case '+' -> Long.toString(Math.round(operand + data));
                case '-' -> Long.toString(Math.round(operand - data));
                case '*' -> Long.toString(Math.round(operand * data));
                case '/' -> Long.toString(Math.round(operand / data));
                default -> "";
            };
        } catch (NumberFormatException ignored) {
            return "";
        }
    }
}
