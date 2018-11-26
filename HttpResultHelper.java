package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.io.IOException;
import java.io.InputStream;

import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;

/**
 * Class has been built based on provided code at https://notes.iopush.net/android-send-a-https-post-request/
 */
public class HttpResultHelper {
    private int statusCode;
    private InputStream response;

    public HttpResultHelper() {
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public InputStream getResponse() {
        return response;
    }

    public void setResponse(InputStream response) {
        this.response = response;
    }

    public HttpResultHelper httpPost(String urlStr, String user, String password, String data, ArrayList<String[]> headers, int timeOut) throws IOException
    {
        // Set url
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // If secure connection
        if (urlStr.startsWith("https")) {
            try {
                SSLContext sc;
                sc = SSLContext.getInstance("TLS");
                sc.init(null, null, new java.security.SecureRandom());
                ((HttpsURLConnection)conn).setSSLSocketFactory(sc.getSocketFactory());
            } catch (Exception e) {
                Log.d(MainActivity.TAG,"Failed to construct SSL object" + e.getMessage());
            }
        }


        // Use this if you need basic authentication
        if ((user != null) && (password != null)) {
            String userPass = user + ":" + password;
            String basicAuth = "Basic " + Base64.encodeToString(userPass.getBytes(), Base64.DEFAULT);
            conn.setRequestProperty("Authorization", basicAuth);
        }

        // Set Timeout and method
        conn.setReadTimeout(timeOut);
        conn.setConnectTimeout(timeOut);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        if (headers != null) {
            for (int i = 0; i < headers.size(); i++) {
                conn.setRequestProperty(headers.get(i)[0], headers.get(i)[1]);
            }
        }

        if (data != null) {
            conn.setFixedLengthStreamingMode(data.getBytes().length);
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(os, "UTF-8"));
            writer.write(data);
            writer.flush();
            writer.close();
            os.close();
        }

        InputStream inputStream = null;
        try
        {
            inputStream = conn.getInputStream();
        }
        catch(IOException exception)
        {
            inputStream = conn.getErrorStream();
        }

        HttpResultHelper result = new HttpResultHelper();
        result.setStatusCode(conn.getResponseCode());
        result.setResponse(inputStream);

        return result;
    }
}