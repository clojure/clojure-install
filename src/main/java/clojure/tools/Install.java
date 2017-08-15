package clojure.tools;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Map;

public class Install {

    private static String HOME = System.getProperty("user.home");

    private static File backup(File f, boolean verbose) throws IOException {
        if(f.exists()) {
            File backup = new File(f.getParentFile(), f.getName() + ".backup");
            if(verbose) System.out.println("Backing up " + f.getAbsolutePath() + " to .backup file");
            Files.copy(f.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return f;
    }

    public static class ConsoleTransferListener implements TransferListener {
        public void transferInitiated(TransferEvent transferEvent) {}
        public void transferProgressed(TransferEvent transferEvent) {}
        public void transferSucceeded(TransferEvent transferEvent) {}
        public void transferCorrupted(TransferEvent transferEvent) {}
        public void transferFailed(TransferEvent transferEvent) {}

        public void transferStarted(TransferEvent transferEvent) {
            String resource = transferEvent.getResource().getResourceName();
            if(resource.endsWith(".jar")) {
                String message = "Downloading: " + resource;
                System.out.println(message);
            }
        }
    }

    private static RepositorySystem repositorySystem() {
        DefaultServiceLocator loc = MavenRepositorySystemUtils.newServiceLocator();
        loc.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        loc.addService(TransporterFactory.class, FileTransporterFactory.class);
        loc.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return loc.getService(RepositorySystem.class);
    }

    private static RepositorySystemSession repositorySession(RepositorySystem system, boolean offline) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        String localRepo = new File(new File(HOME, ".m2"), "repository").getAbsolutePath();
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, new LocalRepository(localRepo)));
        session.setOffline(offline);
        session.setTransferListener(new ConsoleTransferListener());
        return session;
    }

    private static Artifact makeArtifact(String groupId, String artifactId, String version) {
        return new DefaultArtifact(groupId, artifactId, "jar", version);
    }

    private static Dependency makeDependency(String groupId, String artifactId, String version) {
        return new Dependency(makeArtifact(groupId, artifactId, version), null);
    }

    private static VersionRangeRequest makeVersionRangeRequest(String groupId, String artifactId) {
        VersionRangeRequest req = new VersionRangeRequest();
        req.setArtifact(makeArtifact(groupId, artifactId, "[0,)"));
        return req;
    }

    private static List<ArtifactResult> resolveDeps(RepositorySystem system, RepositorySystemSession session, RemoteRepository repo, List<Dependency> deps)
    throws DependencyResolutionException {
        List<RemoteRepository> repos = new ArrayList<RemoteRepository>();
        repos.add(repo);

        CollectRequest collectRequest = new CollectRequest(deps, null, repos);
        collectRequest.setRequestContext("runtime");

        DependencyResult result = system.resolveDependencies(session, new DependencyRequest(collectRequest, null));
        List<ArtifactResult> results = result.getArtifactResults();
        return results;
    }

    private static void install(String configDir, boolean verbose) throws Exception {
        // Ensure config directory exists
        File clojure = new File(configDir);
        if(! clojure.exists()) {
            throw new IOException("Directory does not exist: " + clojure.getAbsolutePath());
        }

        // Read properties file
        File cljPropsFile = new File(clojure, "clj.props");
        if(! cljPropsFile.exists()) {
            throw new IOException("Missing props file at: " + cljPropsFile.getAbsolutePath());

        }
        Properties props = new Properties();
        props.load(new FileReader(cljPropsFile));

        // Ready for mavening
        boolean offline = false;
        RepositorySystem system = repositorySystem();
        RepositorySystemSession session = repositorySession(system, offline);
        RemoteRepository repo = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2").build();

        // Load and install deps
        if(verbose) System.out.println("Installing deps");
        List<Dependency> deps = new ArrayList<Dependency>();
        for (Map.Entry<?, ?> entry: props.entrySet()) {
            String lib = (String) entry.getKey();
            String[] parts = lib.split("/");
            String version = (String) entry.getValue();
            if(parts.length == 1) {
                deps.add(makeDependency(parts[0], parts[0], version));
            } else if(parts.length == 2) {
                deps.add(makeDependency(parts[0], parts[1], version));
            } else {
                throw new IOException("Invalid lib in props file: " + lib + ", expected something like org.clojure/clojure");
            }
        }
        List<ArtifactResult> results = resolveDeps(system, session, repo, deps);

        // Find files and make backups
        File cp = backup(new File(clojure, "clj.cp"), verbose);
        File systemdeps = backup(new File(clojure, "deps.edn"), verbose);

        // Write clj.cp
        if(verbose) System.out.println("Writing: " + cp.getAbsolutePath());
        BufferedWriter cpWriter = new BufferedWriter(new FileWriter(cp));
        Iterator<ArtifactResult> resultsIter = results.iterator();
        cpWriter.write(resultsIter.next().getArtifact().getFile().getAbsolutePath());
        while(resultsIter.hasNext()) {
            cpWriter.write(File.pathSeparatorChar);
            cpWriter.write(resultsIter.next().getArtifact().getFile().getAbsolutePath());
        }
        cpWriter.close();

        // Write system deps.edn
        if(verbose) System.out.println("Writing: " + systemdeps.getAbsolutePath());
        BufferedWriter systemdepsWriter = new BufferedWriter(new FileWriter(systemdeps));
        systemdepsWriter.write(String.format(
                "{:deps {%1$s/%2$s {:type :mvn :version \"%3$s\"}}\n" +
                " :providers {:mvn {:repos {\"central\" {:url \"https://repo1.maven.org/maven2/\"}\n" +
                "                           \"clojars\" {:url \"https://clojars.org/repo/\"}}}}}",
                "org.clojure", "clojure", props.get("org.clojure/clojure")));
        systemdepsWriter.close();

        if(verbose) System.out.println("Done.");
    }

    /**
     * Usage: java clojure.tools.Install config_dir [opts]
     *
     * config_dir - the Clojure config directory to use
     *
     * opts:
     *
     *   -v - verbose mode
     *
     * Expects to be called in the context of install-clj script, which ensures:
     *
     *   ~/.clojure exists
     *   ~/.clojure/clj.props exists
     */
    public static void main(String[] args) {
        try {
            if(args.length == 0) {
                throw new IOException("No config directory specified");
            }
            String configDir = args[0];
            boolean verbose = args.length > 1 && args[1].equals("-v");
            install(configDir, verbose);
        } catch(Throwable e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
