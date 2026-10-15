///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS org.jsoup:jsoup:1.18.3
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes a Mastodon, Bluesky and LinkedIn text for every post that went live,
 * with Claude working from the article itself, and sends them as a Slack direct
 * message, each in a code block so it can be copied as it is.
 *
 *   new <previous.xml> <current.xml>   posts in current that previous lacks
 *
 * Same contract as SlackPosts.java: the previous feed is from BEFORE the
 * deploy, so no state is kept, and an empty previous feed aborts instead of
 * writing texts for the whole feed.
 *
 * Environment:
 *   ANTHROPIC_API_KEY  writes the texts
 *   SLACK_BOT_TOKEN    a bot with chat:write
 *   SLACK_CHANNEL_ID   the channel the message is posted in; the bot must be a member
 *                      of a private one. Without it the message goes to SLACK_MENTION_ID
 *                      as a direct message.
 *   SLACK_MENTION_ID   a Slack member ID, the fallback when there is no channel
 *
 * Any of them unset, or `--dry-run`: prints the texts and sends nothing.
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

    static final List<Platform> PLATFORMS = List.of(
            new Platform("mastodon", "Mastodon", 500,
                    "Add line breaks to separate text, link, etc.\nWhen you mention Foojay, add the emoji `:foojay:`"),
            new Platform("bluesky", "Bluesky", 300, ""),
            new Platform("linkedin", "LinkedIn", 1000,
                    "Add line breaks to separate text, link, etc.\n"
                            + "Mention each author as `@Full Name` exactly as provided (the full name is needed to turn it into a LinkedIn mention later). "
                            + "Don't add a link to the LinkedIn profile(s) of the author(s).\n"
                            + "Use plain text only: no bold, no italic, no markdown (no `**`, no `*`, no `_`) and no unicode bold/italic characters.\n"
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
        String claude = env("ANTHROPIC_API_KEY");
        String token = env("SLACK_BOT_TOKEN");
        String user = env("SLACK_CHANNEL_ID") != null ? env("SLACK_CHANNEL_ID")
                : env("SLACK_MENTION_ID") == null ? null : env("SLACK_MENTION_ID").replaceAll("^<@|>$", "");
        if (!dryRun && (claude == null || token == null || user == null)) {
            System.out.println("ANTHROPIC_API_KEY, SLACK_BOT_TOKEN or SLACK_CHANNEL_ID is not set; printing instead of sending.");
            dryRun = true;
        }

        List<Post> previous = readFeed(a.get(1));
        if (previous.isEmpty()) {
            System.out.println("Previous feed has no posts; skipping rather than writing texts for everything.");
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
            System.out.printf("%d new posts; writing texts for %d.%n", fresh.size(), MAX_NEW);
            fresh = fresh.subList(0, MAX_NEW);
        }

        int failures = 0;
        for (Post p : fresh) {
            try {
                send(p, claude, token, user, dryRun);
            } catch (Exception e) {
                failures++;
                System.err.println("Failed for " + p.link() + ": " + e.getMessage());
            }
        }
        if (failures > 0) System.exit(1);
    }

    static void send(Post p, String claudeKey, String token, String user, boolean dryRun) throws Exception {
        Document page = Jsoup.connect(p.link()).userAgent("foojay.io").timeout(15000).get();
        List<Author> authors = authors(page);
        String article = articleText(page, p);

        StringBuilder message = new StringBuilder("*New on Foojay.io:* <" + p.link() + "|" + esc(p.title()) + ">\n");
        for (Platform pl : PLATFORMS) {
            String text = claudeKey == null ? "(not generated: ANTHROPIC_API_KEY is not set)"
                    : write(claudeKey, pl, p, authors, article);
            message.append("\n*").append(pl.label()).append("* (").append(text.length()).append("/").append(pl.max())
                    .append(")\n```\n").append(text.replace("```", "'''")).append("\n```\n");
        }

        System.out.println("=== " + p.link() + "\n" + message);
        if (dryRun) return;
        post(token, user, message.toString());
        System.out.println("Sent to Slack.");
    }

    /** A channel ID, or a user ID, which opens the bot's direct message with that person. */
    static void post(String token, String user, String text) throws Exception {
        ObjectNode b = JSON.createObjectNode();
        b.put("channel", user);
        b.put("text", text);
        b.put("unfurl_links", false);
        b.put("unfurl_media", false);
        HttpResponse<String> r = HTTP.send(
                HttpRequest.newBuilder(URI.create("https://slack.com/api/chat.postMessage")).timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(b))).build(),
                HttpResponse.BodyHandlers.ofString());
        // Slack answers 200 with ok:false on errors.
        if (r.statusCode() != 200 || !JSON.readTree(r.body()).path("ok").asBoolean()) {
            throw new IllegalStateException("Slack answered " + r.statusCode() + ": " + r.body());
        }
    }

    static String write(String key, Platform pl, Post p, List<Author> authors, String article) throws Exception {
        StringBuilder who = new StringBuilder();
        if (!authors.isEmpty()) {
            who.append("Credit the author(s) of the article by name in the post.\n")
                    .append("Number of authors: ").append(authors.size()).append("\n")
                    .append("Use the handle or link exactly as provided for the ").append(pl.label())
                    .append(" mention. If no handle or link is provided for an author, use the plain name without inventing a handle.\n");
            for (Author au : authors) {
                String handle = switch (pl.id()) {
                    case "bluesky" -> blueskyMention(au.bluesky());
                    case "mastodon" -> mastodonMention(au.mastodon());
                    default -> "@" + au.name(); // LinkedIn has no text handles
                };
                who.append("Author: ").append(au.name()).append("\n    ").append(pl.label())
                        .append(" handle/link: ").append(handle.isBlank() ? "(none)" : handle).append("\n");
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
                Never put a link or an @mention between parentheses.
                You can include extra relevant hashtags, but only a few, and add them at the end of the post.

                %s

                Answer with the post text only, nothing before or after it.""".formatted(
                pl.label(), pl.max(), p.title(), p.link(), article, who, pl.extra());

        String text = unwrapLinks(ask(key, prompt));
        if (text.length() > pl.max()) {
            text = unwrapLinks(ask(key, prompt + "\n\nYour previous answer was " + text.length() + " characters. The limit is "
                    + pl.max() + ". Shorten it, keeping the link:\n" + text));
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

    static final String LINK_OR_MENTION =
            "https?://[^\\s()]+|@[\\w-]+(?:\\.[\\w-]+)*(?:@[\\w-]+(?:\\.[\\w-]+)*)?";

    /** A parenthesis glued to a link or mention is read as part of it, so give it a space. */
    static String unwrapLinks(String text) {
        return text.replaceAll("\\((?=" + LINK_OR_MENTION + ")", "( ")
                .replaceAll("(" + LINK_OR_MENTION + ")\\)", "$1 )");
    }

    static String blueskyMention(String url) {
        if (url == null || url.isBlank()) return "";
        String handle = url.replaceFirst("^https?://(www\\.)?bsky\\.app/profile/", "").replaceAll("/.*$", "");
        return handle.isEmpty() ? url : "@" + handle.replaceFirst("^@", "");
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

    // Slack mrkdwn treats these three as control characters.
    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String env(String name) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? null : v;
    }
}
