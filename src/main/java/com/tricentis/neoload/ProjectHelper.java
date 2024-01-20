package com.tricentis.neoload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class ProjectHelper {
    private static ProjectHelper INSTANCE = new ProjectHelper();


    private static final String TEST_PLAN_END_TAG_1 = "</hashTree>  </hashTree></jmeterTestPlan>";
    private static final String TEST_PLAN_END_TAG_2 = "<hashTree/>  </hashTree></jmeterTestPlan>";

    private static final String BACKEND_LISTENER_NODE_CONTENT_1 = "<BackendListener guiclass=\"BackendListenerGui\" testclass=\"BackendListener\" testname=\"NeoLoad Backend Listener\" enabled=\"true\"><elementProp name=\"arguments\" elementType=\"Arguments\" guiclass=\"ArgumentsPanel\" testclass=\"Arguments\" enabled=\"true\"><collectionProp name=\"Arguments.arguments\"/></elementProp><stringProp name=\"classname\">com.tricentis.neoload.NeoLoadBackend</stringProp></BackendListener><hashTree/></hashTree></hashTree></jmeterTestPlan>";
    private static final String BACKEND_LISTENER_NODE_CONTENT_2 = "<hashTree>" + BACKEND_LISTENER_NODE_CONTENT_1;

    private ProjectHelper() {
    }

    public static ProjectHelper getInstance() {
        return INSTANCE;
    }

    public void instrumentWithNLBackendListener(final Path jmxPath) throws IOException {
        final String jmxContent = Files.lines(jmxPath).collect(Collectors.joining(""));
        final String newJmxContent = instrumentWithNLBackendListener(jmxContent);
        Files.write(jmxPath, newJmxContent.getBytes());
    }

    String instrumentWithNLBackendListener(String jmxContent) {
        jmxContent = trimEmptyLines(jmxContent);
        if (jmxContent.contains(TEST_PLAN_END_TAG_1)) {
            return jmxContent.replace(TEST_PLAN_END_TAG_1, BACKEND_LISTENER_NODE_CONTENT_1);
        } else if (jmxContent.contains(TEST_PLAN_END_TAG_2)) {
            return jmxContent.replace(TEST_PLAN_END_TAG_2, BACKEND_LISTENER_NODE_CONTENT_2);
        }
        return jmxContent;
    }

    static String trimEmptyLines(final String s) {
        return s.replace("\n", "").replace("\r", "");
    }


    public static void main(String[] args) throws IOException {
        INSTANCE.instrumentWithNLBackendListener(Paths.get("C:\\GoogleDrive\\Documents\\work\\OSS\\ApacheJMeter\\projects\\tmp.jmx"));
    }
}
