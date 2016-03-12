package org.kubek2k.autoscaler.web;

import plan3.msg.queue.Queue;
import plan3.restin.jackson.JsonUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;
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
    private final List<String> disabledPaths;

    public StatsDrainResource(final Queue statsQueue, final List<String> disabledPaths) {
        this.statsQueue = statsQueue;
        this.disabledPaths = disabledPaths;
    }

    @POST
    @Consumes("application/logplex-1")
    public void consumeBatch(@NotNull @QueryParam("app") final String appName,
                             @HeaderParam("Logplex-MsgCount") final int messageCount,
                             @HeaderParam("Logplex-Frame-Id") final String frameId,
                             @Context final UriInfo uriInfo,
                             final String logs) {
        final List<RouterEntry> routerEntries = parseMessages(logs, messageCount)
                .stream()
                .map(StatsDrainResource::parseEntry)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::notDisabledPath)
                .collect(Collectors.toList());
        if(!routerEntries.isEmpty()) {
            LOGGER.info("Got some logs to process frame id = {} no of logs = {}", frameId, routerEntries.size());
            this.statsQueue.enqueue(JsonUtil.asJson(new RouterEntries(frameId, routerEntries, appName)));
        }
    }

    private boolean notDisabledPath(final RouterEntry routerEntry) {
        return !this.disabledPaths.stream()
                .filter(patternS -> routerEntry.getMessage().getPath().startsWith(patternS))
                .findAny()
                .isPresent();
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
            LOGGER.debug("Non router message = {} entry, omitting", message);
            return Optional.empty();
        }
        else {
            LOGGER.warn("Get a non-compliant message {}", message);
            return Optional.empty();
        }
    }

    private static final Pattern ROUTER_ENTRY_PATTERN = Pattern.compile("([^ =]+)=((\"([^\"]+)\")|([^ ]+))");

    public static RouterStats parseRouterStats(final String message) {
        final Matcher m = ROUTER_ENTRY_PATTERN.matcher(message.substring(2));
        final Map<String, String> map = new HashMap<>();
        while(m.find()) {
            if(m.group(4) != null) {
                map.put(m.group(1), m.group(4));
            }
            else {
                map.put(m.group(1), m.group(5));
            }
        }
        return new RouterStats(map.get("host"),
                map.get("method"),
                map.get("path"),
                Integer.parseInt(stripMs(map.get("connect"))),
                Integer.parseInt(stripMs(map.get("service"))),
                Integer.parseInt(map.get("status")));
    }

    private static String stripMs(final String s) {
        return s.substring(0, s.length() - 2);
    }

    private static Instant toInstant(final String timestampString) {
        return LocalDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(
                timestampString)).toInstant(
                ZoneOffset.UTC);
    }
}
