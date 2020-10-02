package org.bdgp.OpenHiCAMM;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.StringJoiner;

public class Processes {
	public static int getPid() {
		try {
            java.lang.management.RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
            java.lang.reflect.Field jvm = runtime.getClass().getDeclaredField("jvm");
            jvm.setAccessible(true);
            Object mgmt = jvm.get(runtime);
            java.lang.reflect.Method pid_method = mgmt.getClass().getDeclaredMethod("getProcessId");
            pid_method.setAccessible(true);
            return (Integer) pid_method.invoke(mgmt);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}
	
	public static boolean isRunning(int pid) {
		if (isWindows()) {
			ExitStatus result = runCommandForOutput(
					String.format("%s\\system32\\tasklist.exe", System.getenv("SystemRoot")),
					"/fo","csv",
					String.format("/fi","PID eq %s", pid));
			return !result.out.matches("(?ims).*INFO: No tasks are running which match the specified criteria[.].*");
		}
		else {
			ExitStatus result = runCommandForOutput("ps","-f",Integer.toString(pid));
			return result.exitValue == 0;
		}
	}
	
	public static int killProcess(int pid) {
		if (isWindows()) {
			ExitStatus result = runCommandForOutput(
					String.format("%s\\system32\\taskkill.exe", System.getenv("SystemRoot")),
					"/F","/T","/PID", Integer.toString(pid));
			return result.exitValue;
		}
		else {
			ExitStatus result = runCommandForOutput("kill","-KILL",Integer.toString(pid));
			return result.exitValue;
		}
	}

	public static int stopProcess(int pid) {
		if (isWindows()) {
			ExitStatus result = runCommandForOutput(
					String.format("%s\\system32\\taskkill.exe", System.getenv("SystemRoot")),
					"/T","/PID", Integer.toString(pid));
			return result.exitValue;
		}
		else {
			ExitStatus result = runCommandForOutput("kill","-TERM",Integer.toString(pid));
			return result.exitValue;
		}
	}
	
	public static class ExitStatus {
		public String out;
		public int exitValue;
		public ExitStatus(String out, int exitValue) {
			this.out = out;
			this.exitValue = exitValue;
		}
	}
	
	public static ExitStatus runCommandForOutput(String... params) {
	    try {
            ProcessBuilder pb = new ProcessBuilder(params);
            Process p = pb.start();
	        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        StringJoiner sj = new StringJoiner(System.getProperty("line.separator"));
	        reader.lines().iterator().forEachRemaining(sj::add);
	        String result = sj.toString();
	        p.waitFor();
	        p.destroy();
            return new ExitStatus(result, p.exitValue());
	    } catch (Exception e) {
	    	throw new RuntimeException(e);
	    }
	}
	
//	public static Process fork(List<String> appendArgs, Map<String,String> appendEnv) {
//		String java = isWindows()? 
//				Paths.get(System.getProperty("java.home"),"bin","java.exe").toString() :
//				Paths.get(System.getProperty("java.home"),"bin","java").toString();
//		if (!new File(java).exists()) {
//			throw new RuntimeException(String.format("Could not find java binary at %s!", java));
//		}
//		List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
//		args.addAll(appendArgs);
//		Map<String,String> env = System.getenv();
//		env.putAll(appendEnv);
//		ProcessBuilder p = 
//	}
}