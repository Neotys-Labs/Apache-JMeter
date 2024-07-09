package com.tricentis.neoload.jmx;

import org.junit.jupiter.api.Assertions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.tricentis.neoload.jmx.JMXProjectParser.trimEmptyLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JMXProjectParserTest {

    @Test
    void instrumentWithNLBackendListener_should_throw_when_no_threadgroup() {
        final Exception e = Assertions.assertThrows(
                JMXException.class,
                () -> instrumentWithNLBackendListener("projectWithNoThreadGroup.jmx")
        );
        assertEquals("No thread group found", e.getMessage());
    }

    @Test()
    void instrumentWithNLBackendListener_should_throw_when_already_contain_backendlistener() {
        final Exception e = Assertions.assertThrows(
                JMXException.class,
                () -> instrumentWithNLBackendListener("projectWithOneThreadGroup_WithNLBackendListener.jmx")
        );
        assertEquals("NeoLoadBackend listener already present", e.getMessage());
    }

    @Test
    void testInstrumentWithNLBackendListener() throws IOException, JMXException {
        assertEqualsIgnoreEmptyLines(
                readResourcesFile("projectWithOneThreadGroup_WithNLBackendListener.jmx"),
                instrumentWithNLBackendListener("projectWithOneThreadGroup_WithoutNLBackendListener.jmx"));

        assertEqualsIgnoreEmptyLines(
                readResourcesFile("projectWithTwoThreadGroups_WithNLBackendListener.jmx"),
                instrumentWithNLBackendListener("projectWithTwoThreadGroups_WithoutNLBackendListener.jmx"));
    }

    private static List<String> extractThreadGroups(final String jmx) throws IOException {
        return new ArrayList<>(JMXProjectParser.extractThreadGroups(JMXProjectParserTest.readResourcesFile(jmx)));
    }

    @Test
    void testExtractThreadGroups() throws IOException {
        List<String> threadGroups = new ArrayList<>(extractThreadGroups("projectWithNoThreadGroup.jmx"));
        assertEquals(0, threadGroups.size());

        threadGroups = extractThreadGroups("projectWithOneThreadGroup_WithNLBackendListener.jmx");
        assertEquals(1, threadGroups.size());
        assertEquals("Thread Group", threadGroups.get(0));

        threadGroups = extractThreadGroups("projectWithOneThreadGroup_WithoutNLBackendListener.jmx");
        assertEquals(1, threadGroups.size());
        assertEquals("Thread Group", threadGroups.get(0));

        threadGroups = extractThreadGroups("projectWithTwoThreadGroups_WithNLBackendListener.jmx");
        assertEquals(2, threadGroups.size());
        assertEquals("Thread Group B", threadGroups.get(0));
        assertEquals("Thread Group A", threadGroups.get(1));

        threadGroups = extractThreadGroups("projectWithTwoThreadGroups_WithoutNLBackendListener.jmx");
        assertEquals(2, threadGroups.size());
        assertEquals("Thread Group B", threadGroups.get(0));
        assertEquals("Thread Group A", threadGroups.get(1));

    }

    private void assertEqualsIgnoreEmptyLines(final String expectedJMXContent, final String actualJMXContent) {
        assertEquals(trimEmptyLines(expectedJMXContent), trimEmptyLines(actualJMXContent));
    }

    private static String readResourcesFile(final String fileName) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        final InputStream is = JMXProjectParserTest.class.getClassLoader().getResourceAsStream(fileName);
        if (is == null) {
            return "";
        }
        try (Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }

    private static String instrumentWithNLBackendListener(final String fileName) throws IOException, JMXException {
        return JMXProjectParser.instrumentWithNLBackendListener(readResourcesFile(fileName));
    }
}
