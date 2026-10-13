///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS org.jsoup:jsoup:1.18.3
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Queues a LinkedIn, Bluesky and Mastodon post in Postiz for every post that
 * went live, with the text written by Claude from the article itself.
 *
 *   new <previous.xml> <current.xml>   posts in current that previous lacks
 *
 * Same contract as SlackPosts.java: the previous feed is from BEFORE the
 * deploy, so no state is kept, and an empty previous feed aborts instead of
 * queueing the whole feed.
 *
 * The posts are SCHEDULED a while ahead, not published, so they can be read,
 * edited or deleted in Postiz first. SOCIAL_DELAY_MINUTES (default 180) sets
 * how long that window is. Slack is told they are waiting: as a direct message
 * to SLACK_MENTION_ID when SLACK_BOT_TOKEN is set, else in the SLACK_WEBHOOK_URL channel.
 *
 * Environment:
 *   POSTIZ_API_KEY     Postiz > Settings > Public API
 *   ANTHROPIC_API_KEY  writes the texts
 *   POSTIZ_URL         default https://api.postiz.com/public/v1 (set it for self-hosting)
 *   POSTIZ_BLUESKY_ID, POSTIZ_MASTODON_ID, POSTIZ_LINKEDIN_ID
 *                      optional channel IDs; otherwise the connected channels are looked up
 *
 * Either key unset, or `--dry-run`: prints what would be queued, posts nothing.
 */
public class SocialPosts {

    record Post(String title, String link, String description) {}

    record Author(String name, String bluesky, String mastodon) {}

    record Platform(String id, String label, int max, String extra) {}

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final int MAX_NEW = 5;
    static final int MAX_ARTICLE_CHARS = 8000;
    static final String MODEL = "claude-sonnet-5-5";

    // Postiz identifiers. LinkedIn has no text handles, so authors are named in
    // plain text; the mention a person adds by hand in the app is not available here.
    static final List<Platform> PLATFORMS = List.of(
            new Platform("mastodon", "Mastodon", 500,
                    "Add line breaks to separate text, link, etc.\nWhen you mention Foojay, add the emoji `:foojay:`"),
            new Platform("bluesky", "Bluesky", 300, ""),
            new Platform("linkedin", "LinkedIn", 1000,
                    "Add line breaks to separate text, link, etc.\n"
                            + "Name each author in plain text, without an `@`. Don't add a link to the author's profile.\n"
                            + "Use plain text only: no bold, no italic, no markdown and no unicode bold/italic characters.\n"
                            + "Don't open with a hook or rhetorical question like `If you've ever wondered...`, `Ever wondered...`, "
                            + "`Have you ever...` or `Imagine...`. Start directly with what the article is about, in a plain and "
                            + "factual tone, like a colleague sharing a link."));

    public static void main(String[] args) throws Exception {
        List<String> a = new ArrayList<>(List.of(args));
        boolean dryRun = a.remove("--dry-run");
        if (a.size() != 3 || !a.get(0).equals("new")) {
            System.err.println("usage: SocialPosts.java [--dry-run] new <previous.xml> <current.xml>");
            System.exit(2);
        }
        String postiz = env("POSTIZ_API_KEY");
        String claude = env("ANTHROPIC_API_KEY");
        if (!dryRun && (postiz == null || claude == null)) {
            System.out.println("POSTIZ_API_KEY or ANTHROPIC_API_KEY is not set; printing instead of queueing.");
            dryRun = true;
        }
        String base = env("POSTIZ_URL") == null ? "https://api.postiz.com/public/v1" : env("POSTIZ_URL");

        List<Post> previous = readFeed(a.get(1));
        if (previous.isEmpty()) {
            System.out.println("Previous feed has no posts; skipping rather than queueing everything.");
            return;
        }
        Set<String> known = new LinkedHashSet<>();
        previous.forEach(p -> known.add(p.link()));
        List<Post> fresh = new ArrayList<>(readFeed(a.get(2)).stream().filter(p -> !known.contains(p.link())).toList());
        if (fresh.isEmpty()) {
            System.out.println("No new posts.");
            return;
        }
        if (fresh.size() > MAX_NEW) {
            System.out.printf("%d new posts; queueing only %d.%n", fresh.size(), MAX_NEW);
            fresh = fresh.subList(0, MAX_NEW);
        }

        Map<String, String> channels = new LinkedHashMap<>(dryRun && postiz == null ? Map.of() : channels(base, postiz));
        // Explicit channel IDs win over discovery. The LinkedIn one is the company page.
        pin(channels, "bluesky", "POSTIZ_BLUESKY_ID");
        pin(channels, "mastodon", "POSTIZ_MASTODON_ID");
        pin(channels, "linkedin-page", "POSTIZ_LINKEDIN_ID");
        int failures = 0;
        for (Post p : fresh) {
            try {
                queue(p, base, postiz, claude, channels, dryRun);
            } catch (Exception e) {
                failures++;
                System.err.println("Failed for " + p.link() + ": " + e.getMessage());
            }
        }
        if (failures > 0) System.exit(1);
    }

