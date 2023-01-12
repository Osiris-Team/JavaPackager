package io.github.fvarrui.javapackager.packagers;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.collections4.CollectionUtils.addIgnoreNull;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.github.fvarrui.javapackager.PackageTask;
import io.github.fvarrui.javapackager.model.LinuxConfig;
import io.github.fvarrui.javapackager.model.MacConfig;
import io.github.fvarrui.javapackager.model.Platform;
import io.github.fvarrui.javapackager.model.WindowsConfig;
import io.github.fvarrui.javapackager.utils.*;
import io.github.fvarrui.javapackager.utils.updater.TaskJavaUpdater;

/**
 * Packager base class
 */
public abstract class Packager {

	private static final String DEFAULT_ORGANIZATION_NAME = "ACME";
	public PackageTask task;

	// artifact generators
	protected List<ArtifactGenerator<?>> installerGenerators = new ArrayList<>();
	private BundleJre generateJre = new BundleJre();

	// internal generic properties (setted in "createAppStructure/createApp")
	protected File appFolder;
	protected File assetsFolder;
	protected File executable;
	protected File jarFile;
	protected File libsFolder;
	protected File bootstrapFile;

	// internal specific properties (setted in "doCreateAppStructure")
	protected File executableDestinationFolder;
	protected File jarFileDestinationFolder;
	protected File jreDestinationFolder;
	protected File resourcesDestinationFolder;

	// processed classpaths list
	protected List<String> classpaths = new ArrayList<>();

	// ===============================================

	public File getAppFolder() {
		return appFolder;
	}

	public File getAssetsFolder() {
		return assetsFolder;
	}

	public File getExecutable() {
		return executable;
	}

	public File getJarFile() {
		return jarFile;
	}

	public String getJarName() {
		return jarFile.getName();
	}

	public File getJarFileDestinationFolder() {
		return jarFileDestinationFolder;
	}

	public File getLibsFolder() {
		return libsFolder;
	}

	public List<String> getClasspaths() {
		return classpaths;
	}

	public File getJreDestinationFolder() {
		return jreDestinationFolder;
	}

	public File getBootstrapFile() {
		return bootstrapFile;
	}

	// ===============================================

	public Packager(PackageTask task) {
		super();
		this.task = task;
		Logger.info("Using packager " + this.getClass().getName());
	}


	private void init() throws Exception {

		Logger.infoIndent("Initializing packager ...");

		if (task.getMainClass() == null || task.getMainClass().isEmpty()) {
			throw new Exception("'mainClass' cannot be null");
		}

		// sets assetsDir for velocity to locate custom velocity templates
		VelocityUtils.setAssetsDir(task.getAssetsDir());

		// using name as displayName, if it's not specified
		task.appDisplayName(defaultIfBlank(task.getAppDisplayName(), task.getAppName()));

		// using displayName as description, if it's not specified
		task.description(defaultIfBlank(task.getDescription(), task.getAppDisplayName()));

		// using "ACME" as organizationName, if it's not specified
		task.organizationName(defaultIfBlank(task.getOrganizationName(), DEFAULT_ORGANIZATION_NAME));

		// using empty string as organizationUrl, if it's not specified
		task.organizationUrl(defaultIfBlank(task.getOrganizationUrl(), ""));

		// determines target platform if not specified
		if (task.getPlatform() == null || task.getPlatform() == Platform.auto) {
			task.platform(Platform.getCurrentPlatform());
		}

		// check if name is valid as filename
		try {
			Paths.get(task.getAppName());
			if (task.getAppName().contains("/"))
				throw new InvalidPathException(task.getAppName(), "Illegal char </>");
			if (task.getAppName().contains("\\"))
				throw new InvalidPathException(task.getAppName(), "Illegal char <\\>");
		} catch (InvalidPathException e) {
			throw new Exception("Invalid name specified: " + task.getAppName(), e);
		}

		// init setup languages
		if (task.getPlatform().equals(Platform.windows) && (task.getWinConfig().getSetupLanguages() == null || task.getWinConfig().getSetupLanguages().isEmpty())) {
			task.getWinConfig().getSetupLanguages().put("english", "compiler:Default.isl");
			task.getWinConfig().getSetupLanguages().put("spanish", "compiler:Languages\\Spanish.isl");
		}

		doInit();

		// removes not necessary platform specific configs
		switch (task.getPlatform()) {
		case linux:
			task.macConfig((MacConfig) null);
			task.winConfig((WindowsConfig) null);
			break;
		case mac:
			task.winConfig((WindowsConfig) null);
			task.linuxConfig((LinuxConfig) null);
			break;
		case windows:
			task.linuxConfig((LinuxConfig) null);
			task.macConfig((MacConfig) null);
			break;
		default:
		}

		Logger.info("" + this); // prints packager settings

		Logger.infoUnindent("Packager initialized!");

	}

