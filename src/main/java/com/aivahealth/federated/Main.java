package com.aivahealth.federated;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;

public class Main {
    static final String tokenFile = "aiva-jwt.properties";
    static final String expirePropKey = "expire";
    static final String tokenPropKey = "token";
    static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public static void main(String[] args) {
        // Get the auth0 client and secret from the environment

        String clientSecret = System.getenv("CLIENT_SECRET");
        if (clientSecret == null) {
            System.err.println("Missing client secret");
            System.exit(1);
        }

        String clientId = System.getenv("CLIENT_ID");
        if (clientId == null) {
            System.err.println("Missing client ID");
            System.exit(1);
        }

        try {
            // Try and use a cached token if it has not expired
            String token = readSavedToken();

            if (token == null) {
                // Generate a JWT token and write it through to the cache file
                token = getAuthToken(clientId, clientSecret);
            }

            if (token == null) {
                System.err.println("Exiting; was not able to get a valid auth token");
                System.exit(1);
            }

            // POST a customer request
            sendCustomerRequest(token);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendCustomerRequest(String token) throws IOException {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("device", "mydevice");
        jsonObj.put("unit", "myunit");
        jsonObj.put("bed", "mybed");
        jsonObj.put("room", "myroom");
        jsonObj.put("msg", "somemsg");
        jsonObj.put("organizationId", "cshs");
        jsonObj.put("organizationEnv", "SUP");
        jsonObj.put("source", "R5");

        HttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost("http://federated.aivadev.com/v1/customer/request");
        post.addHeader("content-type", "application/json");
        post.addHeader("Authorization", "Bearer " + token);

        HttpEntity entity = new StringEntity(jsonObj.toJSONString());
        post.setEntity(entity);

        HttpResponse response = client.execute(post);
        if (response.getStatusLine().getStatusCode() == 204) {
            System.out.println("Success!");
        } else {
            HttpEntity responseEntity = response.getEntity();
            String content = IOUtils.toString(responseEntity.getContent(), "UTF-8");
            System.out.println("Failed request");
            System.out.println(" Status: " + response.getStatusLine());
            System.out.println(" Content: " + content);
        }
    }

    private static String getAuthToken(String clientId, String clientSecret) throws IOException, ParseException {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("audience", "https://aivahealth.com/api/v1");
        jsonObj.put("client_id", clientId);
        jsonObj.put("client_secret", clientSecret);
        jsonObj.put("client_credentials", "ClientId");
        jsonObj.put("grant_type", "client_credentials");

        HttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost("https://aiva.auth0.com/oauth/token");
        post.addHeader("content-type", "application/json");

        HttpEntity entity = new StringEntity(jsonObj.toJSONString());
        post.setEntity(entity);

        HttpResponse response = client.execute(post);
        HttpEntity responseEntity = response.getEntity();
        String content = IOUtils.toString(responseEntity.getContent(), "UTF-8");

        JSONParser parser = new JSONParser();
        Object obj = parser.parse(content);
        JSONObject tokenJson = (JSONObject)obj;

        // Write to the cache file
        Properties props = new Properties();
        props.setProperty(tokenPropKey, (String) tokenJson.get("access_token"));
        props.setProperty(expirePropKey, computeExpires((Long) tokenJson.get("expires_in")));
        File propertiesFile = new File(tokenFile);
        OutputStream out = new FileOutputStream(propertiesFile);
        props.store(out, "Aiva auth0 token");

        return (String) tokenJson.get("access_token");
    }

    private static String computeExpires(Long expiresIn) {
        Calendar cal = GregorianCalendar.getInstance();
        cal.add(Calendar.SECOND, Math.toIntExact(expiresIn));
        cal.add(Calendar.HOUR,  - 10);
        return sdf.format(cal.getTime());
    }

    private static String readSavedToken() throws Exception {
        Properties prop = new Properties();
        InputStream input = null;

        try {
            String bar = FileUtils.readFileToString(new File(tokenFile), "UTF-8");
            prop.load(IOUtils.toInputStream(bar, "UTF-8"));
            String token = prop.getProperty(tokenPropKey);
            String expires = prop.getProperty(expirePropKey);
            Date date = sdf.parse(expires);
            if (token != "" && new Date().before(date)) {
                return token;
            }
        } catch (FileNotFoundException ex) {
            // Ok that there's no file
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }
}
