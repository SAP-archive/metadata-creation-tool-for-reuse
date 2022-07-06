package com.sap.ospo.reuseassistant;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections4.CollectionUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class ReuseAssistant {

	public static final String REUSE_METADATA_PROPOSAL_BRANCH = "reuse-metadata-proposal";
	public static final String GITHUB_ACCESS_TOKEN = "GITHUB_ACCESS_TOKEN";
	public static final String COPYRIGHT_OWNER = "COPYRIGHT_OWNER";
	public static final String UPSTREAM_CONTACT = "UPSTREAM_CONTACT";

	public static void main(String[] args) {
		System.out.println("===================================================");
		System.out.println("= Welcome to the Metadata Creation Tool for REUSE =");
		System.out.println("===================================================");

		final Set<String> envVariables = new HashSet<String>(
				Arrays.asList(GITHUB_ACCESS_TOKEN, COPYRIGHT_OWNER, UPSTREAM_CONTACT));

		if (!System.getenv().keySet().containsAll(envVariables)) {
			System.err.println("Environment variables missing - "
					+ CollectionUtils.subtract(envVariables, System.getenv().keySet()));
			return;
		}

		if (args.length < 1) {
			System.err.println("No arguments provided, please provide the GitHub URL that should be scanned.");
			return;
		}

		Pattern pattern = Pattern.compile("github\\.([^\\/]+)\\/([^\\/]+)\\/([^\\/\\.]+)");
		Matcher matcher = pattern.matcher(args[0]);
		if (!matcher.find()) {
			System.err.println(
					"No GitHub repository provided. The REUSE Assistant currently only supports a single given GitHub repository...");
			return;
		}

		final String organizationName = matcher.group(2);
		final String repositoryName = matcher.group(3);

		File clonedRepository = createDirectoryForCloning(repositoryName);
		if (clonedRepository == null) {
			System.err.println("Error creating local directory for cloning...");
			return;
		}
		if (!cloneRepository(args[0], organizationName, repositoryName, clonedRepository)) {
			return;
		}

		Map<String, String> askalonoResults = callAskalono(clonedRepository);
		if (askalonoResults.isEmpty()) {
			System.err.println("Askalono didn't return any results. Exiting...");
			return;
		}
		System.out.println("- Askalono call successful, directories with licenses found: " + askalonoResults.size());

		if (!setupReuse(clonedRepository, organizationName, repositoryName, askalonoResults)) {
			System.err.println("Error setting up REUSE metadata. Exiting...");
			return;
		}

		if (!pushToGitHub(clonedRepository)) {
			System.err.println("Error pushing REUSE metadata proposal to remote GitHub. Exiting...");
			return;
		}
		
		System.out.println(String.format("Metadata creation tool finished successfully, please see the %s branch in the repository %s for the proposal.", REUSE_METADATA_PROPOSAL_BRANCH, args[0]));

	}

	private static boolean pushToGitHub(File clonedRepository) {
		try {
			Git git = Git.open(clonedRepository);
			git.checkout().setCreateBranch(true).setName(REUSE_METADATA_PROPOSAL_BRANCH).call();
			git.add().addFilepattern(".").call();
			git.commit().setMessage("REUSE metadata proposal").call();
			git.push()
					.setCredentialsProvider(
							new UsernamePasswordCredentialsProvider(System.getenv(GITHUB_ACCESS_TOKEN), ""))
					.setRemote("origin").setRefSpecs(new RefSpec(REUSE_METADATA_PROPOSAL_BRANCH)).call();
		} catch (IOException e) {
			System.err.println("Generic IO Error: " + e.getMessage());
			return false;
		} catch (GitAPIException e) {
			System.err.println("Error executing Git commands: " + e.getMessage());
			return false;
		}
		return true;
	}

	private static boolean setupReuse(File clonedRepository, String organizationName, String repositoryName,
			Map<String, String> askalonoResults) {

		File reuseDirectory = new File(clonedRepository.toString() + "/.reuse");
		if (reuseDirectory.exists()) {
			System.err.println("- REUSE metadata already seems to be setup. Aborting process...");
			return false;
		} else {
			reuseDirectory.mkdirs();
		}

		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(reuseDirectory.toString() + "/dep5"));
			buildReuseFile(writer, organizationName, repositoryName, askalonoResults);
			writer.close();

			writeToConsole(new BufferedReader(new FileReader(reuseDirectory.toString() + "/dep5")));

			ProcessBuilder processBuilder = new ProcessBuilder();
			processBuilder.directory(clonedRepository);
			processBuilder.command("/usr/local/bin/reuse", "download", "--all");
			writeToConsole(new BufferedReader(new InputStreamReader(processBuilder.start().getInputStream())));

		} catch (IOException ioe) {
			System.err.println(ioe.getMessage());
			return false;
		}
		return true;
	}

	private static void writeToConsole(BufferedReader reader) throws IOException {
		String outputLine = null;
		while ((outputLine = reader.readLine()) != null) {
			System.out.println(outputLine);
		}
	}

	private static void buildReuseFile(BufferedWriter writer, String organizationName, String repositoryName,
			Map<String, String> askalonoResults) throws IOException {
		
		String copyrightOwner = System.getenv(COPYRIGHT_OWNER);
		String upstreamContact = System.getenv(UPSTREAM_CONTACT);
		
		writer.write("Format: https://www.debian.org/doc/packaging-manuals/copyright-format/1.0/\n");
		writer.write(String.format("Upstream-Name: %s\n", repositoryName));
		writer.write(String.format("Upstream-Contact: %s\n", upstreamContact));
		writer.write(String.format("Source: https://github.com/%s/%s\n", organizationName, repositoryName));
		writer.write("\n");

		for (String directory : askalonoResults.keySet()) {
			if (".".equals(directory)) {
				writer.write("Files: *\n");
				writer.write(String.format("Copyright: %d %s and %s contributors.\n",
						Calendar.getInstance().get(Calendar.YEAR), copyrightOwner, repositoryName));
			} else {
				writer.write(String.format("Files: %s/*\n", directory));
				// If we have a vendor folder we take the complete path for the project name
				// proposal.
				// If not, only the last directory name...
				String projectName = "";
				if (directory.startsWith("vendor/")) {
					projectName = directory.substring(7);
				} else {
					projectName = directory.substring(directory.lastIndexOf("/") + 1);
				}
				writer.write(String.format("Copyright: %d %s contributors.\n", Calendar.getInstance().get(Calendar.YEAR), projectName));
			}
			writer.write("License: " + askalonoResults.get(directory) + "\n");
			writer.write("\n");
		}

	}

	private static Map<String, String> callAskalono(File clonedRepository) {
		System.out.println("- Calling Askalono to get license information for complete repository");
		try {
			ProcessBuilder processBuilder = new ProcessBuilder();
			processBuilder.command("/root/.cargo/bin/askalono", "crawl", clonedRepository.toString());
			Process process = processBuilder.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String outputLine = null;
			HashMap<String, String> licenseInformation = new HashMap<String, String>();
			while ((outputLine = reader.readLine()) != null) {
				// Data coming in groups of 3 lines
				// 0) Path to license
				// 1) License name
				// 2) Score
				String localDirectory = outputLine.substring(clonedRepository.toString().length(),
						outputLine.lastIndexOf("/"));
				if (localDirectory.isEmpty()) {
					localDirectory = ".";
				} else {
					localDirectory = localDirectory.substring(1);
				}
				String rawLicenseLine = reader.readLine();
				String license = rawLicenseLine.substring(9, rawLicenseLine.indexOf(" ", 9));
				String score = reader.readLine().substring(7);
				if (licenseInformation.containsKey(localDirectory)) {
					System.out.println("- Duplicate information for directory " + localDirectory);
				} else {
					licenseInformation.put(localDirectory, license);
					System.out
							.println("- Directory: " + localDirectory + ", license: " + license + ", score: " + score);
				}
			}
			return licenseInformation;
		} catch (IOException ioe) {
			System.err.println(ioe.getMessage());
		}
		return Collections.emptyMap();
	}

	private static boolean cloneRepository(String gitUrl, String organizationName, String repositoryName,
			File clonedRepository) {
		final String repositoryFullName = organizationName + "/" + repositoryName;
		System.out.println("- Cloning " + repositoryFullName + " into directory " + clonedRepository.toString());
		CloneCommand cloneCommand = Git.cloneRepository();
		cloneCommand.setCredentialsProvider(
				new UsernamePasswordCredentialsProvider(System.getenv(GITHUB_ACCESS_TOKEN), ""));
		try {
			Git gitCall = cloneCommand.setURI(gitUrl).setDirectory(clonedRepository).call();
			gitCall.close();
			System.out.println("- Cloning completed...");
		} catch (GitAPIException e) {
			System.err.println("- Error cloning repository: " + e.getMessage());
			return false;
		}
		return true;
	}

	private static File createDirectoryForCloning(String repositoryName) {
		File cloneDirectory = new File(
				System.getProperty("user.dir") + File.separator + "repositories" + File.separator + repositoryName);
		if (cloneDirectory.mkdirs()) {
			return cloneDirectory;
		} else {
			System.err.println("Unable to create clone directory " + cloneDirectory.toString());
			return null;
		}
	}

}
