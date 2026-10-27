package ru.my.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Поиск установленных приложений, их названий, баннеров и иконок. */
final class Apps {

    static final class Entry {
        final String pkg;
        final CharSequence label;
        final Drawable image;
        final boolean isBanner;

        Entry(String pkg, CharSequence label, Drawable image, boolean isBanner) {
            this.pkg = pkg;
            this.label = label;
            this.image = image;
            this.isBanner = isBanner;
        }
    }

    private Apps() {}

    private static Intent query(String category) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(category);
        return i;
    }

    /** Все приложения, которые можно запустить, по алфавиту. Сначала TV-версии. */
    static List<Entry> all(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, ResolveInfo> byPkg = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(query(cat), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName())) continue;
                if (!byPkg.containsKey(pkg)) byPkg.put(pkg, ri);
            }
        }
        List<Entry> out = new ArrayList<>();
        for (ResolveInfo ri : byPkg.values()) out.add(toEntry(pm, ri));
        Collator collator = Collator.getInstance(new Locale("ru"));
        out.sort((a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        return out;
    }

    /** Одно приложение по имени пакета, или null, если его нет. */
    static Entry forPackage(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = query(cat).setPackage(pkg);
            List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
            if (!list.isEmpty()) return toEntry(pm, list.get(0));
        }
        return null;
    }

    static boolean isInstalled(Context c, String pkg) {
        return launchIntent(c.getPackageManager(), pkg) != null;
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** Только название: картинки приложений лаунчер не показывает и в память не грузит. */
    private static Entry toEntry(PackageManager pm, ResolveInfo ri) {
        ActivityInfo ai = ri.activityInfo;
        return new Entry(ai.packageName, ri.loadLabel(pm), null, false);
    }
}
