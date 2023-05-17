package org.workcraft;

import org.workcraft.Version.Status;
import org.workcraft.plugins.builtin.settings.DebugCommonSettings;
import org.workcraft.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Info {

    private static final String TITLE = "Workcraft";
    private static final String SUBTITLE_1 = "A New Hope";
    private static final String SUBTITLE_2 = "Metastability Strikes Back";
    private static final String SUBTITLE_3 = "Return of the Hazard";
    private static final String SUBTITLE_4 = "Revenge of the Timing Assumption";

    private static final Version VERSION = new Version(3, 4, 1, Status.ALPHA);

    private static final int START_YEAR = 2006;
    private static final int CURRENT_YEAR = Calendar.getInstance().get(Calendar.YEAR);
    private static final String ORGANISATION = "Newcastle University";
    private static final String HOMEPAGE = "https://workcraft.org/";
    private static final String EMAIL = "support@workcraft.org";

    private static final Pattern EDITION_PATTERN = Pattern.compile("^WORKCRAFT_EDITION=\"(.+)\"$", Pattern.MULTILINE);
    private static final File RELEASE_FILE = new File("release");

    public static Version getVersion() {
        return VERSION;
    }

    public static String getTitle() {
        return TITLE + ' ' + Integer.toString(VERSION.major);
    }

    public static String getSubtitle() {
        switch (VERSION.major) {
        case 1: return SUBTITLE_1;
        case 2: return SUBTITLE_2;
        case 3: return SUBTITLE_3;
        case 4: return SUBTITLE_4;
        default: return "";
        }
    }

    private static String getEdition() {
        if (RELEASE_FILE.isFile() && RELEASE_FILE.exists()) {
            try {
                String text = FileUtils.readAllText(RELEASE_FILE);
                Matcher matcher = EDITION_PATTERN.matcher(text);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (IOException ignored) {
            }
        }
        return "";
    }

    public static String getFullTitle() {
        String edition = getEdition();
        String suffix = edition.isEmpty() ? "" : (" - " + edition);
        return getTitle() + " (" + getSubtitle() + "), version " + getVersion() + suffix;
    }

    public static String getJavaDescription() {
        return "JVM " + System.getProperty("java.version") + " [" + System.getProperty("java.home") + "]";
    }

    public static String getCopyright() {
        return "Copyright " + Integer.toString(START_YEAR) + '-' + Integer.toString(CURRENT_YEAR) + ' ' + ORGANISATION;
    }

    public static String getHomepage() {
        return HOMEPAGE;
    }

    public static String getEmail() {
        return EMAIL;
    }

    public static String getGeneratedByText(String prefix, String suffix) {
        String info = DebugCommonSettings.getShortExportHeader() ? getTitle() : getFullTitle();
        return prefix + "generated by " + info + suffix;
    }

}
