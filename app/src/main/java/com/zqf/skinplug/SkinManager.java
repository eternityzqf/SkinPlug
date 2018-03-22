package com.zqf.skinplug;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.AsyncTask;

import com.zqf.skinplug.attr.SkinView;
import com.zqf.skinplug.callback.ISkinChangedListener;
import com.zqf.skinplug.callback.ISkinChangingCallback;
import com.zqf.skinplug.util.PrefUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by zqf on 2018/3/21.
 */

public class SkinManager {

    private static SkinManager sInstance = null;
    private Context mContext;
    private ResourcesManage mResourcesManage;
    private Map<ISkinChangedListener, List<SkinView>> mSkinViewMaps = new HashMap<>();
    private List<ISkinChangedListener> mSkinListeners = new ArrayList<>();
    private PrefUtil mPrefUtil;
    private String mCurentPath;
    private String mCurentPkg;
    private String mSuffix;

    public SkinManager() {

    }

    public static SkinManager getsInstance() {
        if (sInstance == null) {
            synchronized (SkinManager.class) {
                if (sInstance == null) {
                    sInstance = new SkinManager();
                }
            }
        }
        return sInstance;
    }

    //初始化
    public void init(Context context) {
        mContext = context.getApplicationContext();
        mPrefUtil = new PrefUtil(mContext);
        try {
            String plugPath = mPrefUtil.getSkinPlug();
            String plugPkg = mPrefUtil.getSkinPkg();
            mSuffix = mPrefUtil.getSuffix();
            File file = new File(plugPath);
            if (file.exists()) {
                loadskinpkg(plugPath, plugPkg);
            }
            mCurentPath = plugPath;
            mCurentPkg = plugPkg;
        } catch (Exception e) {
            e.printStackTrace();
            mPrefUtil.clear();
        }
    }

    public ResourcesManage getResourcesManage() {
        if (!userSkinPlug()) {
            return new ResourcesManage(mContext.getResources(), mContext.getPackageName(), mSuffix);
        }

        return mResourcesManage;
    }


    private void loadskinpkg(String skin_plugin_apk_path, String skin_plugin_pkgname) {
        try {
            //当前如果皮肤相同不换
            if (mCurentPath.equals(skin_plugin_apk_path) && mCurentPkg.equals(skin_plugin_pkgname)) {
                return;
            }
            //当皮肤与当前不一样执行换肤
            //AssetManager
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPathMethod = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPathMethod.invoke(assetManager, skin_plugin_apk_path);
            Resources superResources = mContext.getResources();
            Resources resources = new Resources(assetManager,
                    superResources.getDisplayMetrics(), superResources.getConfiguration());
            mResourcesManage = new ResourcesManage(resources, skin_plugin_pkgname, null);

            mCurentPath = skin_plugin_apk_path;
            mCurentPkg = skin_plugin_pkgname;


        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    public List<SkinView> getSkinViews(ISkinChangedListener listener) {
        return mSkinViewMaps.get(listener);
    }

    public void addSkinView(ISkinChangedListener listener, List<SkinView> views) {
        mSkinViewMaps.put(listener, views);
    }

    public void registListener(ISkinChangedListener listener) {
        mSkinListeners.add(listener);
    }

    public void unregistListener(ISkinChangedListener listener) {
        mSkinListeners.remove(listener);
        mSkinViewMaps.remove(listener);
    }

    //清除插件细信息
    private void clearPlugInfo() {
        mCurentPath = "";
        mCurentPkg = "";
        mSuffix = null;
        mPrefUtil.clear();
    }

    public void changeSkin(String suffix) {
        clearPlugInfo();
        mSuffix = suffix;
        mPrefUtil.saveSuffix(suffix);
        notifyChangedListener();
    }

    public void changeSkin(final String skin_plugin_apk_path, final String skin_plugin_pkgname, ISkinChangingCallback iSkinChangingCallback) {
        if (iSkinChangingCallback == null) {
            iSkinChangingCallback = ISkinChangingCallback.DEFAULT_SKIN_CHANGE_CALLBACK;
        }
        final ISkinChangingCallback callback = iSkinChangingCallback;
        callback.onStart();
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected Integer doInBackground(Void... params) {
                try {
                    loadskinpkg(skin_plugin_apk_path, skin_plugin_pkgname);
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onError(e);
                    return -1;
                }
                return 0;
            }

            @Override
            protected void onPostExecute(Integer aVoid) {
                try {
                    if (aVoid == -1) {
                        callback.onError(null);
                        return;
                    }
                    notifyChangedListener();
                    callback.onComplete();
                    //皮肤更换成功后执行更新换肤存储操作
                    updateSkinPluginfo(skin_plugin_apk_path, skin_plugin_pkgname);
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onError(e);
                }
            }
        }.execute();
    }

    private void updateSkinPluginfo(String plugin_path, String plugin_pkgname) {
        mPrefUtil.saveSkinPlug(plugin_path);
        mPrefUtil.saveSkinPlug(plugin_pkgname);
    }

    private void notifyChangedListener() {
        for (ISkinChangedListener listener : mSkinListeners) {
            skinChanged(listener);
            listener.onSkinChanged();
        }
    }

    public void skinChanged(ISkinChangedListener listener) {
        List<SkinView> skinViews = mSkinViewMaps.get(listener);
        for (SkinView skinView : skinViews) {
            skinView.apply();
        }
    }

    //是否需要换肤
    public boolean needChangeSkin() {
        return userSkinPlug() || userSuffix();
    }

    //判断当前的皮肤值
    private boolean userSkinPlug() {
        return mCurentPath != null && !mCurentPath.equals("");
    }

    //判断当前的皮肤值
    private boolean userSuffix() {
        return mSuffix != null && !mSuffix.equals("");
    }
}
