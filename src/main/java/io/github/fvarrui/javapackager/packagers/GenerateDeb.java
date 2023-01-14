package io.github.fvarrui.javapackager.packagers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.zip.UnixStat;

import io.github.fvarrui.javapackager.utils.Logger;
import io.github.fvarrui.javapackager.utils.VelocityUtils;

/**
 * Creates a DEB package file including all app folder's content only for 
 * GNU/Linux so app could be easily distributed on Gradle context
 */
public class GenerateDeb extends ArtifactGenerator<LinuxPackager> {
	
	public GenerateDeb() {
		super("DEB package");
	}
	
	@Override
	public boolean skip(LinuxPackager packager) {
		// TODO find cause why jitpack publishes the jdeb library as artifact
		Logger.warn("Currently not supported.");
		return true;
	}
	
	@Override
	protected File doApply(LinuxPackager packager) throws Exception {
		return null;
	}
}
