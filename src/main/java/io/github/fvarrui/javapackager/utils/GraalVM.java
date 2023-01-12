package io.github.fvarrui.javapackager.utils;

import io.github.fvarrui.javapackager.model.Platform;
import org.codehaus.plexus.util.cli.CommandLineException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NotDirectoryException;

public class GraalVM {
    public File dir;
    public File dirBin;
    public File guScript;

    public GraalVM(File dir) throws NotDirectoryException, FileNotFoundException {
        if(!dir.isDirectory()) throw new NotDirectoryException(dir.toString());
        this.dir = dir;
        this.dirBin = new File(dir+"/bin");
        if(!dirBin.exists()) throw new FileNotFoundException(dirBin.toString());
        for (File file : dirBin.listFiles()) {
            if(file.getName().contains("gu.")){
                this.guScript = file;
                break;
            }
        }
        if(guScript==null || !guScript.exists()) throw new FileNotFoundException("gu script not found inside: "+dirBin);
    }

    public void guInstall(String name) throws IOException, CommandLineException {
        CommandUtils.execute(guScript, "install", name);
    }

    public File getNativeImageExe(){
        File nativeImage = null;
        for (File file : dirBin.listFiles()) {
            if(file.getName().equals("native-image.cmd") || file.getName().equals("native-image.sh")
                    || file.getName().equals("native-image")){
                nativeImage = file;
                break;
            }
        }
        return nativeImage;
    }

    public File generateNativeImage(Platform platform, File outputDirectory) throws IOException, CommandLineException {
        guInstall("native-image");
        // TODO install other platform dependent dependencies here

        File jarFile = null;
        for (File file : outputDirectory.listFiles()) {
            if(file.getName().endsWith("shaded.jar") || file.getName().endsWith("all.jar") || file.getName().endsWith("dependencies.jar")){
                jarFile = file;
                break;
            }
        }
        if(jarFile==null || !jarFile.exists())
            throw new FileNotFoundException("File ending with \"shaded.jar\" or \"all.jar\" or \"dependencies.jar\"" +
                    " not found inside: "+outputDirectory);
        String outputExeName = jarFile.getName().replace(".jar", "") + (platform == Platform.windows ? ".exe" : "");
        CommandUtils.executeWithResult(outputDirectory, // working dir = output dir
                getNativeImageExe().toString(),  "-jar", jarFile, outputExeName);
        return new File(outputDirectory+"/"+outputExeName);
    }

    public File generateSharedLibrary(Platform platform, File outputDirectory) throws IOException, CommandLineException {
        guInstall("native-image");
        // TODO install other platform dependent dependencies here

        File jarFile = null;
        for (File file : outputDirectory.listFiles()) {
            if(file.getName().endsWith("shaded.jar") || file.getName().endsWith("all.jar") || file.getName().endsWith("dependencies.jar")){
                jarFile = file;
                break;
            }
        }
        if(jarFile==null || !jarFile.exists())
            throw new FileNotFoundException("File ending with \"shaded.jar\" or \"all.jar\" or \"dependencies.jar\"" +
                    " not found inside: "+outputDirectory);
        CommandUtils.executeWithResult(outputDirectory, // working dir = output dir
                getNativeImageExe().toString(),  "-jar", jarFile, jarFile.getName(), "--shared");
        return new File(outputDirectory+"/"+jarFile.getName() + (platform == Platform.windows ? ".dll" : ".so"));
    }
}
