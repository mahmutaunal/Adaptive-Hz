package com.mahmutalperenunal.adaptivehz.core.shizuku;

import android.os.IBinder;
import com.mahmutalperenunal.adaptivehz.core.shizuku.IInputEventCallback;

interface IInputMonitorService {
    String listInputDevices();
    String readRefreshRateSetting(String key);
    boolean writeRefreshRateSetting(String key, String value);
    boolean deleteRefreshRateSetting(String key);
    int readSamsungRefreshRateMode();
    boolean writeSamsungRefreshRateMode(int value);
    String inspectSamsungRefreshRateTokenApi();
    boolean applySamsungRefreshRateTokenLimits(int minRefreshRate, int maxRefreshRate);
    boolean releaseSamsungRefreshRateTokenLimits();
    boolean applyHyperOsRefreshRateSetting(String key, int refreshRate);
    boolean releaseHyperOsRefreshRateSetting();
    void registerDisplaySessionClient(IBinder client);
    void startMonitoring(String devicePath, IInputEventCallback callback);
    void stopMonitoring();
    void destroy();
}
