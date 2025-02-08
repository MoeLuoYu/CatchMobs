package xyz.moeluoyu.catchmobs.Tasks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NMSVersionConverter {

    private static final Pattern VERSION_PATTERN = Pattern.compile("\\(MC: (\\d+\\.\\d+(?:\\.\\d+)?)\\)");

    public static String extractMinecraftVersion(String serverVersion) {
        Matcher matcher = VERSION_PATTERN.matcher(serverVersion);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String convertToNMSVersion(String mcVersion) {
        switch (mcVersion) {
            case "1.18":
            case "1.18.1":
            case "1.18.2":
                return "v1_18_R2";
            case "1.19":
            case "1.19.1":
            case "1.19.2":
            case "1.19.3":
            case "1.19.4":
                return "v1_19_R3";
            case "1.20":
            case "1.20.1":
            case "1.20.2":
            case "1.20.3":
            case "1.20.4":
                return "v1_20_R3";
            case "1.21":
            case "1.21.1":
                return "v1_21_R1";
            default:
                return null;
        }
    }
}