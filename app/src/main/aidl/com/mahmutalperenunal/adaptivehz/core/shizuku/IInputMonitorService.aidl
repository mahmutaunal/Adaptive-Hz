package com.mahmutalperenunal.adaptivehz.core.shizuku;

import com.mahmutalperenunal.adaptivehz.core.shizuku.IInputEventCallback;

interface IInputMonitorService {
    String runCommand(String command);
    void startMonitoring(String devicePath, IInputEventCallback callback);
    void stopMonitoring();
    void destroy();
}