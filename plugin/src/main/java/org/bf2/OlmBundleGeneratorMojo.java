/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bf2;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;


@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyCollection=ResolutionScope.COMPILE, requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM, requiresProject = true, threadSafe=true)
public class OlmBundleGeneratorMojo extends AbstractMojo {
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/bundle")
    private File bundleDirectory;

    @Parameter(defaultValue = "${project.build.directory}")
    private File targetDirectory;

    @Parameter( defaultValue = "${plugin}", readonly = true )
    private PluginDescriptor pluginDescriptor;

    @Parameter(name = "bundle-name")
    private String bundleName;

    @Parameter(name = "bundle-version")
    private String bundleVersion;

    @Parameter(name = "previous-bundle-version", required = false)
    private String previousBundleVersion;

    @Parameter(name = "csv-file-template", required = false)
    private File csvFileTemplate;

    @Parameter
    private String channel = "alpha";

    public File getOutputDirectory() throws MojoExecutionException{
        return bundleDirectory;
    }

    public File getManifestDirectory() throws MojoExecutionException {
        File dir = new File(getOutputDirectory(), "manifests");
        if(!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new MojoExecutionException("Error creating manifests directory");
            }
        }
        return dir;
    }

    public File getMetadataDirectory() throws MojoExecutionException {
        File dir = new File(getOutputDirectory(), "metadata");
        if(!dir.exists()) {
            if(!dir.mkdirs()) {
                throw new MojoExecutionException("Error creating metdata directory");
            }
        }
        return dir;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
        try {
            MustacheFactory mf = new DefaultMustacheFactory();

            ClassLoader pluginClassloader = getClassLoader();
            Thread.currentThread().setContextClassLoader(pluginClassloader);

            HashMap<String, String> map = new HashMap<String, String>();
            map.put("bundle-name", bundleName);
            map.put("channel", channel);
            if (bundleVersion == null) {
                map.put("bundle-version", project.getVersion());
            }

            // set output directory for the bundle
            this.bundleDirectory = new File(getOutputDirectory(), bundleVersion);
            if (!bundleDirectory.exists()) {
                if (!bundleDirectory.mkdirs()) {
                    throw new MojoExecutionException("Error creating output bundle directory");
                }
            }

            map.put("replaces", "");
            if (previousBundleVersion != null) {
                map.put("replaces", "replaces: " + bundleName + ".v" + previousBundleVersion);
            }

            // TODO:
            map.put("skips", "");

            createFile(pluginClassloader, mf, map, "annotations.mustache", new File(getMetadataDirectory(), "annotations.yaml"));
            createFile(pluginClassloader, mf, map, "Dockerfile.mustache", new File(getOutputDirectory(), "Dockerfile"));
            createFile(pluginClassloader, mf, map, "priorityclass.mustache", new File(getManifestDirectory(), this.bundleName + ".priorityclass.yaml"));

            KubeResources kubeResources = new KubeResources();
            for(Artifact artifact: project.getArtifacts()){
                if (artifact.getFile() == null || (!artifact.getFile().getName().endsWith(".yml") && !artifact.getFile().getName().endsWith(".yaml"))) {
                    continue;
                }
                File resource = artifact.getFile();

                Yaml yaml = new Yaml();
                Iterator<Object> fragments = yaml.loadAll(new FileReader(resource, Charset.forName("UTF-8"))).iterator();
                while(fragments.hasNext()) {
                    Map<?, ?> fragment = (Map<?, ?>)fragments.next();
                    String kind = YamlUtil.find(fragment, "kind", String.class);
                    String name = YamlUtil.find(fragment, "metadata^name", String.class);
                    if (kind.equals("CustomResourceDefinition")) {
                        File crdFile = new File(getManifestDirectory(), name + ".crd.yaml");
                        getLog().info("Creating the " + crdFile.getName() + " file");
                        YamlUtil.write(fragment, crdFile);
                    } else if (kind.equals("ConfigMap") || kind.equals("Secret")) {
                        YamlUtil.write(fragment, new File(getManifestDirectory(), name + "." + kind + ".yaml"));
                    } else if (kind.equals("Deployment")){
                        kubeResources.addDeployment(fragment);
                    } else if (kind.equals("Role")){
                        kubeResources.addRole(fragment);
                    } else if (kind.equals("RoleBinding")){
                        kubeResources.addRoleBinding(fragment);
                    } else if (kind.equals("ClusterRole")){
                        kubeResources.addClusterRole(fragment);
                    } else if (kind.equals("ClusterRoleBinding")){
                        kubeResources.addClusterRoleBinding(fragment);
                    } else if (kind.equals("Service")){
                        kubeResources.addService(fragment);
                    } else if (kind.equals("ServiceAccount")){
                        kubeResources.addServiceAccount(fragment);
                    }
                }
            }

            // CSV building - permissions
            Yaml yaml = new Yaml();
            File csvFile = new File(getManifestDirectory(), this.bundleName + ".clusterserviceversion.yaml");
            createFile(pluginClassloader, mf, map, "operator-csv.mustache", csvFile);
            Map<?, ?> csvYaml = (Map<?, ?>)yaml.load(new FileReader(csvFile, Charset.forName("UTF-8")));

            List<Map<String, Object>> clusterPermissions = new ArrayList<>();
            List<Map<String, Object>> permissions = new ArrayList<>();
            List<Map<?,?>> deployments = kubeResources.deployments();

            for (String serviceAccount : kubeResources.serviceAccounts()) {
                Map<?, ?> clusterRole = kubeResources.clusterRolesForServiceAccount(serviceAccount);
                if (clusterRole != null) {
                    clusterPermissions.add(YamlUtil.sortedMap("serviceAccountName", serviceAccount, "rules",  clusterRole.get("rules")));
                }
                Map<?, ?> role = kubeResources.rolesForServiceAccount(serviceAccount);
                if (role != null) {
                    permissions.add(YamlUtil.sortedMap("serviceAccountName", serviceAccount, "rules",  role.get("rules")));
                }
            }
            if (!clusterPermissions.isEmpty()) {
                YamlUtil.put((Map<Object,Object>)csvYaml, "spec^install^spec^clusterPermissions", clusterPermissions.toArray());
            }
            if (!permissions.isEmpty()) {
                YamlUtil.put((Map<Object,Object>)csvYaml, "spec^install^spec^permissions", permissions.toArray());
            }
            if (!deployments.isEmpty()) {
                YamlUtil.put((Map<Object,Object>)csvYaml, "spec^install^spec^deployments", deployments);
            }
            // final write
            getLog().info("Creating the " + csvFile.getName() + " file");
            YamlUtil.write(csvYaml, csvFile);
        } catch (Exception e) {
            throw new MojoExecutionException("Error running the olm-bundle-maven-plugin.", e);
        } finally {
            Thread.currentThread().setContextClassLoader(oldCL);
        }
    }

    private void createFile(ClassLoader pluginClassloader, MustacheFactory mf, HashMap<String, String> props, String template, File outputFile) throws Exception {
        getLog().info("Creating the "+outputFile.getName()+" file");
        Mustache mustache = mf.compile(new InputStreamReader(pluginClassloader.getResourceAsStream(template), "UTF-8"), outputFile.getName());
        Writer out = new FileWriter(outputFile, Charset.forName("UTF-8"));
        mustache.execute(out, props);
        out.close();
    }

    private ClassLoader getClassLoader() throws MojoExecutionException {
        try {
            List<URL> pathUrls = new ArrayList<>();
            for (String mavenCompilePath : project.getCompileClasspathElements()) {
                pathUrls.add(new File(mavenCompilePath).toURI().toURL());
            }

            URL[] urlsForClassLoader = pathUrls.toArray(new URL[pathUrls.size()]);
            getLog().debug("urls for URLClassLoader: " + Arrays.asList(urlsForClassLoader));

            // need to define parent classloader which knows all dependencies of the plugin
            return new URLClassLoader(urlsForClassLoader, OlmBundleGeneratorMojo.class.getClassLoader());
        } catch (Exception e) {
            throw new MojoExecutionException("Couldn't create a classloader.", e);
        }
    }
}
