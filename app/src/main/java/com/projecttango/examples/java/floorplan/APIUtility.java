package com.projecttango.examples.java.floorplan;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.Gson;
import com.lnikkila.oidc.OIDCAccountManager;
import com.lnikkila.oidc.security.UserNotAuthenticatedWrapperException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.Map;
import java.util.List;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

/**
 * An incomplete class that illustrates how to make API requests with the Access Token.
 *
 * @author Leo Nikkilä
 * @author Camilo Montes
 */
public class APIUtility {

    public static class ToFluxTask extends AsyncTask<String, Void, String> {

        Context mContext;
        OIDCAccountManager mAccountManager;
        Account mAccount;
        String mProjectId;
        String mCellId;
        AccountManagerCallback<Bundle> mCallback;

        public ToFluxTask(Context context, OIDCAccountManager am, Account account, String projectId, String cellId) {
            Log.i("ToFlux", "creating new task");
            mContext = context;
            mAccountManager = am;
            mAccount = account;
            mProjectId = projectId;
            mCellId = cellId;
        }

        @Override
        protected void onPreExecute() {
            mCallback = new AccountManagerCallback<Bundle>() {
                @Override
                public void run(AccountManagerFuture<Bundle> futureManager) {
                    // Unless the account creation was cancelled, try logging in again
                    // after the account has been created.
                    Log.i("ToFlux", "sent to flux");
                }
            };
        }

        @Override
        protected String doInBackground(String... data) {
            Log.i("ToFlux", "sending data");
            Log.i("ToFlux", data[0]);
            boolean doRetry = true;
            Resources res = mContext.getResources();
            String url = String.format(res.getString(R.string.cell), mProjectId, mCellId);
            try {
                String accessToken = mAccountManager.getAccessToken(mAccount, mCallback);
                String cookies = mAccountManager.getCookies(mAccount, mCallback);
//                String cookieStrings[] = cookies.split("; ");
//                String fluxToken = "";
//                Log.i("ToFlux", "cookies");
//                for (int i=0, len=cookieStrings.length; i<len; i++) {
//                    String parts[] = cookieStrings[i].split("=", 1);
//                    Log.i("ToFlux", parts[0]);
//                    Log.i("ToFlux", parts[1]);
//                    if (parts[0].equals("flux_token")) {
//                        fluxToken = parts[1];
//                    }
//                }


                Log.i("ToFlux", "cookies");
//                Log.i("ToFlux", Integer.toString(cookieList.size()));
//                Log.i("ToFlux", cookieList.toString());
//                for (int i=0, len=cookieList.size(); i<len; i++) {
//                    Log.i("ToFlux", cookieList.get(i).getName());
//                    Log.i("ToFlux", cookieList.get(i).getValue());
//                    if (cookieList.get(i).getName().equals("flux_token")) {
//                        fluxToken = cookieList.get(i).getValue();
//                    }
//                }
                Log.i("ToFlux", cookies);
                Log.i("ToFlux", "flux_token cookie");
//                Log.i("ToFlux", fluxToken);
                String fluxToken = "";

                // Prepare an API request using the accessToken
                HttpRequest request = new HttpRequest(url, HttpRequest.METHOD_POST);
                request = prepareApiRequest(request, accessToken, cookies);
                request = addFluxHeaders(request, res.getString(R.string.oidc_clientId), fluxToken);
                request.send(data[0]);
                Log.i("ToFlux", "making request");
                Log.i("ToFlux", request.toString());
                Log.i("ToFlux", request.headers().toString());
                Log.i("ToFlux", data[0]);

                if (request.ok()) {
                    Log.i("ToFlux", "request ok");
                    return request.body();
                } else {
                    Log.i("ToFlux", "request problem");
                    int code = request.code();
                    Log.i("ToFlux", Integer.toString(code));

                    String requestContent = "empty body";
                    try {
                        requestContent = request.body();
                        Log.i("ToFlux", requestContent);
                    } catch (HttpRequest.HttpRequestException e) {
                        //Nothing to do, the response has no body or couldn't fetch it
                        e.printStackTrace();
                    }

//                    if (doRetry && (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN ||
//                            (code == HTTP_BAD_REQUEST && (requestContent.contains("invalid_grant") || requestContent.contains("Access Token not valid"))))) {
//                        // We're being denied access on the first try, let's renew the token and retry
//                        mAccountManager.invalidateAuthTokens(account);
//
//                        return makeRequest(mAccountManager, HttpRequest.METHOD_POST, url, data[0], account, false, callback);
//                    } else {
//                        // An unrecoverable error or the renewed token didn't work either
//                        throw new IOException(request.code() + " " + request.message() + " " + requestContent);
//                    }
                }
            } catch (IOException|UserNotAuthenticatedWrapperException|AuthenticatorException|OperationCanceledException e) {
                Log.e("ToFlux", e.toString());
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            Log.i("ToFlux", result);
        }
    }

    /**
     * Makes a GET request and parses the received JSON string as a Map.
     */
    public static Map getJson(OIDCAccountManager accountManager, String url, Account account,
                              AccountManagerCallback<Bundle> callback)
            throws IOException, UserNotAuthenticatedWrapperException, AuthenticatorException, OperationCanceledException {

        String jsonString = makeRequest(accountManager, HttpRequest.METHOD_GET, url, account, callback);
        Log.i("APIUtility", jsonString);
        return new Gson().fromJson(jsonString, Map.class);
    }

