package com.projecttango.examples.java.floorplan;

import android.accounts.Account;
import android.app.Application;

import com.lnikkila.oidc.OIDCAccountManager;

/**
 * Created by alcorn on 7/24/16.
 */
public class DefectParty extends Application {

    public OIDCAccountManager accountManager;
    public Account availableAccounts[];

    public int selectedAccountIndex;

    private static DefectParty instance;

    public void onCreate()
    {
        super.onCreate();
        instance = this;
    }

    public static DefectParty getApp() {
        return instance;
    }

}
