package com.tricentis.neoload.jmx;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.tricentis.neoload.jmx.JMXProjectParser.extractThreadGroups;
import static com.tricentis.neoload.jmx.JMXProjectParser.trimEmptyLines;
import static org.junit.Assert.assertEquals;

public class JMXProjectParserTest {

    @Test
    public void instrumentWithNLBackendListener_should_throw_when_no_threadgroup() {
        final Exception e = Assertions.assertThrows(
                JMXException.class,
                () -> instrumentWithNLBackendListener("projectWithNoThreadGroup.jmx")
        );
        assertEquals("No thread group found", e.getMessage());
    }

    @Test()
    public void instrumentWithNLBackendListener_should_throw_when_already_contain_backendlistener() throws IOException, JMXException {
        final Exception e = Assertions.assertThrows(
                JMXException.class,
                () -> instrumentWithNLBackendListener("projectWithOneThreadGroup_WithNLBackendListener.jmx")
        );
        assertEquals("NeoLoadBackend listener already present", e.getMessage());
    }

    @Test
    public void testInstrumentWithNLBackendListener() throws IOException, JMXException {
        assertEqualesIgnoreEmptyLines(
                readResourcesFile("projectWithOneThreadGroup_WithNLBackendListener.jmx"),
                instrumentWithNLBackendListener("projectWithOneThreadGroup_WithoutNLBackendListener.jmx"));

        assertEqualesIgnoreEmptyLines(
                readResourcesFile("projectWithTwoThreadGroups_WithNLBackendListener.jmx"),
                instrumentWithNLBackendListener("projectWithTwoThreadGroups_WithoutNLBackendListener.jmx"));
    }

    @Test
    public void testExtractThreadGroups() throws IOException, JMXException {
        List<String> threadGroups = extractThreadGroups(
                readResourcesFile("projectWithNoThreadGroup.jmx"));
        assertEquals(0, threadGroups.size());

        threadGroups = extractThreadGroups(
                readResourcesFile("projectWithOneThreadGroup_WithNLBackendListener.jmx"));
        assertEquals(1, threadGroups.size());
        assertEquals("Thread Group", threadGroups.get(0));

        threadGroups = extractThreadGroups(
                readResourcesFile("projectWithOneThreadGroup_WithoutNLBackendListener.jmx"));
        assertEquals(1, threadGroups.size());
        assertEquals("Thread Group", threadGroups.get(0));

        threadGroups = extractThreadGroups(
                readResourcesFile("projectWithTwoThreadGroups_WithNLBackendListener.jmx"));
        assertEquals(2, threadGroups.size());
        assertEquals("Thread Group A", threadGroups.get(0));
        assertEquals("Thread Group B", threadGroups.get(1));

        threadGroups = extractThreadGroups(
                readResourcesFile("projectWithTwoThreadGroups_WithoutNLBackendListener.jmx"));
        assertEquals(2, threadGroups.size());
        assertEquals("Thread Group A", threadGroups.get(0));
        assertEquals("Thread Group B", threadGroups.get(1));

    }

    private void assertEqualesIgnoreEmptyLines(final String expectedJMXContent, final String actualJMXContent) {
        assertEquals(trimEmptyLines(expectedJMXContent), trimEmptyLines(actualJMXContent));
    }

    private static String readResourcesFile(final String fileName) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader
                (JMXProjectParserTest.class.getClassLoader().getResourceAsStream(fileName), StandardCharsets.UTF_8))) {
            int c = 0;
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
