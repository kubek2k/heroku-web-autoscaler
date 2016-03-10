package org.kubek2k.autoscaler.web;

import plan3.msg.queue.Queue;
import plan3.restin.jackson.JsonUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

import org.kubek2k.autoscaler.model.RouterEntries;
import org.kubek2k.autoscaler.model.RouterEntry;
import org.kubek2k.autoscaler.model.RouterStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/drain")
public class StatsDrainResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsDrainResource.class);

    private static final Pattern MSG_LEN_PAT = Pattern.compile("^(\\d+) ");

    private final Queue statsQueue;

    public StatsDrainResource(final Queue statsQueue) {
        this.statsQueue = statsQueue;
    }

    @POST
    @Consumes("application/logplex-1")
    public void consumeBatch(@QueryParam("app") final String appName,
                             @HeaderParam("Logplex-MsgCount") final int messageCount,
                             @HeaderParam("Logplex-Frame-Id") final String frameId,
                             @Context final UriInfo uriInfo,
                             final String logs) {
        final List<RouterEntry> routerEntries = parseMessages(logs, messageCount)
                .stream()
                .map(StatsDrainResource::parseEntry)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        if(!routerEntries.isEmpty()) {
            LOGGER.info("Got some logs to process {}", routerEntries);
            this.statsQueue.enqueue(JsonUtil.asJson(new RouterEntries(frameId, routerEntries, "plan3-tag-advisor-dev")));
        }
    }

    public static List<String> parseMessages(final String blob, final int number) {
        int currentIdx = 0;
        final List<String> result = new ArrayList<>(number);
        while(currentIdx < blob.length()) {
            final String next = blob.substring(currentIdx);
            final Matcher m = MSG_LEN_PAT.matcher(next);
            if(m.find()) {
                final String lineLenS = m.group(1);
                final int messageLen = Integer.parseInt(lineLenS);
                final String message = blob.substring(currentIdx + lineLenS.length() + 1, currentIdx + messageLen);
                result.add(message);
                currentIdx += lineLenS.length() + messageLen + 1;
            }
            else {
                throw new IllegalArgumentException("something wrong has happened. While whole message = " + blob +
                        " next part to parse = " + next + " index = " + currentIdx);
            }
        }
        return result;
    }

    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "^<\\d+>\\d+ (\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{6}\\+\\d{2}:\\d{2}) host ([^ ]+) ([^ ]+) (.*)$");

    public static Optional<RouterEntry> parseEntry(final String message) {
        final Matcher m = ENTRY_PATTERN.matcher(message);
        if(m.matches()) {
            final String system = m.group(2);
            final String type = m.group(3);
            final String entryMessage = m.group(4);
            if("heroku".equals(system) && "router".equals(type)) {
                final Instant timestamp = toInstant(m.group(1));
                return Optional.of(new RouterEntry(timestamp, parseRouterStats(entryMessage)));
            }
            LOGGER.info("Non router message = {} entry, omitting", message);
            return Optional.empty();
        }
        else {
            LOGGER.warn("Get a non-compliant message {}", message);
            return Optional.empty();
        }
    }

    private static final Pattern ROUTER_ENTRY_PATTERN = Pattern.compile(
            "^- at=[^ ]+ method=([A-Z]+) path=\"([^\"]+)\" host=([^ ]+) request_id=[^ ]+ fwd=\"[^\"]+\" dyno=[^ ]+ connect=(\\d+)ms service=(\\d+)ms status=(\\d+) bytes=.*$");

    public static RouterStats parseRouterStats(final String message) {
        final Matcher m = ROUTER_ENTRY_PATTERN.matcher(message);
        if(m.matches()) {
            final String method = m.group(1);
            final String path = m.group(2);
            final String host = m.group(3);
            final String connectS = m.group(4);
            final String serviceS = m.group(5);
            final String statusS = m.group(6);
            return new RouterStats(host,
                    method,
                    path,
                    Integer.parseInt(statusS),
                    Integer.parseInt(connectS),
                    Integer.parseInt(serviceS));
        }
        throw new IllegalArgumentException("Illegal router entry passed");
    }

    private static Instant toInstant(final String timestampString) {
        return LocalDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(
                timestampString)).toInstant(
                ZoneOffset.UTC);
    }

    public static void main(final String[] args) {
        final String s1 = "<158>1 2016-03-10T10:25:13.229818+00:00 host heroku router - at=info method=OPTIONS path=\"/advise\" host=tag-advisor.api.plan3dev.se request_id=0a61dd29-a8e2-4729-9ca0-424d0d67d8a0 fwd=\"5.226.119.97\" dyno=web.1 connect=1ms service=4ms status=200 bytes=425";
        System.out.println(JsonUtil.asJson(parseEntry(s1)));
    }
}
