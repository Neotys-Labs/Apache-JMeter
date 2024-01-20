package com.tricentis.neoload;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static com.tricentis.neoload.ProjectHelper.trimEmptyLines;

public class ProjectHelperTest {

    @Test
    public void testInstrumentWithNLBackendListener() throws IOException {
        assertEqualesIgnoreEmptyLines(
                readResourcesFile("project1WithNLBackendListener.jmx"),
                instrumentWithNLBackendListener("project1WithoutNLBackendListener.jmx"));

        assertEqualesIgnoreEmptyLines(
                readResourcesFile("project2WithNLBackendListener.jmx"),
                instrumentWithNLBackendListener("project2WithoutNLBackendListener.jmx"));
    }

    private static String instrumentWithNLBackendListener(final String fileName) throws IOException {
        return ProjectHelper.getInstance().instrumentWithNLBackendListener(readResourcesFile(fileName));
    }

    private void assertEqualesIgnoreEmptyLines(final String expectedJMXContent, final String actualJMXContent) {
        Assert.assertEquals(trimEmptyLines(expectedJMXContent), trimEmptyLines(actualJMXContent));
    }


    private static String readResourcesFile(final String fileName) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader
                (ProjectHelperTest.class.getClassLoader().getResourceAsStream(fileName), StandardCharsets.UTF_8))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }
}
