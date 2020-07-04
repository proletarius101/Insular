package org.securesystem.insular;


import org.securesystem.insular.shared.BuildConfig;


/**
 * Remotely configurable values, (default values are defined in config_defaults.xml)
 *
 * Created by Oasis on 2016/5/26.
 */
public enum Config {


	/* All keys must be consistent with config_defaults.xml */
	IS_REMOTE("is_remote"),
	URL_FAQ("url_faq"),
	URL_SETUP("url_setup"),
	URL_SETUP_GOD_MODE("url_setup_god_mode"),
	URL_SETUP_TROUBLESHOOTING("url_setup_trouble"),
	URL_FILE_SHUTTLE("url_file_shuttle"),
	PERMISSION_REQUEST_ALLOWED_APPS("permission_allowed_apps");


	public String get() {
        switch (key) {
            // From config_defaults.xml
            case "url_faq":
                return "https://island.oasisfeng.com/faq";
            case "url_setup_trouble":
                return "https://island.oasisfeng.com/faq";
            case "url_setup":
                return "https://island.oasisfeng.com/setup.html";
            case "url_setup_god_mode":
                return "https://island.oasisfeng.com/setup.html#manual-setup-for-island-in-god-mode";
            case "url_file_shuttle":
                return "https://island.oasisfeng.com/files";
            case "url_coolapk":
                return "https://www.coolapk.com/";
            case "permission_allowed_apps":
                return "com.oasisfeng.greenify,com.oasisfeng.nevo";
            default:
                return "";
        }
	}


	public static boolean isRemote() {
		return false;
	}


	Config(final String key) { this.key = key; }


	private final String key;
}