    static void queue(Post p, String base, String postizKey, String claudeKey,
                      Map<String, String> channels, boolean dryRun) throws Exception {
        Document page = Jsoup.connect(p.link()).userAgent("foojay.io").timeout(15000).get();
        List<Author> authors = authors(page);
        String article = articleText(page, p);

        long delay = env("SOCIAL_DELAY_MINUTES") == null ? 180 : Long.parseLong(env("SOCIAL_DELAY_MINUTES"));
        String when = Instant.now().plus(Duration.ofMinutes(delay)).toString();

        ObjectNode body = JSON.createObjectNode();
        body.put("type", "schedule");
        body.put("date", when);
        body.put("shortLink", false);
        body.putArray("tags");
        ArrayNode posts = body.putArray("posts");
        List<String> queued = new ArrayList<>();

        System.out.println("=== " + p.title() + " (" + p.link() + ")");
        for (Platform pl : PLATFORMS) {
            // A LinkedIn company page is a separate Postiz provider with its own identifier.
            String type = pl.id().equals("linkedin") && !channels.containsKey("linkedin")
                    && channels.containsKey("linkedin-page") ? "linkedin-page" : pl.id();
            String id = channels.get(type);
            if (id == null && !dryRun) {
                System.err.println("No " + pl.label() + " channel connected in Postiz; skipping it.");
                continue;
            }
            String text = dryRun && claudeKey == null ? "(not generated: ANTHROPIC_API_KEY is not set)"
                    : write(claudeKey, pl, p, authors, article);
            System.out.println("--- " + pl.label() + " (" + text.length() + "/" + pl.max() + ")\n" + text);
            if (id == null || text.isBlank()) continue;

            ObjectNode post = posts.addObject();
            post.putObject("integration").put("id", id);
            ObjectNode value = post.putArray("value").addObject();
            value.put("content", text);
            value.putArray("image");
            post.putObject("settings").put("__type", type);
            queued.add(pl.label());
        }
        if (dryRun) return;
        if (posts.isEmpty()) throw new IllegalStateException("nothing to queue");

        HttpResponse<String> r = HTTP.send(
                HttpRequest.newBuilder(URI.create(base + "/posts")).timeout(Duration.ofSeconds(60))
                        .header("Authorization", postizKey).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) throw new IllegalStateException("Postiz answered " + r.statusCode() + ": " + r.body());
        System.out.println("Queued in Postiz for " + when);
        notifySlack(p, queued, when, delay);
    }

