package plan3.ner.brute;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        LOGGER.info("Got some logs {}", parseMessages(logs, messageCount));
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
                currentIdx += len.length() + messageLen;
            } else {
                throw new IllegalArgumentException("something wrong has happened. While whole message = " + blob +
                        " next part to parse = " + next + " index = " + currentIdx);
            }
        }
        return result;
    }

    public static void main(final String[] args) {
        System.out.println(parseMessages("300 <158>1 2016-03-10T10:18:48.436558+00:00 host heroku router - at=info method=GET path=\"/store/default-authors/bcea2578-e221-4ef2-9036-ba47a9c5556b\" host=article.api.plan3dev.se request_id=45ca3ab6-09e4-42f5-bbe5-3419ac3f4671 fwd=\"37.247.7.182\" dyno=web.1 connect=0ms service=20ms status=200 bytes=294", 1));
        System.out.println(parseMessages("304 <45>1 2016-03-10T10:02:41.761316+00:00 host heroku web.1 - source=web.1 dyno=heroku.24944085.59356af4-7746-45e4-b753-69fe0c1faf28 sample#memory_total=278.12MB sample#memory_rss=277.97MB sample#memory_cache=0.15MB sample#memory_swap=0.00MB sample#memory_pgpgin=84816pages sample#memory_pgpgout=47344pages" +
                "304 <45>1 2016-03-10T10:02:41.761316+00:00 host heroku web.1 - source=web.1 dyno=heroku.24944085.59356af4-7746-45e4-b753-69fe0c1faf28 sample#memory_total=278.12MB sample#memory_rss=277.97MB sample#memory_cache=0.15MB sample#memory_swap=0.00MB sample#memory_pgpgin=84816pages sample#memory_pgpgout=47344pages", 2));
    }
}
