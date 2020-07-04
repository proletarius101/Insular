package org.securesystem.insular;

import android.app.SearchManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import com.oasisfeng.android.app.LifecycleActivity;
import com.oasisfeng.android.base.Scopes;
import com.oasisfeng.android.os.Loopers;
import org.securesystem.insular.analytics.Analytics;
import org.securesystem.insular.analytics.Analytics.Property;
import org.securesystem.insular.console.apps.AppListFragment;
import org.securesystem.insular.engine.IslandManager;
import org.securesystem.insular.mobile.BuildConfig;
import org.securesystem.insular.mobile.R;
import org.securesystem.insular.setup.SetupActivity;
import org.securesystem.insular.util.DeviceAdmins;
import org.securesystem.insular.util.DevicePolicies;
import org.securesystem.insular.util.Modules;
import org.securesystem.insular.util.Users;

import java.util.List;
import java.util.Optional;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

public class MainActivity extends LifecycleActivity {

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (! Users.isOwner()) {
			if (Users.isProfileManagedByIsland()) {    // Should generally not run in profile, unless the managed profile provision is interrupted or manually provision is not complete.
				onCreateInProfile();
				finish();
			} else startSetupWizard();		// Running in non-primary user or profile not managed by Island. TODO: Proper handling?
			return;
		}
		if (mIsDeviceOwner = new DevicePolicies(this).isProfileOrDeviceOwnerOnCallingUser()) {
			startMainUi(savedInstanceState);	// As device owner, always show main UI.
			return;
		}
		final UserHandle profile = Users.profile;
		if (profile == null) {					// Nothing setup yet
			Log.i(TAG, "Profile not setup yet");
			startSetupWizard();
			return;
		}

		if (! DevicePolicies.isProfileOwner(this, profile)) {	// Profile without owner, probably caused by provisioning interrupted before device-admin is activated.
			final Optional<ComponentName> owner = DevicePolicies.getProfileOwnerAsUser(this, profile);
			if (owner == null) {
				Log.w(TAG, "Not profile owner");
				startMainUi(savedInstanceState);
			} else if (! owner.isPresent()) {
				Log.w(TAG, "Profile without owner");
				if (IslandManager.launchApp(this, getPackageName(), profile)) finish();	// Try starting Island in profile to finish the provisioning.
				else startSetupWizard();		// Cannot resume the provisioning, probably this profile is not created by us, go ahead with normal setup.
			} else {			// Profile is not owned by us, show setup wizard.
				startSetupWizard();
			}
		} else {
			final LauncherApps launcher_apps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
			final List<LauncherActivityInfo> our_activities_in_launcher;
			if (launcher_apps != null && ! (our_activities_in_launcher = launcher_apps.getActivityList(getPackageName(), profile)).isEmpty()
					&& our_activities_in_launcher.get(0).getComponentName().getClassName().equals(MainActivity.class.getName())) {
				// Main activity is left enabled, probably due to pending post-provisioning in manual setup. Some domestic ROMs may block implicit broadcast, causing ACTION_USER_INITIALIZE being dropped.
				
				Log.w(TAG, "Setup in Island is not complete, continue it now.");
				try {
					launcher_apps.startMainActivity(our_activities_in_launcher.get(0).getComponentName(), profile, null, null);
				} catch (final RuntimeException e) {
					Analytics.$().logAndReport(TAG, "Error starting self in profile " + Users.toId(profile), e);
					startSetupWizard();
				}
				finish();
				return;
			}
			startMainUi(savedInstanceState);
		}
	}

	private void onCreateInProfile() {
		final DevicePolicies policies = new DevicePolicies(this);
		if (! policies.invoke(DevicePolicyManager::isAdminActive)) {
			
			startActivity(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
					.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceAdmins.getComponentName(this))
					.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.dialog_reactivate_message)));
			return;
		}
		final PackageManager pm = getPackageManager();
		final List<ResolveInfo> resolve = pm.queryBroadcastReceivers(new Intent(Intent.ACTION_USER_INITIALIZE).setPackage(Modules.MODULE_ENGINE), 0);
		if (! resolve.isEmpty()) {
			Log.w(TAG, "Manual provisioning is pending, resume it now.");
			
			final ActivityInfo receiver = resolve.get(0).activityInfo;
			sendBroadcast(new Intent().setComponent(new ComponentName(receiver.packageName, receiver.name)));
		} else {    // Receiver disabled but launcher entrance is left enabled. The best bet is just disabling the launcher entrance. No provisioning attempt any more.
			Log.w(TAG, "Manual provisioning is finished, but launcher activity is still left enabled. Disable it now.");
			
			pm.setComponentEnabledSetting(new ComponentName(this, getClass()), COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
		}
	}

	private void startMainUi(final Bundle savedInstanceState) {
		setContentView(R.layout.activity_main);
		if (savedInstanceState != null) return;
		final AppListFragment fragment = new AppListFragment();
		final Intent intent = getIntent();
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			final Bundle arguments = new Bundle();
			arguments.putString(SearchManager.QUERY, intent.getStringExtra(SearchManager.QUERY));
			final UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
			if (user != null) arguments.putParcelable(Intent.EXTRA_USER, user);
			fragment.setArguments(arguments);
		}
		getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
		performOverallAnalyticsIfNeeded();
	}

	private void startSetupWizard() {
		startActivity(new Intent(this, SetupActivity.class));
		performOverallAnalyticsIfNeeded();
		finish();
	}

	private void performOverallAnalyticsIfNeeded() {
		if (! BuildConfig.DEBUG && ! Scopes.boot(this).mark("overall_analytics")) return;
		Loopers.addIdleTask(() -> {
			final Analytics analytics = Analytics.$();
			analytics.setProperty(Property.DeviceOwner, mIsDeviceOwner);
			analytics.setProperty(Property.RemoteConfigAvailable, Config.isRemote());
		});
	}

	private boolean mIsDeviceOwner;

	private static final String TAG = "Island.Main";
}