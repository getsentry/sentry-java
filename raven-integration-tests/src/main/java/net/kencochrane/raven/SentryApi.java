package net.kencochrane.raven;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

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

    public List<Event> getEvents(String projectSlug) throws IOException {
        HttpResponse response = client.execute(new HttpGet(host + "/" + projectSlug));
        Document doc = Jsoup.parse(EntityUtils.toString(response.getEntity()));
        Elements items = doc.select("ul#event_list li.event");
        List<Event> events = new LinkedList<Event>();
        for (Element item : items) {
            int count = Integer.parseInt(item.attr("data-count"));
            int level = extractLevel(item.classNames());
            Element anchor = item.select("h3 a").get(0);
            String link = anchor.attr("href");
            String title = StringUtils.trim(anchor.text());
            Element messageElement = item.select("p.message").get(0);
            String message = StringUtils.trim(messageElement.attr("title"));
            String logger = StringUtils.trim(messageElement.select("span.tag-logger").text());
            events.add(new Event(count, level, link, title, message, logger));
        }
        return events;
    }

    protected static int extractLevel(Collection<String> classNames) {
        for (String name : classNames) {
            if (name.startsWith("level-")) {
                return Integer.parseInt(name.replace("level-", ""));
            }
        }
        return 0;
    }

    public static class Event {
        public final int count;
        public final int level;
        public final String url;
        public final String title;
        public final String message;
        public final String logger;

        public Event(int count, int level, String url, String title, String message, String logger) {
            this.count = count;
            this.level = level;
            this.url = url;
            this.title = title;
            this.message = message;
            this.logger = logger;
        }

    }

}
