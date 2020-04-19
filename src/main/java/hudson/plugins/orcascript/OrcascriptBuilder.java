/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014, Kyle Sweeney, Gregory Boissinot and other contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.orcascript;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;

/**
 * @author kyle.sweeney@valtech.com
 */
public class OrcascriptBuilder extends Builder {
	/**
	 * GUI fields
	 */
	private final String orcascriptName;
	private final String pbtFile;
	private final String excludeLiblist;
	private final String refreshType;	
	private final boolean continueOnBuildFailure;
	private final boolean unstableIfWarnings;

	/**
	 * When this builder is created in the project configuration step, the
	 * builder object will be created from the strings below.
	 *
	 * @param orcascriptName
	 *            The Orcascript logical name
	 * @param pbtFile
	 *            The name/location of the PBT file for the project
	 * @param excludeLiblist
	 *            The name/location of any libraries in the PBT that should be
	 *            excluded from the import (i.e., PBD files)
	 * @param refreshType
	 *            Whether the 3pass or migration refresh type is performed
	 * @param continueOnBuildFailure
	 *            If true, job will continue despite OrcaScript build failure
	 * @param unstableIfWarnings
	 *            If true, job will be unstable if there are warnings
	 */
	@DataBoundConstructor
	public OrcascriptBuilder(String orcascriptName, String pbtFile, String excludeLiblist,
			String refreshType, boolean continueOnBuildFailure, boolean unstableIfWarnings) {
		this.orcascriptName = orcascriptName;
		this.pbtFile = pbtFile;
		this.refreshType = refreshType ;
		this.excludeLiblist = excludeLiblist;
		this.continueOnBuildFailure = continueOnBuildFailure;
		this.unstableIfWarnings = unstableIfWarnings;
	}

	public String getExcludeLiblist() {
		return excludeLiblist;
	}

	public String getRefreshType() {
		return refreshType;
	}

	public String getPbtFile() {
		return pbtFile;
	}

	public String getOrcascriptName() {
		return orcascriptName;
	}

	public boolean getContinueOnBuildFailure() {
		return continueOnBuildFailure;
	}

	public boolean getUnstableIfWarnings() {
		return unstableIfWarnings;
	}

	public OrcascriptInstallation getOrcascript() {
		DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
		for (OrcascriptInstallation i : descriptor.getInstallations()) {
			if (orcascriptName != null && i.getName().equals(orcascriptName))
				return i;
		}

		return null;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		return runOrcaScript(build, launcher, listener);
	}

	public boolean runOrcaScript(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		String execName = "orcascr190.exe";
		OrcascriptInstallation ai = getOrcascript();

		if (ai == null) {
			listener.getLogger().println("Path To orcascr190.exe: " + execName);
			args.add(execName);
		} else {
			EnvVars env = build.getEnvironment(listener);
			Node node = Computer.currentComputer().getNode();
			if (node != null) {
				ai = ai.forNode(node, listener);
				ai = ai.forEnvironment(env);
				String pathToOrcascript = getToolFullPath(launcher, ai.getHome(), execName);
				FilePath exec = new FilePath(launcher.getChannel(), pathToOrcascript);

				try {
					if (!exec.exists()) {
						listener.fatalError(pathToOrcascript + " doesn't exist");
						return false;
					}
				} catch (IOException e) {
					listener.fatalError("Failed checking for existence of " + pathToOrcascript);
					return false;
				}

				listener.getLogger().println("Path To orcascr190.exe: " + pathToOrcascript);
				args.add(pathToOrcascript);

				if (ai.getDefaultArgs() != null) {
					args.add(tokenizeArgs(ai.getDefaultArgs()));
				}
			}
		}

		EnvVars env = build.getEnvironment(listener);
		FilePath pwd = build.getModuleRoot();

		PrintWriter pw = null;
		String orcascript = build.getWorkspace() + "\\orcascript.orca";
		try {
			File file = new File(orcascript);
			FileWriter fw = new FileWriter(file, false);
			pw = new PrintWriter(fw);
			pw.println("start session");
			// pw.println("set debug true");
			// pw.println("scc set connect property logfile
			// \"createpbls.log\"");
			pw.println("scc connect offline");
			pw.println("scc set target \"" + pbtFile + "\" importonly");
			if (excludeLiblist != null && !excludeLiblist.isEmpty()) {
				pw.println("scc exclude liblist \"" + excludeLiblist + "\"");
			}
			pw.println("scc refresh target " + refreshType);
			pw.println("scc close");
			pw.println("end session");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (pw != null) {
				pw.close();
			}
		}

		args.add(orcascript);

		if (!launcher.isUnix()) {
			final int cpi = getCodePageIdentifier(build.getCharset());
			if (cpi != 0)
				args.prepend("cmd.exe", "/C", "\"", "chcp", String.valueOf(cpi), "&&");
			else
				args.prepend("cmd.exe", "/C", "\"");
			args.add("\"", "&&", "exit", "%%ERRORLEVEL%%");
		}

		try {
			listener.getLogger()
					.println(String.format("Executing the command %s from %s", args.toStringWithQuote(), pwd));
			// Parser to find the number of Warnings/Errors
			OrcascriptConsoleParser mbcp = new OrcascriptConsoleParser(listener.getLogger(), build.getCharset());
			OrcascriptConsoleAnnotator annotator = new OrcascriptConsoleAnnotator(listener.getLogger(),
					build.getCharset());
			// Launch orcascript190.exe
			int r = launcher.launch().cmds(args).envs(env).stdout(mbcp).stdout(annotator).pwd(pwd).join();
			// Check the number of warnings
			if (unstableIfWarnings && mbcp.getNumberOfWarnings() > 0) {
				listener.getLogger().println("> Set build UNSTABLE because there are warnings.");
				build.setResult(Result.UNSTABLE);
			}
			// Return the result of the compilation
			return continueOnBuildFailure ? true : (r == 0);
		} catch (IOException e) {
			Util.displayIOException(e, listener);
			build.setResult(Result.FAILURE);
			return false;
		}
	}

