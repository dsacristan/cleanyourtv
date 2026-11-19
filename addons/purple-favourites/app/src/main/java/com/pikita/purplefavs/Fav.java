package com.pikita.purplefavs;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Opens Purple IPTV and presses its own "FAVORITOS" tile on the dashboard, so whoever asked lands on the
 * favourites list with one key. Nothing here reads the app's data: the tile is clicked the way a person
 * would, through accessibility, and it is only ever done to that one screen.
 *
 * The player's dashboard item is a clickable LinearLayout with a stable id ({@code ll_favourite}) and a
 * fallback label, and the favourites screen itself is titled "Favoritos"; both are matched by text or id
 * so no screen coordinates are involved.
 */
final class Fav {
    static final String TAG = "PurpleFavs";

    static final String PLAYER = "com.purple.iptv.smart.player";
    private static final String SPLASH =
            "com.purpleplayer.iptv.android.activities.SplashActivity";
    private static final String ID_FAVOURITE = "ll_favourite";
    private static final String ID_HEADER = "header_title";

    /** How long the search may go on: a cold start, a splash and the profile check fit well inside it. */
    private static final long DEADLINE_MS = 25000;
    /** A click that landed but did not change the screen is retried, but not forever. */
    private static final int MAX_CLICKS = 3;
    /** How many times the shortcut may back out of the player to reach the dashboard. */
    private static final int MAX_BACKS = 2;
    /** A press of Back needs a moment to land before the screen is read again. */
    private static final long BACK_GAP_MS = 1500;

    private static volatile boolean pending;
    private static long deadline;
    private static boolean launched;
    private static long launchedAt;
    private static long inAppAt;
    private static long lastBack;
    private static int clicks;
    private static int backs;

    private Fav() { }

    /** Starts watching for the player's dashboard: the caller is done the moment this returns. */
    static void begin(Context ctx) {
        pending = true;
        deadline = SystemClock.elapsedRealtime() + DEADLINE_MS;
        launched = false;
        launchedAt = 0;
        inAppAt = 0;
        lastBack = 0;
        clicks = 0;
        backs = 0;
        FavKeyService.kick();
        Log.i(TAG, "begin");
    }

    static boolean isPending() {
        return pending;
    }

    static void cancel() {
        pending = false;
    }

    /**
     * One polling step, from the accessibility service: starts the player when it is not in front, then clicks
     * Favoritos. Returns true once there is nothing left to wait for.
     */
    static boolean step(AccessibilityService svc) {
        if (!pending) return true;
        long now = SystemClock.elapsedRealtime();
        if (now > deadline) {
            Log.w(TAG, "gave up: no dashboard in " + DEADLINE_MS + " ms");
            pending = false;
            return true;
        }

        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        CharSequence pkg = root == null ? null : root.getPackageName();
        if (pkg == null || !PLAYER.contentEquals(pkg)) {
            // Not in the player yet: ask for it once, then keep looking.
            if (!launched) {
                launched = true;
                launchedAt = now;
                launch(svc);
            }
            return false;
        }

        if (isOnFavourites(root)) {
            Log.i(TAG, "already on Favoritos");
            pending = false;
            return true;
        }
        if (inAppAt == 0) inAppAt = now;
        if (now - inAppAt < 1200) return false;  // the screen that came up is still drawing

        AccessibilityNodeInfo tile = byId(root, ID_FAVOURITE);
        if (tile == null) tile = byText(root, "FAVORITOS");
        if (tile == null) tile = byText(root, "FAVOURITES");
        if (tile == null) {
            // Somewhere else in the player (a channel playing, the movies screen): back out towards the
            // dashboard instead of leaving the press unanswered.
            if (backs < MAX_BACKS && now - lastBack > BACK_GAP_MS) {
                lastBack = now;
                backs++;
                boolean ok = svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                Log.i(TAG, "back " + backs + " -> " + ok);
            }
            return false;
        }

        AccessibilityNodeInfo target = tile;
        while (target != null && !target.isClickable()) target = target.getParent();
        boolean clicked = (target != null ? target : tile).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        clicks++;
        Log.i(TAG, "click " + clicks + " -> " + clicked);
        if (clicked && clicks >= MAX_CLICKS) pending = false;
        return false;
    }

    private static void launch(Context ctx) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(PLAYER, SPLASH));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            Log.i(TAG, "player asked for");
        } catch (Exception e) {
            Log.w(TAG, "cannot start the player", e);
            pending = false;
        }
    }

    /** The favourites screen is the one whose title says so. */
    private static boolean isOnFavourites(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo header = byId(root, ID_HEADER);
        CharSequence text = header == null ? null : header.getText();
        if (text == null) return false;
        String s = text.toString().trim().toLowerCase(Locale.ROOT);
        return s.startsWith("favorit") || s.startsWith("favourit");
    }

    private static AccessibilityNodeInfo byId(AccessibilityNodeInfo root, String id) {
        for (AccessibilityNodeInfo n : walk(root)) {
            String rid = n.getViewIdResourceName();
            if (rid != null && rid.endsWith(":id/" + id)) return n;
        }
        return null;
    }

    private static AccessibilityNodeInfo byText(AccessibilityNodeInfo root, String text) {
        for (AccessibilityNodeInfo n : walk(root)) {
            CharSequence t = n.getText();
            if (t != null && text.contentEquals(t.toString().trim())) return n;
        }
        return null;
    }

    /** Every node under root, breadth first, without recursion (the dashboard is deep). */
    private static Iterable<AccessibilityNodeInfo> walk(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> out = new ArrayDeque<>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty() && out.size() < 4000) {
            AccessibilityNodeInfo n = queue.removeFirst();
            out.add(n);
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) queue.add(c);
            }
        }
        return out;
    }
}