    public static List<JSONObject> getJsonList(OIDCAccountManager accountManager, String url, Account account,
                                   AccountManagerCallback<Bundle> callback)
            throws IOException, UserNotAuthenticatedWrapperException, AuthenticatorException, OperationCanceledException {

        String jsonString = makeRequest(accountManager, HttpRequest.METHOD_GET, url, account, callback);
        Log.i("APIUtility", jsonString);
        return new Gson().fromJson(jsonString, List.class);
    }

    /**
     * Makes an arbitrary HTTP request using the provided account.
     *
     * If the request doesn't execute successfully on the first try, the tokens will be refreshed
     * and the request will be retried. If the second try fails, an exception will be raised.
     */
    public static String makeRequest(OIDCAccountManager accountManager, String method, String url, Account account,
                                     AccountManagerCallback<Bundle> callback)
            throws IOException, UserNotAuthenticatedWrapperException, AuthenticatorException, OperationCanceledException {

        return makeRequest(accountManager, method, url, "", account, true, callback);
    }

    private static String makeRequest(OIDCAccountManager accountManager, String method, String url, String body, Account account,
                                      boolean doRetry, AccountManagerCallback<Bundle> callback)
            throws IOException, UserNotAuthenticatedWrapperException, AuthenticatorException, OperationCanceledException {


        String accessToken = accountManager.getAccessToken(account, callback);
        String cookies = accountManager.getCookies(account, callback);

        // Prepare an API request using the accessToken
        HttpRequest request = new HttpRequest(url, method);
        request = prepareApiRequest(request, accessToken, cookies);
        if (body != "") {
            request.send(body);
        }

        if (request.ok()) {
            return request.body();
        } else {
            int code = request.code();

            String requestContent = "empty body";
            try {
                requestContent = request.body();
            } catch (HttpRequest.HttpRequestException e) {
                //Nothing to do, the response has no body or couldn't fetch it
                e.printStackTrace();
            }

            if (doRetry && (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN ||
                    (code == HTTP_BAD_REQUEST && (requestContent.contains("invalid_grant") || requestContent.contains("Access Token not valid"))))) {
                // We're being denied access on the first try, let's renew the token and retry
                accountManager.invalidateAuthTokens(account);

                return makeRequest(accountManager, method, url, body, account, false, callback);
            } else {
                // An unrecoverable error or the renewed token didn't work either
                throw new IOException(request.code() + " " + request.message() + " " + requestContent);
            }
        }
    }

    public static HttpRequest addFluxHeaders(HttpRequest request, String clientId, String fluxToken) throws IOException {
        JSONObject fluxOptions = new JSONObject();
        try {
            fluxOptions.put("ClientInfo", new JSONObject()
                            .put("ClientId", clientId)
                            .put("ClientVersion", "unknown")
                            .put("SDK", "Flux Javascript SDK")
                            .put("SDKVersion", "0.0.0")
                            .put("AdditionalClientData", new JSONObject()
                                            .put("HostProgramVersion", "unknown")
                                            .put("HostProgramMainFile", "unknown")
                            )
//                            .put("Platform", new JSONObject()
                            .put("OS", "browser/*")
//                            )
            );
        } catch (JSONException e) {
            Log.e("ToFlux", e.toString());
        }
        Log.i("ToFlux", fluxOptions.toString());
        Log.i("ToFlux", HttpRequest.Base64.encode(fluxOptions.toString()));

//        String _fluxOptions = "eyJDbGllbnRJbmZvIjp7IkNsaWVudElkIjoiNDczNjQ2MzYtMTQ2MS00NTBhLWEyNWQtYTFmYTc3MTIyZTRlIiwiQ2xpZW50VmVyc2lvbiI6IjIzMjciLCJPUyI6ImJyb3dzZXIvanMtc2RrLzAuNC4wIiwiU0RLTmFtZSI6IkZsdXggSmF2YXNjcmlwdCBTREsiLCJTREtWZXJzaW9uIjoiMC40LjAifSwiTWV0YWRhdGEiOnRydWUsIkNsaWVudE1ldGFkYXRhIjp0cnVlfQ==";
//        String _fluxToken = "MTQ2Nzc4NDMzN3xxWXNmcjA3UW1FUkZzbzFBdEFjdzFrV2ctU1UybVBRaFNUNFA3azkyTmhjVjk2Zk5FbjVJUnNIaFlCNGlPbkcxVzFtakJNWWpud2ZoWHctYzZiUUtIRnlrdDlWY2ZiRm1UQ01oUkwtYmIteWFHWWpOTXJYQld6dnFnNy10NDFXT1RWYldLX0tVUTRxc3FrakFLa0VKVlBBS3y7FuyQkawxGfPv3C536xhuHFC7KJ2mvaxU5iueGbncCg==";

        return request.header("Flux-Options", HttpRequest.Base64.encode(fluxOptions.toString()))
//        return request.header("Flux-Options", _fluxOptions)
                .header("Flux-Request-Marker", 1)
                .header("Flux-Plugin-Platform", "browser/*")
                .header("Flux-Plugin-Host", "web")
                .header("Flux-Request-Token", fluxToken);
//                .header("Flux-Request-Token", _fluxToken);
    }

    /**
     * Prepares an arbitrary API request by injecting an ID Token into an HttpRequest. Uses an
     * external library to make my life easier, but you can modify this to use whatever in case you
     * don't like the (small) dependency.
     */
    public static HttpRequest prepareApiRequest(HttpRequest request, String idToken, String cookies)
            throws IOException {

        request = request.authorization("Bearer " + idToken).acceptJson();
        request = request.header("Cookie", cookies);
        return request;
    }
}
