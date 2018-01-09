import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.charset.Charset;

public final class CheckTranslations {

    private static boolean debug = false;
    private static boolean plurals = false;
    private static boolean empty = false;
    private static boolean remove = false;
    private static int checks = 0;
    private static int matches = 0;
    private static int changes = 0;
    private static Pattern p, pb, pe, e, o;

    /**
     * Search translated strings.xml files for empty item / plural tags
     * and remove them.
     * @param args directories which contain string.xml files (in any subdirectory)
     *             -e option to find all empty string tags
     *             -p option to find all empty plurals and item tags
     *             -r option to remove all occurrences from the files
     *             -d option to see more details
     */
    public static void main(String[] args) {
        if (args.length < 1 || (args[0].equals("-d") && args.length < 2)) {  
            System.out.println("Not enough arguments");
            return;
        }
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d":
                    debug = true;
                    break;
                case "-p":
                    plurals = true;
                    break;
                case "-e":
                    empty = true;
                    break;
                case "-r":
                    remove = true;
                    break;
            }
        }
        
        if (!plurals && !empty) {
            plurals = true;
            empty = true;
        }

        p = Pattern.compile("(<item quantity=\")(zero|one|two|three|few|many|other)(\"></item>|\"/>)");
        pb = Pattern.compile("(<plurals[\\sa-zA-Z=\"]*>)");
        pe = Pattern.compile("(</plurals>)");
        e = Pattern.compile("(<string[\\sa-z_\\\"=]*)((><\\/string>|\\/>){1})");
        o = Pattern.compile("(<item quantity=\"other\">)[^</>]*(<\\/item>)");

        for (int i = 0; i < args.length; i++) {
            if (!args[i].equals("-d") && !args[i].equals("-p") && !args[i].equals("-e") && !args[i].equals("-r")) {
                File f = new File(args[i]);
                if (f.exists() && !f.isDirectory()) {
                    checkFile(f);
                } else if (f.isDirectory()) {
                    checkFiles(f.listFiles());
                } else {
                    System.out.println("'" + args[i] + "' does not exist!");
                }
            }
        }

        System.out.println(checks + " files were checked.");
        System.out.println(matches + " corrupt lines detected.");
        if (remove) {
            System.out.println(matches + " corrupt lines removed and " + changes + " lines fixed.");
        }
    }


    private static void checkFiles(File[] f) {
        for (int i = 0; i < f.length; i++) {
            if (f[i].exists() && !f[i].isDirectory()) {
                if (f[i].toString().contains("strings.xml")) {
                    checkFile(f[i]);
                }
            } else if (f[i].isDirectory()) {
                checkFiles(f[i].listFiles());
            }
        }
    }

    private static void checkFile(File f) {
        // Do not check our original English strings to cause no unwanted changes
        // Btw. there should not be empty plural/item tags
        if (f.toString().contains("values/strings.xml")) {
            return;
        }
        if (debug) System.out.println("Checking " + f.toString());
        checks++;


        List<String> lines = new ArrayList<String>();
        boolean checkFailed = false;
        boolean otherDetected = false;
        boolean inPlurals = false;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            int ln = 0;
            while ((line = br.readLine()) != null) {
                ln++;
                if (plurals && p.matcher(line).find()) {
                    matches++;
                    if (debug) System.out.println("    Line " + ln + " was " + ((remove) ? "removed" : "detected") + ": '" + line + "'");
                    checkFailed = true;
                } else if (empty && e.matcher(line).find()) {
                    matches++;
                    checkFailed = true;
                    if (debug) System.out.println("    Line " + ln + " was " + ((remove) ? "removed" : "detected") + ": '" + line + "'");
                } else {
                    if (remove) lines.add(line);
                }
            }
            br.close();
            int pluralsLine = 0;
            for (int i = 0; i < lines.size(); i++) {
                if (o.matcher(lines.get(i)).find()) {
                    otherDetected = true;
                }
                if (plurals && pb.matcher(lines.get(i)).find()) {
                    inPlurals = true;
                    pluralsLine = i;
                } else if (plurals && pe.matcher(lines.get(i)).find()) {
                    inPlurals = false;
                    if (!otherDetected) {
                        boolean b = false;
                        check: for(int j = pluralsLine; j < i; j++) {
                            if (lines.get(j).contains("many")) {
                                b = true;
                                pluralsLine = j;
                                break check;
                            }
                        }
                        if (remove && b) {
                            if (debug) System.out.println("    Line " + (pluralsLine + 1) + " was " + ((remove) ? "changed" : "detected") + ": '" + lines.get(pluralsLine) + "'");
                            lines.set(pluralsLine, lines.get(pluralsLine).replace("many", "other"));
                            changes++;
                            checkFailed = true;
                        } else if (debug) {
                            if (debug) System.out.println("    WARNING: Line " + (i + 1) + " - No <item quantity=\"other\"> found!");
                        }
                    }
                    otherDetected = false;
                }
                 
            }
            if (remove && checkFailed) {
                Files.write(f.toPath(), lines, Charset.forName("UTF-8"));
            }
        } catch (IOException e) {
            System.out.println(e);
        }
    }
}

