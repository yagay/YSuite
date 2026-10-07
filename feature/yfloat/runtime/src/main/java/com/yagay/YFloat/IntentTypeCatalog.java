package com.yagay.YFloat;

import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;

import java.util.List;

/** Standard intent families YFloat can discover without asking the user to type parameters. */
public final class IntentTypeCatalog {
    public static final class Spec {
        public final String type;
        public final String title;
        public final String description;

        Spec(String type, String title, String description) {
            this.type = type;
            this.title = title;
            this.description = description;
        }

        public Intent probeIntent() {
            return switch (type) {
                case CustomMenuActionStore.TYPE_LAUNCH -> new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER);
                case CustomMenuActionStore.TYPE_SEND_TEXT -> new Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "YFloat");
                case CustomMenuActionStore.TYPE_PROCESS_TEXT -> new Intent(Intent.ACTION_PROCESS_TEXT)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_PROCESS_TEXT, "YFloat")
                        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
                case CustomMenuActionStore.TYPE_VIEW_WEB -> new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://example.com"));
                case CustomMenuActionStore.TYPE_WEB_SEARCH -> new Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra(SearchManager.QUERY, "YFloat");
                case CustomMenuActionStore.TYPE_DIAL -> new Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:10086"));
                case CustomMenuActionStore.TYPE_SMS -> new Intent(Intent.ACTION_SENDTO,
                        Uri.parse("smsto:10086"));
                case CustomMenuActionStore.TYPE_EMAIL -> new Intent(Intent.ACTION_SENDTO,
                        Uri.parse("mailto:test@example.com"));
                case CustomMenuActionStore.TYPE_MAP -> new Intent(Intent.ACTION_VIEW,
                        Uri.parse("geo:0,0?q=YFloat"));
                case CustomMenuActionStore.TYPE_TRANSLATE -> new Intent("android.intent.action.TRANSLATE")
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "YFloat");
                case CustomMenuActionStore.TYPE_VIEW_TEXT -> new Intent(Intent.ACTION_VIEW)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "YFloat");
                default -> new Intent();
            };
        }
    }

    private static final List<Spec> ALL = List.of(
            new Spec(CustomMenuActionStore.TYPE_LAUNCH, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_7fe957b4e334), com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_b5ae3bff2e4d)),
            new Spec(CustomMenuActionStore.TYPE_SEND_TEXT, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_f258fec45ef0), "ACTION_SEND · text/plain"),
            new Spec(CustomMenuActionStore.TYPE_PROCESS_TEXT, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_bacfe3e73295), "ACTION_PROCESS_TEXT"),
            new Spec(CustomMenuActionStore.TYPE_VIEW_WEB, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_be4f13a31f24), "ACTION_VIEW · http/https"),
            new Spec(CustomMenuActionStore.TYPE_WEB_SEARCH, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_d9be509376b2), "ACTION_WEB_SEARCH"),
            new Spec(CustomMenuActionStore.TYPE_DIAL, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_ea0eca2434fa), "ACTION_DIAL"),
            new Spec(CustomMenuActionStore.TYPE_SMS, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_943ba3b6b72e), "ACTION_SENDTO · sms"),
            new Spec(CustomMenuActionStore.TYPE_EMAIL, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_467d3205e6b0), "ACTION_SENDTO · mail"),
            new Spec(CustomMenuActionStore.TYPE_MAP, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_3247f3fc702a), "ACTION_VIEW · geo"),
            new Spec(CustomMenuActionStore.TYPE_TRANSLATE, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_6c51b8bbb258), "ACTION_TRANSLATE"),
            new Spec(CustomMenuActionStore.TYPE_VIEW_TEXT, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_4043fbe3b425), "ACTION_VIEW · text/plain")
    );

    public static List<Spec> all() { return ALL; }

    public static Spec find(String type) {
        for (Spec s : ALL) if (s.type.equals(type)) return s;
        return null;
    }

    private IntentTypeCatalog() {}
}
