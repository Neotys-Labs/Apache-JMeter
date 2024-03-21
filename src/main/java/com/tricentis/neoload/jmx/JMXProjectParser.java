package com.tricentis.neoload.jmx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JMXProjectParser {


    private static final String TEST_PLAN_END_TAG = "</hashTree>  </hashTree></jmeterTestPlan>";
    private static final String BACKEND_LISTENER_NODE_CONTENT = "<BackendListener guiclass=\"BackendListenerGui\" testclass=\"BackendListener\" testname=\"NeoLoad Backend Listener\" enabled=\"true\"><elementProp name=\"arguments\" elementType=\"Arguments\" guiclass=\"ArgumentsPanel\" testclass=\"Arguments\" enabled=\"true\"><collectionProp name=\"Arguments.arguments\"/></elementProp><stringProp name=\"classname\">com.tricentis.neoload.NeoLoadBackend</stringProp></BackendListener><hashTree/></hashTree></hashTree></jmeterTestPlan>";
    private static final String BACKEND_LISTENER_STRING_PROP = "<stringProp name=\"classname\">com.tricentis.neoload.NeoLoadBackend</stringProp>";

    private static final String REGEXP_EXTRACT_THREAD_GROUPS = "<ThreadGroup([^>]+) testname=\"([^>\"]+)\"";
    private static final Pattern PATTERN_EXTRACT_THREAD_GROUPS = Pattern.compile(REGEXP_EXTRACT_THREAD_GROUPS);

    private JMXProjectParser() {
    }

    public static Set<String> extractThreadGroups(final Path jmxPath) throws IOException {
        final String jmxContent = read(jmxPath);
        return extractThreadGroups(jmxContent);
    }

    public static Set<String> extractThreadGroups(final String jmxContent) {
        final Matcher matcher = PATTERN_EXTRACT_THREAD_GROUPS.matcher(jmxContent);
        final Set<String> threadGroups = new HashSet<>();
        while (matcher.find()) {
            threadGroups.add(matcher.group(2));
        }
        return threadGroups;
    }


    public static void instrumentWithNLBackendListener(final Path jmxPath) throws IOException, JMXException {
        final String jmxContent = read(jmxPath);
        final String newJmxContent = instrumentWithNLBackendListener(jmxContent);
        Files.write(jmxPath, newJmxContent.getBytes());
    }

    static String instrumentWithNLBackendListener(String jmxContent) throws JMXException {
        if (extractThreadGroups(jmxContent).isEmpty()) {
            throw new JMXException("No thread group found");
        }
        if (jmxContent.contains(BACKEND_LISTENER_STRING_PROP)) {
            throw new JMXException("NeoLoadBackend listener already present");
        }
        jmxContent = trimEmptyLines(jmxContent);
        if (jmxContent.contains(TEST_PLAN_END_TAG)) {
            return jmxContent.replace(TEST_PLAN_END_TAG, BACKEND_LISTENER_NODE_CONTENT);
        }
        return jmxContent;
    }

    private static String read(final Path path) throws IOException {
        try (Stream<String> input = Files.lines(path)) {
            return input.collect(Collectors.joining(""));
        }
    }

    static String trimEmptyLines(final String s) {
        return s.replace("\n", "").replace("\r", "");
    }

    public static void main(String[] args) throws IOException, JMXException {
        instrumentWithNLBackendListener(Paths.get("C:\\GoogleDrive\\Documents\\work\\OSS\\ApacheJMeter\\projects\\tmp.jmx"));
        extractThreadGroups(Paths.get("C:\\GoogleDrive\\Documents\\work\\OSS\\ApacheJMeter\\projects\\tmp.jmx")).forEach(System.out::println);
    }
}
