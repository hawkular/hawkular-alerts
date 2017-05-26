package org.hawkular.alerts.netty.util;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ManifestUtil {
    private static final String IMPLEMENTATION_VERSION = "Implementation-Version";
    private static final String BUILT_FROM_GIT = "Built-From-Git-SHA1";

    private static final List<String> VERSION_ATTRIBUTES = Arrays.asList(IMPLEMENTATION_VERSION, BUILT_FROM_GIT);

    private Map<String, String> manifestInformation = new HashMap<>();

    public Map<String, String> getFrom() {
        if (!manifestInformation.containsKey(IMPLEMENTATION_VERSION) &&
                !manifestInformation.containsKey(BUILT_FROM_GIT)) {
            try (InputStream inputStream = ManifestUtil.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
                Manifest manifest = new Manifest(inputStream);
                Attributes attr = manifest.getMainAttributes();
                for (String attribute : VERSION_ATTRIBUTES) {
                    manifestInformation.put(attribute, attr.getValue(attribute));
                }
            } catch (Exception e) {
                for (String attribute : VERSION_ATTRIBUTES) {
                    if (manifestInformation.get(attribute) == null) {
                        manifestInformation.put(attribute, "Unknown");
                    }
                }
            }
        }
        return manifestInformation;
    }
}
