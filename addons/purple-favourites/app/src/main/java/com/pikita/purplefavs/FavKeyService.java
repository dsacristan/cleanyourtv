package com.pikita.purplefavs;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Two jobs, both of them the ones only an accessibility service can do here:
 *
 * <ul>
 *   <li>Take the remote's app button before the system's global-key handler does (it would open the store
 *       listing for an app that is no longer installed) and run the shortcut instead.</li>
 *   <li>Walk the player's screen and press its Favoritos tile, since no launcher may read another app's
 *       state directly.</li>
 * </ul>
 *
 * Every key the filter sees is logged: that is how the button's code was found, and it stays useful if the
 * remote is paired again. Keys other than the one mapped here are left alone.
 */
public class FavKeyService extends AccessibilityService {

    /** The Chromecast remote's Netflix button, as the key filter sees it: KEYCODE_BUTTON_4 (getevent shows the
     *  HID usage 0x0c0078 with no keycode, and the system's global-key handler answers it by opening the store
     *  listing for com.netflix.ninja). */
    private static final int KEY_CODE = KeyEvent.KEYCODE_BUTTON_4;

    /** How often the player's screen is looked at while a shortcut is in flight. */
    private static final long POLL_MS = 400;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** The running service, so the tile's activity can start the poll without binding to it. */
    private static volatile FavKeyService instance;

    /** Asks for a poll after a shortcut was requested from the tile's activity or from a key. */
    static void kick() {
        FavKeyService s = instance;
        if (s == null) return;
        s.handler.removeCallbacks(s.poll);
        s.handler.postDelayed(s.poll, 250);
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            try {
                if (!Fav.step(FavKeyService.this)) handler.postDelayed(this, POLL_MS);
            } catch (Exception e) {
                Log.w(Fav.TAG, "poll failed", e);
                Fav.cancel();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        instance = this;
        Log.i(Fav.TAG, "service connected");
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        boolean netflix = event.getKeyCode() == KEY_CODE;
        if (!netflix) return false;
        Log.i(Fav.TAG, "netflix key: action=" + event.getAction() + " repeat=" + event.getRepeatCount());
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            Fav.begin(this);
            kick();
        }
        return true;   // swallowed: the store listing does not open
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Nothing is read from other apps outside a shortcut in flight.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacks(poll);
        super.onDestroy();
    }
}