	/**
	 * Get the full path of the tool to run. If given path is a directory, this
	 * will append the executable name.
	 */
	static String getToolFullPath(Launcher launcher, String pathToTool, String execName)
			throws IOException, InterruptedException {
		String fullPath = pathToTool;

		FilePath exec = new FilePath(launcher.getChannel(), fullPath);
		if (exec.isDirectory()) {
			if (!fullPath.endsWith("\\")) {
				fullPath = fullPath + "\\";
			}

			fullPath = fullPath + execName;
		}

		return fullPath;
	}

	@Override
	public Descriptor<Builder> getDescriptor() {
		return super.getDescriptor();
	}

	/**
	 * Tokenize a set of arguments, preserving quotes.
	 *
	 * @param args
	 * @return
	 */
	static String[] tokenizeArgs(String args) {

		if (args == null) {
			return null;
		}

		final String[] tokenize = Util.tokenize(args);

		if (args.endsWith("\\")) {
			tokenize[tokenize.length - 1] = tokenize[tokenize.length - 1] + "\\";
		}

		return tokenize;
	}

	@Extension
	@Symbol("orcascript")
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		@CopyOnWrite
		private volatile OrcascriptInstallation[] installations = new OrcascriptInstallation[0];

		public DescriptorImpl() {
			super(OrcascriptBuilder.class);
			load();
		}

		@Override
		public String getDisplayName() {
			return Messages.Orcascript_DisplayName();
		}

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public OrcascriptInstallation[] getInstallations() {
			return Arrays.copyOf(installations, installations.length);
		}

		public void setInstallations(OrcascriptInstallation... antInstallations) {
			this.installations = antInstallations;
			save();
		}

