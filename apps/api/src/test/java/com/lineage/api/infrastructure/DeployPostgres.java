package com.lineage.api.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Starts the disposable PostgreSQL 17 from {@code deploy/docker-compose.yml}.
 */
public final class DeployPostgres {

	private static volatile boolean started;

	private DeployPostgres() {
	}

	public static synchronized void ensureRunning() {
		if (started) {
			if (jdbcReady()) {
				return;
			}
			started = false;
		}
		File root = repoRoot();
		File compose = new File(root, "deploy/docker-compose.yml");
		if (!compose.isFile()) {
			throw new IllegalStateException("missing " + compose.getAbsolutePath());
		}
		File env = new File(root, "deploy/.env");
		if (!env.isFile()) {
			env = new File(root, "deploy/.env.example");
		}
		List<String> up = docker("compose", "-f", compose.getAbsolutePath(), "--env-file", env.getAbsolutePath(),
			"up", "-d");
		run(root, up, 120000);
		waitUntilJdbcReady(60000);
		started = true;
	}

	public static String jdbcUrl() {
		return "jdbc:postgresql://127.0.0.1:5432/lineage";
	}

	public static String username() {
		return "lineage";
	}

	public static String password() {
		return "change-me-local";
	}

	static File repoRoot() {
		File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
		for (int i = 0; i < 6 && dir != null; i++) {
			if (new File(dir, "deploy/docker-compose.yml").isFile() && new File(dir, "apps/api").isDirectory()) {
				return dir;
			}
			dir = dir.getParentFile();
		}
		throw new IllegalStateException("cannot locate repo root from " + System.getProperty("user.dir"));
	}

	private static List<String> docker(String... args) {
		List<String> command = new ArrayList<String>();
		File sock = new File("/var/run/docker.sock");
		if (!sock.canWrite()) {
			command.add("sudo");
			command.add("-n");
		}
		command.add("docker");
		command.addAll(Arrays.asList(args));
		return command;
	}

	private static void waitUntilJdbcReady(long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		Exception last = null;
		while (System.currentTimeMillis() < deadline) {
			if (jdbcReady()) {
				return;
			}
			try {
				Thread.sleep(500L);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrupted waiting for PostgreSQL", e);
			}
		}
		throw new IllegalStateException("PostgreSQL not ready at " + jdbcUrl(), last);
	}

	private static boolean jdbcReady() {
		try {
			Class.forName("org.postgresql.Driver");
			Connection c = DriverManager.getConnection(jdbcUrl(), username(), password());
			c.close();
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	private static void run(File cwd, List<String> command, long timeoutMs) {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(cwd);
		pb.redirectErrorStream(true);
		try {
			Process process = pb.start();
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			InputStream in = process.getInputStream();
			byte[] chunk = new byte[2048];
			long deadline = System.currentTimeMillis() + timeoutMs;
			while (true) {
				int available = in.available();
				int n;
				if (available > 0 || !isAlive(process)) {
					n = in.read(chunk);
				}
				else {
					n = 0;
				}
				if (n > 0) {
					buf.write(chunk, 0, n);
				}
				else if (n < 0) {
					break;
				}
				else if (!isAlive(process)) {
					drain(in, buf, chunk);
					break;
				}
				else if (System.currentTimeMillis() > deadline) {
					process.destroyForcibly();
					throw new IllegalStateException("timeout running " + join(command) + "\n" + buf);
				}
				else {
					Thread.sleep(50L);
				}
			}
			int code = process.waitFor();
			if (code != 0) {
				throw new IllegalStateException(
					"command failed (" + code + "): " + join(command) + "\n" + new String(buf.toByteArray(),
						StandardCharsets.UTF_8));
			}
		}
		catch (IllegalStateException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalStateException("failed running " + join(command), e);
		}
	}

	private static void drain(InputStream in, ByteArrayOutputStream buf, byte[] chunk) throws Exception {
		int n;
		while ((n = in.read(chunk)) > 0) {
			buf.write(chunk, 0, n);
		}
	}

	private static boolean isAlive(Process process) {
		try {
			process.exitValue();
			return false;
		}
		catch (IllegalThreadStateException e) {
			return true;
		}
	}

	private static String join(List<String> command) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < command.size(); i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(command.get(i));
		}
		return sb.toString();
	}
}
