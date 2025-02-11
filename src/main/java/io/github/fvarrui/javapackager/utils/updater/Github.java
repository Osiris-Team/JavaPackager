package io.github.fvarrui.javapackager.utils.updater;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class Github {

    /**
     * Searches the latest GitHub release and returns a {@link SearchResult} object with all the relevant information.
     *
     * @param repoName           GitHub repository name.
     * @param currentVersion     current version of the installed software.
     * @param assetNamePredicate predicate that contains the asset name and ist used to determine the asset to download.
     */
    public static SearchResult searchUpdate(String repoName, String currentVersion, Predicate<String> assetNamePredicate) {
        Exception exception = null;
        boolean updateAvailable = false;
        String downloadUrl = null;
        String latestVersion = null;
        String downloadFile = null;
        String sha256 = null;
        try {
            JsonObject latestRelease = Json.fromUrlAsObject("https://api.github.com/repos/" + repoName + "/releases/latest");
            latestVersion = latestRelease.get("tag_name").getAsString();
            if (latestVersion != null)
                latestVersion = latestVersion.replaceAll("[^0-9.]", ""); // Before passing over remove everything except numbers and dots
            if (new UtilsVersion().isLatestBigger(currentVersion, latestVersion)) {
                updateAvailable = true;

                // Find asset-name containing our provided asset-name
                for (JsonElement el : latestRelease.getAsJsonArray("assets")) {
                    JsonObject obj = el.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    if (assetNamePredicate.test(name)) {
                        downloadFile = name;
                        downloadUrl = obj.get("browser_download_url").getAsString();
                        break;
                    }
                }

                if (downloadUrl == null) {
                    List<String> names = new ArrayList<>();
                    for (JsonElement el : latestRelease.getAsJsonArray("assets")) {
                        JsonObject obj = el.getAsJsonObject();
                        String n = obj.get("name").getAsString();
                        names.add(n);
                    }
                    throw new Exception("Failed to find an asset-name matching the assetNamePredicate inside of " + Arrays.toString(names.toArray()));
                }

                // Determine sha256
                String expectedShaAssetName = downloadFile + ".sha256";
                for (JsonElement el : latestRelease.getAsJsonArray("assets")) {
                    JsonObject obj = el.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    if (name.equals(expectedShaAssetName)) {
                        sha256 = IOUtils.toString(new URL(obj.get("browser_download_url").getAsString()), StandardCharsets.UTF_8);
                        break;
                    }
                }

            }
        } catch (Exception e) {
            exception = e;
        }

        return new SearchResult(updateAvailable, exception, latestVersion, downloadUrl, downloadFile, sha256);
    }
}