		public OrcascriptInstallation.DescriptorImpl getToolDescriptor() {
			return ToolInstallation.all().get(OrcascriptInstallation.DescriptorImpl.class);
		}
	}

	private static int getCodePageIdentifier(Charset charset) {
		final String s_charset = charset.name();
		if (s_charset.equalsIgnoreCase("utf-8")) // Unicode
			return 65001;
		else if (s_charset.equalsIgnoreCase("ibm437")) // US
			return 437;
		else if (s_charset.equalsIgnoreCase("ibm850")) // OEM Multilingual Latin
														// 1
			return 850;
		else if (s_charset.equalsIgnoreCase("ibm852")) // OEM Latin2
			return 852;
		else if (s_charset.equalsIgnoreCase("shift_jis") || s_charset.equalsIgnoreCase("windows-31j")) // Japanese
			return 932;
		else if (s_charset.equalsIgnoreCase("us-ascii")) // US-ASCII
			return 20127;
		else if (s_charset.equalsIgnoreCase("euc-jp")) // Japanese
			return 20932;
		else if (s_charset.equalsIgnoreCase("iso-8859-1")) // Latin 1
			return 28591;
		else if (s_charset.equalsIgnoreCase("iso-8859-2")) // Latin 2
			return 28592;
		else if (s_charset.equalsIgnoreCase("IBM00858"))
			return 858;
		else if (s_charset.equalsIgnoreCase("IBM775"))
			return 775;
		else if (s_charset.equalsIgnoreCase("IBM855"))
			return 855;
		else if (s_charset.equalsIgnoreCase("IBM857"))
			return 857;
		else if (s_charset.equalsIgnoreCase("ISO-8859-4"))
			return 28594;
		else if (s_charset.equalsIgnoreCase("ISO-8859-5"))
			return 28595;
		else if (s_charset.equalsIgnoreCase("ISO-8859-7"))
			return 28597;
		else if (s_charset.equalsIgnoreCase("ISO-8859-9"))
			return 28599;
		else if (s_charset.equalsIgnoreCase("ISO-8859-13"))
			return 28603;
		else if (s_charset.equalsIgnoreCase("ISO-8859-15"))
			return 28605;
		else if (s_charset.equalsIgnoreCase("KOI8-R"))
			return 20866;
		else if (s_charset.equalsIgnoreCase("KOI8-U"))
			return 21866;
		else if (s_charset.equalsIgnoreCase("UTF-16"))
			return 1200;
		else if (s_charset.equalsIgnoreCase("UTF-32"))
			return 12000;
		else if (s_charset.equalsIgnoreCase("UTF-32BE"))
			return 12001;
		else if (s_charset.equalsIgnoreCase("windows-1250"))
			return 1250;
		else if (s_charset.equalsIgnoreCase("windows-1251"))
			return 1251;
		else if (s_charset.equalsIgnoreCase("windows-1252"))
			return 1252;
		else if (s_charset.equalsIgnoreCase("windows-1253"))
			return 1253;
		else if (s_charset.equalsIgnoreCase("windows-1254"))
			return 1254;
		else if (s_charset.equalsIgnoreCase("windows-1257"))
			return 1257;
		else if (s_charset.equalsIgnoreCase("Big5"))
			return 950;
		else if (s_charset.equalsIgnoreCase("EUC-KR"))
			return 51949;
		else if (s_charset.equalsIgnoreCase("GB18030"))
			return 54936;
		else if (s_charset.equalsIgnoreCase("GB2312"))
			return 936;
		else if (s_charset.equalsIgnoreCase("IBM-Thai"))
			return 20838;
		else if (s_charset.equalsIgnoreCase("IBM01140"))
			return 1140;
		else if (s_charset.equalsIgnoreCase("IBM01141"))
			return 1141;
		else if (s_charset.equalsIgnoreCase("IBM01142"))
			return 1142;
		else if (s_charset.equalsIgnoreCase("IBM01143"))
			return 1143;
		else if (s_charset.equalsIgnoreCase("IBM01144"))
			return 1144;
		else if (s_charset.equalsIgnoreCase("IBM01145"))
			return 1145;
		else if (s_charset.equalsIgnoreCase("IBM01146"))
			return 1146;
		else if (s_charset.equalsIgnoreCase("IBM01147"))
			return 1147;
		else if (s_charset.equalsIgnoreCase("IBM01148"))
			return 1148;
		else if (s_charset.equalsIgnoreCase("IBM01149"))
			return 1149;
		else if (s_charset.equalsIgnoreCase("IBM037"))
			return 37;
		else if (s_charset.equalsIgnoreCase("IBM1026"))
			return 1026;
		else if (s_charset.equalsIgnoreCase("IBM273"))
			return 20273;
		else if (s_charset.equalsIgnoreCase("IBM277"))
			return 20277;
		else if (s_charset.equalsIgnoreCase("IBM278"))
			return 20278;
		else if (s_charset.equalsIgnoreCase("IBM280"))
			return 20280;
		else if (s_charset.equalsIgnoreCase("IBM284"))
			return 20284;
		else if (s_charset.equalsIgnoreCase("IBM285"))
			return 20285;
		else if (s_charset.equalsIgnoreCase("IBM297"))
			return 20297;
		else if (s_charset.equalsIgnoreCase("IBM420"))
			return 20420;
		else if (s_charset.equalsIgnoreCase("IBM424"))
			return 20424;
		else if (s_charset.equalsIgnoreCase("IBM500"))
			return 500;
		else if (s_charset.equalsIgnoreCase("IBM860"))
			return 860;
		else if (s_charset.equalsIgnoreCase("IBM861"))
			return 861;
		else if (s_charset.equalsIgnoreCase("IBM863"))
			return 863;
		else if (s_charset.equalsIgnoreCase("IBM864"))
			return 864;
		else if (s_charset.equalsIgnoreCase("IBM865"))
			return 865;
		else if (s_charset.equalsIgnoreCase("IBM869"))
			return 869;
		else if (s_charset.equalsIgnoreCase("IBM870"))
			return 870;
		else if (s_charset.equalsIgnoreCase("IBM871"))
			return 20871;
		else if (s_charset.equalsIgnoreCase("ISO-2022-JP"))
			return 50220;
		else if (s_charset.equalsIgnoreCase("ISO-2022-KR"))
			return 50225;
		else if (s_charset.equalsIgnoreCase("ISO-8859-3"))
			return 28593;
		else if (s_charset.equalsIgnoreCase("ISO-8859-6"))
			return 28596;
		else if (s_charset.equalsIgnoreCase("ISO-8859-8"))
			return 28598;
		else if (s_charset.equalsIgnoreCase("windows-1255"))
			return 1255;
		else if (s_charset.equalsIgnoreCase("windows-1256"))
			return 1256;
		else if (s_charset.equalsIgnoreCase("windows-1258"))
			return 1258;
		else
			return 0;
	}

}
