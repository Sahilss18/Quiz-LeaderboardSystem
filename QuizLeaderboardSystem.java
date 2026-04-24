import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuizLeaderboardSystem {

    private static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final String REG_NO = "2024CS101";

    private static final Pattern STRING_FIELD_TEMPLATE = Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"((?:\\\\\\.|[^\\\\\"])*)\\\"");
    private static final Pattern INT_FIELD_TEMPLATE = Pattern.compile("\\\"%s\\\"\\s*:\\s*(-?\\d+)");

    public static void main(String[] args) {
        String regNo = args.length > 0 && !args[0].isBlank() ? args[0] : REG_NO;
        try {
            HttpClient client = HttpClient.newHttpClient();

            Set<String> seenEvents = new HashSet<>();
            Map<String, Integer> scores = new HashMap<>();

            for (int i = 0; i < 10; i++) {
                System.out.println("Polling index: " + i);

                String url = BASE_URL + "/quiz/messages?regNo=" + URLEncoder.encode(regNo, StandardCharsets.UTF_8) + "&poll=" + i;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                try {
                    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        System.err.println("Skipping poll " + i + ": HTTP " + response.statusCode() + " - " + response.body());
                        continue;
                    }

                    String body = response.body();

                    for (String eventJson : extractArrayObjects(body, "events")) {
                        String roundId = extractStringField(eventJson, "roundId");
                        String participant = extractStringField(eventJson, "participant");
                        int score = extractIntField(eventJson, "score");

                        if (roundId == null || participant == null) {
                            continue;
                        }

                        participant = participant.trim();
                        String eventKey = (roundId.trim() + "_" + participant).toLowerCase();

                        if (!seenEvents.contains(eventKey)) {
                            seenEvents.add(eventKey);
                            System.out.println("Processed key: " + eventKey);
                            System.out.println("Adding score: " + participant + " + " + score);

                            scores.put(participant, scores.getOrDefault(participant, 0) + score);
                        } else {
                            System.out.println("Duplicate ignored: " + eventKey);
                        }
                    }
                } catch (RuntimeException ex) {
                    System.err.println("Skipping poll " + i + ": " + ex.getMessage());
                }

                Thread.sleep(5000);
            }

            List<Map.Entry<String, Integer>> leaderboard = new ArrayList<>(scores.entrySet());
            leaderboard.sort(Comparator
                    .comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
                    .thenComparing(Map.Entry::getKey));

            int totalScore = 0;
                JSONArray leaderboardJson = new JSONArray();

            System.out.println();
            System.out.println("Leaderboard:");

            for (int i = 0; i < leaderboard.size(); i++) {
                Map.Entry<String, Integer> entry = leaderboard.get(i);
                String participant = entry.getKey();
                int score = entry.getValue();

                totalScore += score;

                JSONObject obj = new JSONObject();
                obj.put("participant", participant);
                obj.put("totalScore", score);
                leaderboardJson.put(obj);

                System.out.println(participant + " -> " + score);
            }

            System.out.println();
            System.out.println("Total Score: " + totalScore);

                JSONObject submitBody = new JSONObject();
                submitBody.put("regNo", regNo);
                submitBody.put("leaderboard", leaderboardJson);

                System.out.println("Final JSON:");
                System.out.println(submitBody.toString(2));

            HttpRequest submitRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/quiz/submit"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(submitBody.toString()))
                    .build();

            HttpResponse<String> submitResponse = client.send(submitRequest, BodyHandlers.ofString());

            System.out.println();
            System.out.println("Submission Response:");
            System.out.println(submitResponse.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Execution interrupted: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Network error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Failed to process response: " + e.getMessage());
        }
    }

    private static List<String> extractArrayObjects(String json, String arrayKey) {
        int keyIndex = json.indexOf('"' + arrayKey + '"');
        if (keyIndex < 0) {
            return List.of();
        }

        int arrayStart = json.indexOf('[', keyIndex);
        if (arrayStart < 0) {
            return List.of();
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        int arrayEnd = -1;

        for (int i = arrayStart; i < json.length(); i++) {
            char ch = json.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
            } else if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    arrayEnd = i;
                    break;
                }
            }
        }

        if (arrayEnd < 0 || arrayEnd <= arrayStart + 1) {
            return List.of();
        }

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        List<String> objects = new ArrayList<>();

        depth = 0;
        inString = false;
        escaped = false;
        int objectStart = -1;

        for (int i = 0; i < arrayContent.length(); i++) {
            char ch = arrayContent.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    objects.add(arrayContent.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }

        return objects;
    }

    private static String extractStringField(String json, String fieldName) {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD_TEMPLATE.pattern(), Pattern.quote(fieldName))).matcher(json);
        return matcher.find() ? unescapeJson(matcher.group(1)) : null;
    }

    private static int extractIntField(String json, String fieldName) {
        Matcher matcher = Pattern.compile(String.format(INT_FIELD_TEMPLATE.pattern(), Pattern.quote(fieldName))).matcher(json);
        if (!matcher.find()) {
            throw new RuntimeException("Missing numeric field: " + fieldName);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private static String unescapeJson(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                switch (ch) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    default -> result.append(ch);
                }
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static final class JSONArray {
        private final List<Object> values = new ArrayList<>();

        JSONArray put(Object value) {
            values.add(value);
            return this;
        }

        @Override
        public String toString() {
            return toString(0);
        }

        String toString(int indentSize) {
            return toJsonArray(values, indentSize, 0);
        }
    }

    private static final class JSONObject {
        private final Map<String, Object> values = new LinkedHashMap<>();

        JSONObject put(String key, Object value) {
            values.put(key, value);
            return this;
        }

        @Override
        public String toString() {
            return toString(0);
        }

        String toString(int indentSize) {
            return toJsonObject(values, indentSize, 0);
        }
    }

    private static String toJsonValue(Object value, int indentSize, int currentIndent) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof JSONObject) {
            return toJsonObject(((JSONObject) value).values, indentSize, currentIndent);
        }
        if (value instanceof JSONArray) {
            return toJsonArray(((JSONArray) value).values, indentSize, currentIndent);
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private static String toJsonObject(Map<String, Object> map, int indentSize, int currentIndent) {
        if (map.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder("{");
        boolean pretty = indentSize > 0;
        int nextIndent = currentIndent + indentSize;
        int index = 0;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (index++ > 0) {
                sb.append(',');
            }
            if (pretty) {
                sb.append('\n').append(" ".repeat(nextIndent));
            }
            sb.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            if (pretty) {
                sb.append(' ');
            }
            sb.append(toJsonValue(entry.getValue(), indentSize, nextIndent));
        }

        if (pretty) {
            sb.append('\n').append(" ".repeat(currentIndent));
        }
        sb.append('}');
        return sb.toString();
    }

    private static String toJsonArray(List<Object> list, int indentSize, int currentIndent) {
        if (list.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        boolean pretty = indentSize > 0;
        int nextIndent = currentIndent + indentSize;

        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            if (pretty) {
                sb.append('\n').append(" ".repeat(nextIndent));
            }
            sb.append(toJsonValue(list.get(i), indentSize, nextIndent));
        }

        if (pretty) {
            sb.append('\n').append(" ".repeat(currentIndent));
        }
        sb.append(']');
        return sb.toString();
    }
}