    /**
     * Tells the team the posts are waiting in Postiz, so they can be edited
     * (mentions, wording) before they go out. A failure here is only logged: the
     * posts are queued either way. SLACK_MENTION_ID, a Slack member ID like U0123ABC, pings one person.
     */
    static void notifySlack(Post p, List<String> platforms, String when, long delayMinutes) {
        String token = env("SLACK_BOT_TOKEN");
        String webhook = env("SLACK_WEBHOOK_URL");
        boolean dm = token != null && env("SLACK_MENTION_ID") != null;
        if (!dm && webhook == null) return;
        String ui = env("POSTIZ_UI_URL") == null ? "https://platform.postiz.com/launches" : env("POSTIZ_UI_URL");
        String id = env("SLACK_MENTION_ID");
        String user = id == null ? null : id.replaceAll("^<@|>$", "");
        // In a direct message a ping is pointless; in the channel it is how you get noticed.
        String mention = dm || user == null ? "" : "<@" + user + "> ";
        String text = mention + "Social posts for *<" + p.link() + "|" + esc(p.title()) + ">* are queued for "
                + String.join(", ", platforms) + " and go out in " + (delayMinutes / 60) + "h"
                + (delayMinutes % 60 == 0 ? "" : " " + (delayMinutes % 60) + "m")
                + " (" + when + ").\nCheck the text and add the mentions in <" + ui + "|Postiz>.";
        try {
            ObjectNode b = JSON.createObjectNode();
            b.put("text", text);
            b.put("unfurl_links", false);
            if (dm) b.put("channel", user);   // a user ID as the channel opens the bot's direct message with them
            HttpRequest.Builder req = HttpRequest
                    .newBuilder(URI.create(dm ? "https://slack.com/api/chat.postMessage" : webhook))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=utf-8");
            if (dm) req.header("Authorization", "Bearer " + token);
            HttpResponse<String> r = HTTP.send(
                    req.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(b))).build(),
                    HttpResponse.BodyHandlers.ofString());
            // chat.postMessage answers 200 with ok:false on errors; the webhook answers 200 with "ok".
            boolean ok = r.statusCode() == 200 && (!dm || JSON.readTree(r.body()).path("ok").asBoolean());
            if (!ok) System.err.println("Slack answered " + r.statusCode() + ": " + r.body());
        } catch (Exception e) {
            System.err.println("Could not notify Slack: " + e.getMessage());
        }
    }

    // Slack mrkdwn treats these three as control characters.
    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static void pin(Map<String, String> channels, String type, String variable) {
        if (env(variable) != null) channels.put(type, env(variable));
    }

    /** Connected accounts by platform: Postiz calls them integrations. */
    static Map<String, String> channels(String base, String key) throws Exception {
        HttpResponse<String> r = HTTP.send(
                HttpRequest.newBuilder(URI.create(base + "/integrations")).timeout(Duration.ofSeconds(30))
                        .header("Authorization", key).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("Postiz integrations answered " + r.statusCode() + ": " + r.body());
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode n : JSON.readTree(r.body())) {
            if (n.path("disabled").asBoolean(false)) continue;
            out.putIfAbsent(n.path("identifier").asText(), n.path("id").asText());
        }
        System.out.println("Postiz channels: " + out.keySet());
        return out;
    }

    static String write(String key, Platform pl, Post p, List<Author> authors, String article) throws Exception {
        StringBuilder who = new StringBuilder();
        if (!authors.isEmpty()) {
            who.append("Credit the author(s) of the article by name in the post.\n");
            for (Author au : authors) {
                String handle = switch (pl.id()) {
                    case "bluesky" -> au.bluesky().replace("https://bsky.app/profile/", "@");
                    case "mastodon" -> mastodonMention(au.mastodon());
                    default -> "";
                };
                who.append("Author: ").append(au.name()).append("\n");
                if (!pl.id().equals("linkedin")) {
                    who.append("    ").append(pl.label()).append(" handle: ")
                            .append(handle.isBlank() ? "(none)" : handle).append("\n");
                }
            }
            if (!pl.id().equals("linkedin")) {
                who.append("Use the handle exactly as provided. If there is none, use the plain name without inventing a handle.\n");
            }
        }
        String prompt = """
                Create a short %s post (max %d characters) for this article:
                Title: %s
                Link: %s

                Article content (the only allowed source of facts):
                \"\"\"
                %s
                \"\"\"

                %s

                Write the message as being the publisher and maintainer of the Foojay website.
                Don't use `–`.
                Avoid typical LLM words (like `reveal`, `dive`, `explore`, `exciting`, `thrilled`,...) and characters (like `—`).
                Never add content, names or brand names that are not in the original article (title, description or text above).
                Only Foojay (the publisher) and the author names and handles provided above are allowed on top of that.
                Don't use `breaks down` or `break down`, use `explains` instead.
                Don't go nuts in the wording, keep it simple, no exaggerations.
                Make it engaging but keep it concise.
                Make sure to include a link to the original article.
                You can include extra relevant hashtags, but only a few, and add them at the end of the post.

                %s

                Answer with the post text only, nothing before or after it.""".formatted(
                pl.label(), pl.max(), p.title(), p.link(), article, who, pl.extra());

        String text = ask(key, prompt);
        if (text.length() > pl.max()) {
            text = ask(key, prompt + "\n\nYour previous answer was " + text.length() + " characters. The limit is "
                    + pl.max() + ". Shorten it, keeping the link:\n" + text);
        }
        if (text.length() > pl.max()) {
            throw new IllegalStateException(pl.label() + " text is " + text.length() + " characters, over " + pl.max());
        }
        return text;
    }

    static String ask(String key, String prompt) throws Exception {
        ObjectNode b = JSON.createObjectNode();
        b.put("model", MODEL);
        b.put("max_tokens", 1024);
        b.putArray("messages").addObject().put("role", "user").put("content", prompt);
        HttpResponse<String> r = HTTP.send(
                HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages")).timeout(Duration.ofSeconds(120))
                        .header("x-api-key", key).header("anthropic-version", "2023-06-01")
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(b))).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("Claude answered " + r.statusCode() + ": " + r.body());
        StringBuilder out = new StringBuilder();
        for (JsonNode block : JSON.readTree(r.body()).path("content")) {
            if (block.path("type").asText().equals("text")) out.append(block.path("text").asText());
        }
        if (out.toString().isBlank()) throw new IllegalStateException("Claude returned no text: " + r.body());
        return out.toString().trim();
    }

    /** The same JSON-LD the article page carries: names plus the social profiles in sameAs. */
    static List<Author> authors(Document page) {
        List<Author> out = new ArrayList<>();
        for (Element s : page.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = JSON.readTree(s.data());
                JsonNode graph = root.has("@graph") ? root.get("@graph") : root;
                for (JsonNode item : graph.isArray() ? graph : List.of(graph)) {
                    JsonNode an = item.path("author");
                    for (JsonNode n : an.isArray() ? an : List.of(an)) {
                        String name = n.path("name").asText("").trim();
                        if (name.isEmpty()) continue;
                        String bluesky = "", mastodon = "";
                        for (JsonNode same : n.path("sameAs")) {
                            String href = same.asText("");
                            String host = host(href);
                            if (host.endsWith("bsky.app")) bluesky = href;
                            else if (isMastodon(href, host)
                                    && (mastodon.isEmpty() || host.contains("social") || host.contains("mastodon"))) mastodon = href;
                        }
                        out.add(new Author(name, bluesky, mastodon));
                    }
                }
            } catch (Exception ignored) {
                // a block that is not the article's metadata
            }
        }
        return out;
    }

    static final List<String> NOT_MASTODON = List.of("youtube.com", "youtu.be", "medium.com", "tiktok.com",
            "instagram.com", "threads.net", "threads.com", "github.com", "gitlab.com", "twitch.tv", "dev.to",
            "substack.com", "hashnode.dev");

    static boolean isMastodon(String href, String host) {
        return href.matches("https?://[^/]+/@[^/]+/?")
                && NOT_MASTODON.stream().noneMatch(h -> host.equals(h) || host.endsWith("." + h));
    }

    static String mastodonMention(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            URI u = URI.create(url);
            if (u.getHost() != null && u.getPath() != null && u.getPath().startsWith("/@")) {
                return "@" + u.getPath().substring(2).replaceAll("/$", "") + "@" + u.getHost();
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return url;
    }

    static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "" : h.toLowerCase();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    static String articleText(Document page, Post p) {
        Element body = page.selectFirst("article .prose");
        if (body == null) body = page.selectFirst("article");
        String t = body == null ? "" : body.text().trim();
        if (t.isEmpty()) t = p.description();
        return t.length() > MAX_ARTICLE_CHARS ? t.substring(0, MAX_ARTICLE_CHARS) : t;
    }

    static List<Post> readFeed(String file) throws Exception {
        Path path = Path.of(file);
        byte[] xml = Files.exists(path) ? Files.readAllBytes(path) : new byte[0];
        if (xml.length == 0) return List.of();
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        NodeList items = doc.getElementsByTagName("item");
        List<Post> out = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            var item = (org.w3c.dom.Element) items.item(i);
            out.add(new Post(text(item, "title"), text(item, "link"), text(item, "description")));
        }
        return out;
    }

    static String text(org.w3c.dom.Element e, String tag) {
        NodeList n = e.getElementsByTagName(tag);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent().trim();
    }

    static String env(String name) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? null : v;
    }
}
