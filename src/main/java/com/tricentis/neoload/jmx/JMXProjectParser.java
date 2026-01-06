package com.tricentis.neoload.jmx;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.HashTreeTraverser;

public class JMXProjectParser {

	private final Path jmxPath;
	private final JMeterInfoExtractor jMeterInfoExtractor = new JMeterInfoExtractor();

	static class JMeterInfoExtractor implements HashTreeTraverser {
		private String testPlanName;
		private final Set<String> threadGroupNames = new HashSet<>();

		@Override
		public void addNode(final Object node, final HashTree subTree) {
			if (node instanceof AbstractThreadGroup) {
				AbstractThreadGroup threadGroup = (AbstractThreadGroup) node;
				if (threadGroup.isEnabled()) {
					threadGroupNames.add(threadGroup.getName());
				}
			}
			if (node instanceof TestPlan) {
				TestPlan testPlan = (TestPlan) node;
				if (testPlan.isEnabled()) {
					this.testPlanName = testPlan.getName();
				}
			} else {
				this.testPlanName = "JMeter Test Plan";
			}
		}

		@Override
		public void subtractNode() {
		}

		@Override
		public void processPath() {
		}

		public Set<String> getThreadGroupNames() {
			return threadGroupNames;
		}

		public String getTestPlanName() {
			return testPlanName;
		}
	}

	public JMXProjectParser(final Path jmxPath) {
		this.jmxPath = jmxPath;
		try {
			loadData();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void loadData() throws IOException {
		HashTree tree = SaveService.loadTree(this.jmxPath.toFile());
		tree.traverse(jMeterInfoExtractor);
	}

	public Set<String> extractThreadGroups() {
		return jMeterInfoExtractor.getThreadGroupNames();

	}

	public String extractTestPlan() {
		return jMeterInfoExtractor.getTestPlanName();
	}
}
