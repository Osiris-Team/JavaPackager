package io.github.fvarrui.javapackager.packagers;

import io.github.fvarrui.javapackager.model.Platform;
import io.github.fvarrui.javapackager.utils.*;

import java.io.File;
import java.util.Objects;

public class GenerateNativeImage extends ArtifactGenerator<Packager> {

	public GenerateNativeImage() {
		super("Native Image");
	}

	@Override
	public boolean skip(Packager packager) {
		
		if (!packager.task.isNativeImage()) {
			return true;
		}
		
		if (!Objects.equals(packager.task.getJdkVendor(), Const.graalvm)) {
			Logger.warn(getArtifactName() + " cannot be generated because '"+Const.graalvm +"' was expected as jdkVendor, but provided '"+packager.task.getJdkVendor()+"'!");
			return true;
		}

		if (!packager.task.getPlatform().isCurrentPlatform()) {
			Logger.warn(getArtifactName() + " cannot be generated due to the target platform (" + packager.task.getPlatform() + ") is different from the execution platform (" + Platform.getCurrentPlatform() + ")!");
			return true;
		}
		
		return false;		
	}
	
	@Override
	protected File doApply(Packager packager) throws Exception {
		File outputDirectory = packager.task.getOutputDirectory();
		GraalVM graalVM = new GraalVM(packager.task.getJdkPath());
		return graalVM.generateNativeImage(packager.task.getPlatform(), outputDirectory);
	}
	
}
