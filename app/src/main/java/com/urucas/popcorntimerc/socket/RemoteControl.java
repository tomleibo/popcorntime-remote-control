package com.urucas.popcorntimerc.socket;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.ConnectionConfigurator;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;
import com.urucas.popcorntimerc.R;
import com.urucas.popcorntimerc.activities.SplashActivity;
import com.urucas.popcorntimerc.interfaces.JSONRPCCallback;
import com.urucas.popcorntimerc.interfaces.SocketFoundCallback;
import com.urucas.popcorntimerc.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Urucas on 8/20/14.
 */
public class RemoteControl {

    private static final String TAG_NAME = "RemoteControl";

    private static String _host, _port, _user, _pass;

    private JSONRPC2Session _jsonRPCSession;
    private Activity _activity;

    public static int volume = 50;

    public RemoteControl(Activity activity, String host, String port, String user, String pass) {
        _activity = activity;
        _host = host;
        _port = port;
        _user = user;
        _pass = pass;
    }

    public void search4Sockets(final SocketFoundCallback callback) {

        String _myip = Utils.getIPAddress(true);

        String localip = "http://192.168." +
                _myip.split("\\.")[2]
                +".";

        for(int i = 0; i<256; i++) {
            String ip = localip + String.valueOf(i);
            final String hosts = ip;
            if(ip.equals(_myip)) continue;
            try {
                JSONRPC2Session testSession = new JSONRPC2Session(new URL(hosts+ ":"+_port));
                BasicAuthenticator auth = new BasicAuthenticator();
                auth.setCredentials(_user, _pass);
                testSession.setConnectionConfigurator(auth);

                new JSONRPCTask(testSession, new JSONRPCCallback(){

                    @Override
                    public void onSuccess(net.minidev.json.JSONObject jsonObject) { }

                    @Override
                    public void onSuccess() {
                        callback.onSuccess(hosts.replace("http://", ""));
                    }

                    @Override
                    public void onError(int error) { }

                }).execute("ping");

            } catch (Exception e) {}
        }
    }

    public void setSettings(String host, String port, String user, String pass) throws MalformedURLException {
        _host = host;
        _port = port;
        _user = user;
        _pass = pass;

        _jsonRPCSession = new JSONRPC2Session(new URL("http://" + _host + ":" + _port));

        BasicAuthenticator auth = new BasicAuthenticator();
        auth.setCredentials(_user, _pass);

        _jsonRPCSession.setConnectionConfigurator(auth);
    }

    private JSONRPC2Session getJsonRPCSession() throws MalformedURLException {
        if(_jsonRPCSession == null) {
            _jsonRPCSession = new JSONRPC2Session(new URL("http://" + _host + ":" + _port));

            BasicAuthenticator auth = new BasicAuthenticator();
            auth.setCredentials(_user, _pass);

            _jsonRPCSession.setConnectionConfigurator(auth);
        }
        return _jsonRPCSession;
    }

    private static int requestID = 1;

    private class JSONRPCTask extends AsyncTask<String, String, JSONRPC2Response> {

        private Map<String, Object> params;
        private JSONRPC2Session session;
        private JSONRPCCallback callback;

        public JSONRPCTask(JSONRPC2Session session, JSONRPCCallback callback) {
            this.session = session;
            this.callback = callback;
        }

        public JSONRPCTask(JSONRPC2Session session, Map<String, Object> params, JSONRPCCallback callback){
            this.session = session;
            this.params  = params;
            this.callback = callback;
        }

        @Override
        protected JSONRPC2Response doInBackground(String... params1) {
            if(params == null) {
                params = new HashMap<String, Object>();
            }
            String method = params1[0];

            session.getOptions().setRequestContentType("application/json");
            session.getOptions().setConnectTimeout(100);
            JSONRPC2Request request = new JSONRPC2Request(method, params, requestID);
            JSONRPC2Response response = null;

            try {
                response = session.send(request);
                Log.i("response", response.toString());

            } catch (JSONRPC2SessionException e) {
                // e.printStackTrace();
            }
            requestID++;
            return response;
        }

        @Override
        protected void onPostExecute(JSONRPC2Response response) {
            if(response == null || response.getError() != null) {
                callback.onError(R.string.connectiontesterror);
                return;
            }
            if(response.getResult() == null) {
                callback.onSuccess();
                return;
            }
            try {
                net.minidev.json.JSONObject object = (net.minidev.json.JSONObject) response.getResult();
                callback.onSuccess(object);

            }catch(Exception e){
                callback.onError(R.string.connectiontesterror);
            }
        }

    }

    private class BasicAuthenticator implements ConnectionConfigurator {

        private String user;
        private String pass;

        public void setCredentials(String user, String pass) {
            this.user = user;
            this.pass = pass;
        }

        @Override
        public void configure(HttpURLConnection connection) {

            byte[] encodedBytes = Base64.encode((user + ":" + pass).getBytes(), Base64.DEFAULT);
            // add custom HTTP header
            connection.addRequestProperty("Authorization", "Basic "+ new String(encodedBytes));
        }
    }

    /**
     * remote control events to emit
     */

    private void emit(String event){
        try {
            new JSONRPCTask(getJsonRPCSession(), new JSONRPCCallback(){

                @Override
                public void onSuccess(net.minidev.json.JSONObject jsonObject) {

                }

                @Override
                public void onSuccess() {

                }

                @Override
                public void onError(final int error) {
                    _activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((SplashActivity)_activity).connectionError(error);
                        }
                    });
                }

            }).execute(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void emit(String method, JSONRPCCallback callback) {
        if(!Utils.isWIFIConnected(_activity)) {
            callback.onError(R.string.noconnection);
        }

        try {
            new JSONRPCTask(getJsonRPCSession(), callback).execute(method);

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void ping(JSONRPCCallback callback) {
        this.emit("ping", callback);
    }

    public void toggleplay() {
        this.emit("toggleplaying");
    }

    public void fullscreen() {
        // toggle fullscreen only works on player view
        this.emit("togglefullscreen");
    }

    public void mute() {
        this.emit("togglemute");
    }

    public void escape() {
        this.emit("back");
    }

    public void moveRight() {
        this.emit("right");
    }

    public void moveLeft() {
        this.emit("left");
    }

    public void moveUp() {
        this.emit("up");
    }

    public void moveDown() {
        this.emit("down");
    }

    public void enter() {
        this.emit("enter");
    }

    public void showMoviesList() {
        this.emit("movieslist");
    }

    public void showSeriesList() {
        this.emit("showslist");
    }

    public void seasonUp() {
        this.emit("previousseason");
    }

    public void seasonDown() {
        this.emit("nextseason");
    }

    public void favourite() {
        this.emit("togglefavourite");
    }

    public void watched() {
        this.emit("togglewatched");
    }
}
