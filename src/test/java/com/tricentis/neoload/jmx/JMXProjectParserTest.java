package com.tricentis.neoload.jmx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JMXProjectParserTest {


	private static List<String> extractThreadGroups(final String jmxFile) throws IOException {
		File file = File.createTempFile("jmxProject", ".jmx");
		file.deleteOnExit();

		String content = readResourcesFileContent(jmxFile);
		try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			writer.write(content);
		}
		return new ArrayList<>(new JMXProjectParser(file.toPath()).extractThreadGroups());
	}

	@BeforeAll
	static void setUpAll() throws IOException {
		JMeterBootstrap.initJMeter();
	}

	public static Stream<Arguments> provideJmxFilesForThreadGroupExtraction() {
		return Stream.of(
				Arguments.of("projectWithNoThreadGroup.jmx", new String[]{}),
				Arguments.of("projectWithOneThreadGroup_WithNLBackendListener.jmx", new String[]{"Thread Group"}),
				Arguments.of("projectWithOneThreadGroup_WithoutNLBackendListener.jmx", new String[]{"Thread Group"}),
				Arguments.of("projectWithTwoThreadGroups_WithNLBackendListener.jmx", new String[]{"Thread Group B", "Thread Group A", "setUp Thread Group"}),
				Arguments.of("projectWithTwoThreadGroups_WithoutNLBackendListener.jmx", new String[]{"Thread Group B", "Thread Group A", "setUp Thread Group"}),
				Arguments.of("customThreadGroups.jmx", new String[]{"GuestCheckout_UK", "GuestCheckout_AU"})
		);
	}


	@ParameterizedTest(name = "{index} => file={0}, expectedThreadGroups={1}")
	@MethodSource("provideJmxFilesForThreadGroupExtraction")
	void testExtractThreadGroups(String file, String[] expectedThreadGroups) throws IOException {
		List<String> threadGroups = new ArrayList<>(extractThreadGroups(file));
		assertThat(threadGroups).containsExactlyInAnyOrder(expectedThreadGroups);
	}

	private static String readResourcesFileContent(final String fileName) throws IOException {
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

}