	public void resolveResources() throws Exception {

		Logger.infoIndent("Resolving resources ...");

		// locates license file
		task.licenseFile(resolveLicense(task.getLicenseFile()));

		// locates icon file
		task.iconFile(resolveIcon(task.getIconFile(), task.getAppName(), assetsFolder));

		// adds to additional resources
		if (task.getAdditionalResources() != null) {
			if (task.getLicenseFile() != null) task.getAdditionalResources().add(task.getLicenseFile());
			task.getAdditionalResources().add(task.getIconFile());
			Logger.info("Effective additional resources " + task.getAdditionalResources());
		}

		Logger.infoUnindent("Resources resolved!");

	}

	/**
	 * Copy a list of resources to a folder
	 * 
	 * @param resources   List of files and folders to be copied
	 * @param destination Destination folder. All specified resources will be copied
	 *                    here
	 */
	protected void copyAdditionalResources(List<File> resources, File destination) {

		Logger.infoIndent("Copying additional resources");

		resources.stream().forEach(r -> {
			if (!r.exists()) {
				Logger.warn("Additional resource " + r + " doesn't exist");
				return;
			}
			try {
				if (r.isDirectory()) {
					FileUtils.copyFolderToFolder(r, destination);
				} else if (r.isFile()) {
					FileUtils.copyFileToFolder(r, destination);
				}
			} catch (Exception e) {
				Logger.error(e.getMessage(), e);
			}
		});

		// copy bootstrap script
		if (FileUtils.exists(task.getScripts().getBootstrap())) {
			String scriptExtension = getExtension(task.getScripts().getBootstrap().getName());
			File scriptsFolder = new File(destination, "scripts");
			bootstrapFile = new File(scriptsFolder, "bootstrap" + (!scriptExtension.isEmpty() ? "." + scriptExtension : ""));
			try {
				FileUtils.copyFileToFile(task.getScripts().getBootstrap(), bootstrapFile);
				bootstrapFile.setExecutable(true, false);
			} catch (Exception e) {
				Logger.error(e.getMessage(), e);
			}
		}

		Logger.infoUnindent("All additional resources copied!");

	}

	/**
	 * Locates license file
	 * 
	 * @param licenseFile Specified license file
	 * @return Resolved license file
	 */
	protected File resolveLicense(File licenseFile) {

		// if default license file doesn't exist
		if (licenseFile != null && !licenseFile.exists()) {
			Logger.warn("Specified license file doesn't exist: " + licenseFile.getAbsolutePath());
			licenseFile = null;
		}

		// invokes custom license resolver if exists
		if (licenseFile == null) {
			try {
				licenseFile = Context.getContext().resolveLicense(this);
			} catch (Exception e) {
				Logger.error(e.getMessage());
			}
		}

		// if license is still null, looks for LICENSE file
		if (licenseFile == null || !licenseFile.exists()) {
			licenseFile = new File(Context.getContext().getRootDir(), "LICENSE");
			if (!licenseFile.exists()) licenseFile = null;
		}

		if (licenseFile != null) {
			Logger.info("License file found: " + licenseFile.getAbsolutePath());
		} else {
			Logger.warn("No license file specified");
		}

		return licenseFile;
	}

	/**
	 * Locates assets or default icon file if the specified one doesn't exist or
	 * isn't specified
	 * 
	 * @param iconFile     Specified icon file
	 * @param name         Name
	 * @param assetsFolder Assets folder
	 * @return Resolved icon file
	 * @throws Exception Process failed
	 */
	protected File resolveIcon(File iconFile, String name, File assetsFolder) throws Exception {

		// searchs for specific icons
		switch (task.getPlatform()) {
		case linux:
			iconFile = FileUtils.exists(task.getLinuxConfig().getPngFile()) ? task.getLinuxConfig().getPngFile() : null;
			break;
		case mac:
			iconFile = FileUtils.exists(task.getMacConfig().getIcnsFile()) ? task.getMacConfig().getIcnsFile() : null;
			break;
		case windows:
			iconFile = FileUtils.exists(task.getWinConfig().getIcoFile()) ? task.getWinConfig().getIcoFile() : null;
			break;
		default:
		}

		String iconExtension = IconUtils.getIconFileExtensionByPlatform(task.getPlatform());

		// if not specific icon specified for target platform, searchs for an icon in
		// "${assetsDir}" folder
		if (iconFile == null) {
			iconFile = new File(task.getAssetsDir(), task.getPlatform() + "/" + name + iconExtension);
		}

		// if there's no icon yet, uses default one
		if (!iconFile.exists()) {
			iconFile = new File(assetsFolder, iconFile.getName());
			FileUtils.copyResourceToFile("/" + task.getPlatform() + "/default-icon" + iconExtension, iconFile);
		}

		Logger.info("Icon file resolved: " + iconFile.getAbsolutePath());

		return iconFile;
	}

