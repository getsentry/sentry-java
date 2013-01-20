package net.kencochrane.raven;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sentry tools.
 */
public class SentryApi {

    public final String host;
    public final HttpClient client = new DefaultHttpClient();
    public final HttpContext context = new BasicHttpContext();
    private boolean loggedIn;

    public SentryApi() throws MalformedURLException {
        this("http://localhost:9500");
    }

    public SentryApi(String host) {
        this.host = host;
        // Otherwise HttpClient gets confused when hitting the dashboard without an existing project
        client.getParams().setParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);
    }

    public boolean login(String username, String password) throws IOException {
        if (loggedIn) {
            return true;
        }
        String url = host + "/login/";
        HttpResponse response = client.execute(new HttpGet(url), context);
        String html = EntityUtils.toString(response.getEntity());
        Document doc = Jsoup.parse(html);
        Elements inputs = doc.select("input[name=csrfmiddlewaretoken]");
        String token = inputs.get(0).val();
        List<NameValuePair> formparams = new ArrayList<NameValuePair>();
        formparams.add(new BasicNameValuePair("username", username));
        formparams.add(new BasicNameValuePair("password", password));
        formparams.add(new BasicNameValuePair("csrfmiddlewaretoken", token));
        HttpPost post = new HttpPost(host + "/login/");
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");
        post.setEntity(entity);
        response = client.execute(post, context);
        if (response.getStatusLine().getStatusCode() == 302) {
            EntityUtils.toString(response.getEntity());
            response = client.execute(new HttpGet(url), context);
            html = EntityUtils.toString(response.getEntity());
            doc = Jsoup.parse(html);
            html = doc.getElementById("header").select("li.dropdown").get(1).html();
            loggedIn = html.contains(">Logout<");
        }
        return loggedIn;
    }

    public String getDsn(String projectSlug) throws IOException {
        HttpResponse response = client.execute(new HttpGet(host + "/account/projects/" + projectSlug + "/docs/"));
        String html = EntityUtils.toString(response.getEntity());
        Document doc = Jsoup.parse(html);
        Element wrapper = doc.select("#content code.clippy").get(0);
        return StringUtils.trim(wrapper.html());
    }

    public boolean clear(String projectId) throws IOException {
        HttpResponse response = client.execute(new HttpPost(host + "/api/" + projectId + "/clear/"));
        boolean ok = (response.getStatusLine().getStatusCode() == 200);
        EntityUtils.toString(response.getEntity());
        return ok;
    }

    public JSONArray getRawJson(String projectSlug, String group) throws IOException {
        HttpResponse response = client.execute(new HttpGet(host + "/" + projectSlug + "/group/" + group + "/events/json/"));
        String raw = EntityUtils.toString(response.getEntity());
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return (JSONArray) new JSONParser().parse(raw);
        } catch (ParseException e) {
            throw new IOException(e);
        }
    }

    public List<Event> getEvents(String projectId) throws IOException {
        HttpResponse response = client.execute(new HttpGet(host + "/api/" + projectId + "/poll/"));
        try {
            Object json = new JSONParser().parse(EntityUtils.toString(response.getEntity()));
            List<JSONObject> eventJson = null;
            if (json instanceof JSONObject) {
                eventJson = Arrays.asList((JSONObject) json);
            } else {
                eventJson = (List<JSONObject>) json;
            }
            List<Event> events = new LinkedList<Event>();
            for (JSONObject item : eventJson) {
                Boolean resolved = (Boolean) item.get("isResolved");
                if (resolved != null && resolved) {
                    continue;
                }
                String group = (String) item.get("id");
                Integer count = Integer.parseInt((String) item.get("count"));
                Integer level = ((Long) item.get("level")).intValue();
                String levelName = (String) item.get("levelName");
                String link = (String) item.get("permalink");
                String title = (String) item.get("title");
                String message = (String) item.get("message");
                String logger = (String) item.get("logger");
                events.add(new Event(group, count, level, levelName, link, title, message, logger));
            }
            return events;
        } catch (ParseException e) {
            throw new IOException(e);
        }
    }

    public List<String> getAvailableTags(String projectSlug) throws IOException {
        HttpResponse response = client.execute(new HttpGet(host + "/account/projects/" + projectSlug + "/tags/"));
        Document doc = Jsoup.parse(EntityUtils.toString(response.getEntity()));
        Elements items = doc.select("#div_id_filters label.checkbox");
        Pattern pattern = Pattern.compile(".*\\((\\w+)\\)");
        List<String> tagNames = new LinkedList<String>();
        for (Element item : items) {
            Matcher matcher = pattern.matcher(item.text());
            if (matcher.matches()) {
                tagNames.add(matcher.group(1));
            }
        }
        return tagNames;
    }

    protected static String extractLevel(Collection<String> classNames) {
        for (String name : classNames) {
            if (name.startsWith("level-")) {
                return name.replace("level-", "");
            }
        }
        return null;
    }

    public static class Event {
        public final String group;
        public final int count;
        public final int level;
        public final String levelName;
        public final String url;
        public final String title;
        public final String message;
        public final String logger;

        public Event(String group, int count, int level, String levelName, String url, String title, String message, String logger) {
            this.group = group;
            this.count = count;
            this.level = level;
            this.levelName = levelName;
            this.url = url;
            this.title = title;
            this.message = message;
            this.logger = logger;
        }

    }

}
