package com.hidden.launcher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class HiddenAccessibilityService extends AccessibilityService {
    private static HiddenAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally unused. HIDDEN does not inspect screen content.
    }

    @Override
    public void onInterrupt() {
        // No continuous accessibility feedback to interrupt.
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isConnected() {
        return instance != null;
    }

    public static boolean openNotifications() {
        HiddenAccessibilityService service = instance;
        return service != null &&
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
    }
}
