package nep.timeline.cirno.utils;

import com.topjohnwu.superuser.Shell;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Root package enumeration used by the native Android UI and PackageUtils. */
public final class RootPackageRepository {
    private static final Pattern USER = Pattern.compile("UserInfo\\{(\\d+):");

    private RootPackageRepository() { }

    public static Set<String> getManagedAppKeySet() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String user : run("pm list users")) {
            Matcher matcher = USER.matcher(user);
            if (!matcher.find()) continue;
            int userId;
            try { userId = Integer.parseInt(matcher.group(1)); }
            catch (NumberFormatException ignored) { continue; }
            for (String line : run("pm list packages --user " + userId)) {
                if (line.startsWith("package:")) {
                    String pkg = line.substring("package:".length()).trim();
                    if (!pkg.isEmpty()) result.add(pkg + "#" + userId);
                }
            }
        }
        if (result.isEmpty()) {
            for (String line : run("pm list packages")) {
                if (line.startsWith("package:")) {
                    String pkg = line.substring("package:".length()).trim();
                    if (!pkg.isEmpty()) result.add(pkg + "#0");
                }
            }
        }
        return result;
    }

    private static java.util.List<String> run(String command) {
        try {
            Shell.Result result = Shell.cmd(command).exec();
            return result.isSuccess() ? result.getOut() : java.util.Collections.emptyList();
        } catch (Throwable ignored) {
            return java.util.Collections.emptyList();
        }
    }
}
