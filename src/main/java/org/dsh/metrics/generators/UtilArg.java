package org.dsh.metrics.generators;

public class UtilArg {
    static public int getIntArg(String[] args, String arg, int defaultValue) {
        String x = getArg(args, arg, defaultValue + "");
        return Integer.parseInt(x);
    }

    static public String getArg(String[] args, String arg, String defaultValue) {
        String match = "-" + arg + "=";
        for (String x : args) {
            if (x != null && x.startsWith(match)) {
                return x.substring(match.length());
            }
        }
        return defaultValue;
    }

}