	/**
	 * Bundling app folder in tarball and/or zipball
	 * 
	 * @return Generated bundles
	 * @throws Exception Process failed
	 */
	public List<File> createBundles() throws Exception {

		List<File> bundles = new ArrayList<>();

		Logger.infoIndent("Creating bundles ...");

		if (task.getCreateZipball()) {
			File zipball = Context.getContext().createZipball(this);
			Logger.info("Zipball created: " + zipball);
			bundles.add(zipball);
		}

		if (task.getCreateTarball()) {
			File tarball = Context.getContext().createTarball(this);
			Logger.info("Tarball created: " + tarball);
			bundles.add(tarball);
		}

		Logger.infoUnindent("Bundles created!");

		return bundles;
	}

	private void createAppStructure() throws Exception {

		Logger.infoIndent("Creating app structure ...");

		// creates output directory if it doesn't exist
		if (!task.getOutputDirectory().exists()) {
			task.getOutputDirectory().mkdirs();
		}

		// creates app destination folder
		appFolder = new File(task.getOutputDirectory(), task.getAppName());
		if (appFolder.exists()) {
			FileUtils.removeFolder(appFolder);
			Logger.info("Old app folder removed " + appFolder.getAbsolutePath());
		}
		appFolder = FileUtils.mkdir(task.getOutputDirectory(), task.getAppName());
		Logger.info("App folder created: " + appFolder.getAbsolutePath());

		// creates folder for intermmediate assets
		assetsFolder = FileUtils.mkdir(task.getOutputDirectory(), "assets");
		Logger.info("Assets folder created: " + assetsFolder.getAbsolutePath());

		// create the rest of the structure
		doCreateAppStructure();

		Logger.infoUnindent("App structure created!");

	}

	public File createApp() throws Exception {

		Logger.infoIndent("Creating app ...");

		init();

		// creates app folders structure
		createAppStructure();

		// resolve resources
		resolveResources();

		// copies additional resources
		copyAdditionalResources(task.getAdditionalResources(), resourcesDestinationFolder);

		// copies all dependencies to Java folder
		Logger.infoIndent("Copying all dependencies ...");
		libsFolder = task.getCopyDependencies() ? Context.getContext().copyDependencies(this) : null;
		Logger.infoUnindent("Dependencies copied to " + libsFolder + "!");

		// creates a runnable jar file
		if (task.getRunnableJar() != null && task.getRunnableJar().exists()) {
			Logger.info("Using runnable JAR: " + task.getRunnableJar());
			jarFile = task.getRunnableJar();
		} else {
			Logger.infoIndent("Creating runnable JAR...");
			jarFile = Context.getContext().createRunnableJar(this);
			Logger.infoUnindent("Runnable jar created in " + jarFile + "!");
		}

		// embeds a JRE if is required
		generateJre.apply(this);

		File appFile = doCreateApp();

		Logger.infoUnindent("App created in " + appFolder.getAbsolutePath() + "!");

		return appFile;
	}

	public List<File> generateInstallers() throws Exception {
		List<File> installers = new ArrayList<>();

		if (!task.getGenerateInstaller()) {
			Logger.warn("Installer generation is disabled by 'generateInstaller' property!");
			return installers;
		}

		Logger.infoIndent("Generating installers ...");

		init();

		// creates folder for intermmediate assets if it doesn't exist
		assetsFolder = FileUtils.mkdir(task.getOutputDirectory(), "assets");

		// invokes installer producers
		
		for (ArtifactGenerator<?> generator : Context.getContext().getInstallerGenerators(task.getPlatform())) {
			try {
				Logger.infoIndent("Generating " + generator.getArtifactName() + "...");
				File artifact = generator.apply(this);
				if (artifact != null) {
					addIgnoreNull(installers, artifact);
					Logger.infoUnindent(generator.getArtifactName() + " generated in " + artifact + "!");
				} else {
					Logger.warnUnindent(generator.getArtifactName() + " NOT generated!!!");
				}
			} catch (Exception e) {
				Logger.errorUnindent(generator.getArtifactName() + " generation failed due to: " + e.getMessage(), e);
			}
		}

		Logger.infoUnindent("Installers generated! " + installers);

		return installers;
	}

	protected abstract void doCreateAppStructure() throws Exception;

	public abstract File doCreateApp() throws Exception;

	public abstract void doInit() throws Exception;

}
