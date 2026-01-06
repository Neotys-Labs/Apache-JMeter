package com.tricentis.neoload.jmx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;

public class JMeterBootstrap {

	private static Path createJMeterFolders() throws IOException {
		Path tempDir = Files.createTempDirectory("jmeter-test-");
		Path bin = tempDir.resolve("bin");
		Files.createDirectory(bin);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try (Stream<Path> paths = Files.walk(tempDir)) {
				paths.sorted(Comparator.reverseOrder())
						.forEach(path -> {
							try {
								Files.deleteIfExists(path);
							} catch (IOException ignored) {
							}
						});
			} catch (IOException ignored) {
			}
		}));
		return tempDir;
	}


	private static Path createJMeterHome() throws IOException {
		Path jmeterHome = createJMeterFolders();
		copyResource("jmeter.properties", jmeterHome.resolve("bin"));
		copyResource("saveservice.properties", jmeterHome.resolve("bin"));
		return jmeterHome;
	}

	static void initJMeter() throws IOException {
		if (JMeterUtils.getJMeterProperties() != null) {
			return;
		}
		Path jMeterHome = createJMeterHome();

		JMeterUtils.setJMeterHome(jMeterHome.toAbsolutePath().toString());
		JMeterUtils.loadJMeterProperties(
				jMeterHome
						.resolve("bin")
						.resolve("jmeter.properties").toString()
		);
		JMeterUtils.initLocale();
		SaveService.loadProperties();
	}

	private static void copyResource(String resourceName, Path targetDir)
			throws IOException {

		try (InputStream is =
						 JMeterBootstrap.class
								 .getClassLoader()
								 .getResourceAsStream(resourceName)) {

			if (is == null) {
				throw new IllegalArgumentException(
						"Resource not found: " + resourceName
				);
			}

			Files.copy(
					is,
					targetDir.resolve(resourceName),
					StandardCopyOption.REPLACE_EXISTING
			);
		}
	}
}