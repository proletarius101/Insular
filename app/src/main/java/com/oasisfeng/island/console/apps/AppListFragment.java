package com.oasisfeng.island.console.apps;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.databinding.Observable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;

import com.oasisfeng.android.service.Services;
import com.oasisfeng.common.app.AppListProvider;
import com.oasisfeng.island.TempDebug;
import com.oasisfeng.island.data.IslandAppInfo;
import com.oasisfeng.island.data.IslandAppListProvider;
import com.oasisfeng.island.engine.IIslandManager;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.guide.UserGuide;
import com.oasisfeng.island.mobile.BR;
import com.oasisfeng.island.mobile.BuildConfig;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.mobile.databinding.AppListBinding;
import com.oasisfeng.island.model.AppListViewModel;
import com.oasisfeng.island.model.GlobalStatus;
import com.oasisfeng.island.settings.SettingsActivity;
import com.oasisfeng.island.shuttle.ShuttleContext;
import com.oasisfeng.island.shuttle.ShuttleServiceConnection;

import java.util.Collection;

/** The main UI - App list */
public class AppListFragment extends Fragment {

	@Override public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		final Activity activity = getActivity();
		mShuttleContext = new ShuttleContext(activity);
		mViewModel = new AppListViewModel();
		mViewModel.mProfileController = IslandManager.NULL;
		mUserGuide = new UserGuide(activity, mViewModel);
		IslandAppListProvider.getInstance(activity).registerObserver(mAppChangeObserver);
	}

	@Override public void onStart() {
		super.onStart();
		if (GlobalStatus.hasProfile() && ! Services.bind(mShuttleContext, IIslandManager.class, mServiceConnection))
			Toast.makeText(getActivity(), "Error opening Island", Toast.LENGTH_LONG).show();
	}

	@Override public void onStop() {
		mViewModel.mProfileController = IslandManager.NULL;
		if (GlobalStatus.hasProfile()) try {
			mShuttleContext.unbindService(mServiceConnection);
		} catch (final RuntimeException e) { Log.e(TAG, "Unexpected exception in unbinding", e); }
		mViewModel.clearSelection();
		super.onStop();
	}

	@Override public void onDestroy() {
		IslandAppListProvider.getInstance(getActivity()).unregisterObserver(mAppChangeObserver);
		mViewModel.removeOnPropertyChangedCallback(onPropertyChangedCallback);
		super.onDestroy();
	}

	// Use ShuttleServiceConnection to connect to remote service in profile via ServiceShuttle (see also MainActivity.bindService)
	private final ServiceConnection mServiceConnection = new ShuttleServiceConnection() {
		@Override public void onServiceConnected(final IBinder service) {
			mViewModel.mProfileController = IIslandManager.Stub.asInterface(service);
			Log.d(TAG, "Service connected");
		}

		@Override public void onServiceDisconnected() {
			mViewModel.mProfileController = IslandManager.NULL;
		}
	};

	AppListProvider.PackageChangeObserver<IslandAppInfo> mAppChangeObserver = new AppListProvider.PackageChangeObserver<IslandAppInfo>() {

		@Override public void onPackageUpdate(final Collection<IslandAppInfo> apps) {
			Log.i(TAG, "Package updated: " + apps);
			mViewModel.onPackagesUpdate(apps);
// TODO
//			Snackbars.make(mBinding.getRoot(), getString(R.string.dialog_add_shortcut, app.getLabel()),
//					Snackbars.withAction(android.R.string.ok, v -> AppLaunchShortcut.createOnLauncher(activity, pkg))).show();
			invalidateOptionsMenu();
		}

		@Override public void onPackageRemoved(final Collection<IslandAppInfo> apps) {
			Log.i(TAG, "Package removed: " + apps);
			mViewModel.onPackagesRemoved(apps);
			invalidateOptionsMenu();
		}
	};

	private final Observable.OnPropertyChangedCallback onPropertyChangedCallback = new Observable.OnPropertyChangedCallback() {
		@Override public void onPropertyChanged(final Observable observable, final int var) {
			if (var == BR.selection) invalidateOptionsMenu();
		}
	};

	private void invalidateOptionsMenu() {
		final Activity activity = getActivity();
		if (activity != null) activity.invalidateOptionsMenu();
	}

	@Nullable @Override public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final @Nullable Bundle saved_state) {
		final Activity activity = getActivity();
		final AppListBinding binding = AppListBinding.inflate(inflater, container, false);
		binding.setApps(mViewModel);
		binding.setGuide(mUserGuide);
		mViewModel.attach(activity, binding.details.toolbar.getMenu(), saved_state);
		mViewModel.addOnPropertyChangedCallback(onPropertyChangedCallback);

		if (! Services.bind(activity, IIslandManager.class, mIslandManagerConnection = new ServiceConnection() {
			@Override public void onServiceConnected(final ComponentName name, final IBinder service) {
				mViewModel.setOwnerController(IIslandManager.Stub.asInterface(service));
			}

			@Override public void onServiceDisconnected(final ComponentName name) {}
		})) throw new IllegalStateException("Module engine not installed");

		activity.setActionBar(binding.appbar);
		final ActionBar actionbar = activity.getActionBar();
		if (actionbar != null) actionbar.setDisplayShowTitleEnabled(false);
		binding.filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
				if (getActivity() == null) return;
				mViewModel.setFilterPrimaryChoice(position);
			}
			@Override public void onNothingSelected(final AdapterView<?> parent) {}
		});
		binding.executePendingBindings();		// This ensures all view state being fully restored
		return binding.getRoot();
	}

	@Override public void onDestroyView() {
		if (mIslandManagerConnection != null) {
			getActivity().unbindService(mIslandManagerConnection);
			mIslandManagerConnection = null;
		}
		super.onDestroyView();
	}

	@Override public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		inflater.inflate(R.menu.main_actions, menu);
	}

	@Override public void onPrepareOptionsMenu(final Menu menu) {
		final MenuItem.OnMenuItemClickListener tip = mUserGuide.getAvailableTip();
		menu.findItem(R.id.menu_tip).setVisible(tip != null).setOnMenuItemClickListener(tip);
		menu.findItem(R.id.menu_show_system).setChecked(mViewModel.areSystemAppsIncluded());
		if (BuildConfig.DEBUG) menu.findItem(R.id.menu_test).setVisible(true);
	}

	@Override public boolean onOptionsItemSelected(final MenuItem item) {
		final int id = item.getItemId();
		if (id == R.id.menu_show_system) {
			final boolean should_include = ! item.isChecked();
			mViewModel.onFilterHiddenSysAppsInclusionChanged(should_include);
			item.setChecked(should_include);    // Toggle the checked state
		} else if (id == R.id.menu_settings) startActivity(new Intent(getActivity(), SettingsActivity.class));
		else if (id == R.id.menu_test) TempDebug.run(getActivity());
		else return super.onOptionsItemSelected(item);
		return true;
	}

	@Override public void onSaveInstanceState(final Bundle out_state) {
		super.onSaveInstanceState(out_state);
		mViewModel.onSaveInstanceState(out_state);
	}

	/** Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes). */
	public AppListFragment() {}

	private AppListViewModel mViewModel;
	private UserGuide mUserGuide;
	private ShuttleContext mShuttleContext;
	private ServiceConnection mIslandManagerConnection;

	private static final String TAG = "Island.AppsUI";
}
