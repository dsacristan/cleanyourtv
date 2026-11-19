package com.pikita.purplefavs;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

/**
 * What the tile and the remote button run: asks for the player's Favoritos screen and finishes before it is
 * drawn, so nothing of this app is seen. With the accessibility service switched off there is nobody to press
 * the tile for us, and the player is simply opened.
 */
public class OpenFavouritesActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (serviceEnabled()) {
            Fav.begin(this);
        } else {
            startPlayer();
        }
        finish();
    }

    private boolean serviceEnabled() {
        String list = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        String flat = new ComponentName(this, FavKeyService.class).flattenToString();
        String shortName = getPackageName() + "/.FavKeyService";
        return list.contains(flat) || list.contains(shortName);
    }

    private void startPlayer() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(Fav.PLAYER,
                    "com.purpleplayer.iptv.android.activities.SplashActivity"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
            // Nothing else to do: the player is not installed.
        }
    }
}
