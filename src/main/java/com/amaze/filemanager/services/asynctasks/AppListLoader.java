package com.amaze.filemanager.services.asynctasks;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.content.AsyncTaskLoader;
import android.text.format.Formatter;

import com.amaze.filemanager.R;
import com.amaze.filemanager.ui.Layoutelements;
import com.amaze.filemanager.utils.FileListSorter;
import com.amaze.filemanager.utils.InterestingConfigChange;
import com.amaze.filemanager.utils.broadcast_receiver.PackageReceiver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by vishal on 23/2/17.
 *
 * Class loads all the packages installed
 */

public class AppListLoader extends AsyncTaskLoader<List<Layoutelements>> {

    private PackageManager packageManager;
    private PackageReceiver packageReceiver;
    private Context context;
    private List<Layoutelements> mApps;
    private int sortBy, asc;

    public AppListLoader(Context context, int sortBy, int asc) {
        super(context);

        this.context = context;
        this.sortBy = sortBy;
        this.asc = asc;

        /**
         * using global context because of the fact that loaders are supposed to be used
         * across fragments and activities
         */
        packageManager = getContext().getPackageManager();
    }

    @Override
    public List<Layoutelements> loadInBackground() {

        List<ApplicationInfo> apps = packageManager.getInstalledApplications(
                PackageManager.MATCH_UNINSTALLED_PACKAGES |
                        PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS);

        if (apps == null)
            apps = new ArrayList<>();

        mApps = new ArrayList<>(apps.size());

        for (ApplicationInfo object : apps) {
            Bundle metaData = object.metaData;
            if (metaData != null) {
                if (metaData.getString("Substratum_Parent") != null ||
                        metaData.getString("Substratum_IconPack") != null) {
                    continue;
                }
            }
            File sourceDir = new File(object.sourceDir);

            String label = object.loadLabel(packageManager).toString();

            mApps.add(new Layoutelements(new BitmapDrawable(context.getResources(),
                    BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_doc_apk_grid)),
                    label == null ? object.packageName : label, object.sourceDir,
                    object.packageName, object.flags + "", Formatter.formatFileSize(getContext(), sourceDir.length()),
                    sourceDir.length(), false, sourceDir.lastModified()+"", false));

            Collections.sort(mApps, new FileListSorter(0, sortBy, asc, false));
        }
        return mApps;
    }

    @Override
    public void deliverResult(List<Layoutelements> data) {
        if (isReset()) {

            if (data != null)
                onReleaseResources(data);
        }

        // preserving old data for it to be closed
        List<Layoutelements> oldData = mApps;
        mApps = data;
        if (isStarted()) {
            // loader has been started, if we have data, return immediately
            super.deliverResult(mApps);
        }

        // releasing older resources as we don't need them now
        if (oldData != null) {
            onReleaseResources(oldData);
        }
    }

    @Override
    protected void onStartLoading() {

        if (mApps != null) {
            // we already have the results, load immediately
            deliverResult(mApps);
        }

        if (packageReceiver != null) {
            packageReceiver = new PackageReceiver(this);
        }

        boolean didConfigChange = InterestingConfigChange.isConfigChanged(getContext().getResources());

        if (takeContentChanged() || mApps == null || didConfigChange) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    public void onCanceled(List<Layoutelements> data) {
        super.onCanceled(data);

        onReleaseResources(data);
    }

    @Override
    protected void onReset() {
        super.onReset();

        onStopLoading();

        // we're free to clear resources
        if (mApps != null) {
            onReleaseResources(mApps);
            mApps = null;
        }

        if (packageReceiver != null) {
            getContext().unregisterReceiver(packageReceiver);

            packageReceiver = null;
        }

        InterestingConfigChange.recycle();

    }

    /**
     * We would want to release resources here
     * List is nothing we would want to close
     * @param layoutelementsList
     */
    private void onReleaseResources(List<Layoutelements> layoutelementsList) {

    }
}
