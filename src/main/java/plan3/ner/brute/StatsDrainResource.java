package plan3.ner.brute;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/drain")
public class StatsDrainResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsDrainResource.class);

    private static final Pattern MSG_LEN_PAT = Pattern.compile("^(\\d+) ");

    @POST
    @Consumes("application/logplex-1")
    public void consumeBatch(@QueryParam("app") final String appName,
                             @HeaderParam("Logplex-MsgCount") final int messageCount,
                             @Context final UriInfo uriInfo,
                             final String logs) {
        final List<RouterEntry> routerEntries = parseMessages(logs, messageCount)
                .stream()
                .map(StatsDrainResource::parseEntry)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        LOGGER.info("Got some logs {}", routerEntries);
    }

    public static List<String> parseMessages(final String blob, final int number) {
        int currentIdx = 0;
        final List<String> result = new ArrayList<>(number);
        while (currentIdx < blob.length()) {
            final String next = blob.substring(currentIdx);
            final Matcher m = MSG_LEN_PAT.matcher(next);
            if (m.find()) {
                final String len = m.group(1);
                final int messageLen = Integer.parseInt(len);
                final String message = blob.substring(currentIdx + len.length() + 1, currentIdx + messageLen);
                result.add(message);
                currentIdx += len.length() + messageLen + 1;
            } else {
                throw new IllegalArgumentException("something wrong has happened. While whole message = " + blob +
                        " next part to parse = " + next + " index = " + currentIdx);
            }
        }
        return result;
    }

    private static final Pattern ENTRY_PATTERN = Pattern.compile("^<\\d+>\\d+ (\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{6}\\+\\d{2}:\\d{2}) host ([^ ]+) ([^ ]+) (.*)$");

    public static Optional<RouterEntry> parseEntry(final String message) {
        final Matcher m = ENTRY_PATTERN.matcher(message);
        if (m.matches()) {
            final String timestampString = m.group(1);
            final String system = m.group(2);
            final String type = m.group(3);
            final String entryMessage = m.group(4);
            if ("heroku".equals(system) && "router".equals(type)) {
                return Optional.of(new RouterEntry(timestampString, entryMessage));
            }
            LOGGER.info("Non router message = {} entry, omitting", message);
            return Optional.empty();
        } else {
            LOGGER.warn("Get a non-compliant message {}", message);
            return Optional.empty();
        }
    }

    private static class RouterEntry {
        private final String timestamp;

        private final String message;

        public RouterEntry(final String timestamp, final String message) {
            this.timestamp = timestamp;
            this.message = message;
        }

        @Override
        public String toString() {
            return "RouterEntry{" +
                    "timestamp=" + this.timestamp +
                    ", message='" + this.message + '\'' +
                    '}';
        }
    }

    public static void main(final String[] args) {
//        final String s1 = "<158>1 2016-03-10T10:25:13.229818+00:00 host heroku router - at=info method=OPTIONS path=\"/advise\" host=tag-advisor.api.plan3dev.se request_id=0a61dd29-a8e2-4729-9ca0-424d0d67d8a0 fwd=\"5.226.119.97\" dyno=web.1 connect=1ms service=4ms status=200 bytes=425";
        final String s1 = "<190>1 2016-03-10T12:32:26.864447+00:00 host app web.1 - 5.226.119.97 - - [10/Mar/2016:12:32:26 +0000] \"POST /advise HTTP/1.1\" 200 2 \"https://cms-playground.plan3dev.se/workbench/o65B;newsroom=fvn\" \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/48.0.2564.116 Safari/537.36";

        System.out.println(parseEntry(s1));
    }
}
