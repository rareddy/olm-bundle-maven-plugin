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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KubeResources {
    List<Map<?, ?>> deployments = new ArrayList<>();
    List<Map<?, ?>> clusterRoles = new ArrayList<>();
    List<Map<?, ?>> clusterRoleBindings = new ArrayList<>();
    List<Map<?, ?>> roles = new ArrayList<>();
    List<Map<?, ?>> roleBindings = new ArrayList<>();
    List<Map<?, ?>> serviceAccounts = new ArrayList<>();
    List<Map<?, ?>> service = new ArrayList<>();

    void addDeployment(Map<?, ?> item) {
        this.deployments.add(item);
    }
    void addClusterRole(Map<?, ?> item) {
        this.clusterRoles.add(item);
    }
    void addClusterRoleBinding(Map<?, ?> item) {
        this.clusterRoleBindings.add(item);
    }
    void addRole(Map<?, ?> item) {
        this.roles.add(item);
    }
    void addRoleBinding(Map<?, ?> item) {
        this.roleBindings.add(item);
    }
    void addServiceAccount(Map<?, ?> item) {
        this.serviceAccounts.add(item);
    }
    void addService(Map<?, ?> item) {
        this.service.add(item);
    }

    List<String> serviceAccounts() {
        ArrayList<String> list = new ArrayList<>();
        for (Map<?, ?> item : this.serviceAccounts) {
            list.add(YamlUtil.find(item, "metadata^name", String.class));
        }
        return list;
    }

    Map<?, ?> clusterRolesForServiceAccount(String serviceAccountName) {
        for (Map<?, ?> item : this.clusterRoleBindings) {
            Object obj = item.get("subjects");
            if (obj instanceof List) {
                List<?> objList = (List<?>)obj;
                for(Object obj2 : objList) {
                    Map<?, ?> subject = (Map<?, ?>)obj2;
                    if (YamlUtil.find(subject, "name", String.class).equals(serviceAccountName)) {
                        String roleNme = YamlUtil.find(item, "roleRef^name", String.class);
                        for (Map<?, ?> role : this.clusterRoles) {
                            if (YamlUtil.find(role, "metadata^name", String.class).equals(roleNme)) {
                                return role;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    Map<?, ?> rolesForServiceAccount(String serviceAccountName) {
        for (Map<?, ?> item : this.roleBindings) {
            Object obj = item.get("subjects");
            if (obj instanceof List) {
                List<?> objList = (List<?>)obj;
                for(Object obj2 : objList) {
                    Map<?, ?> subject = (Map<?, ?>)obj2;
                    if (YamlUtil.find(subject, "name", String.class).equals(serviceAccountName)) {
                        String roleNme = YamlUtil.find(item, "roleRef^name", String.class);
                        for (Map<?, ?> role : this.roles) {
                            if (YamlUtil.find(role, "metadata^name", String.class).equals(roleNme)) {
                                return role;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    List<Map<?,?>> deployments() {
        ArrayList<Map<?,?>> list = new ArrayList<>();
        for (Map<?, ?> item : this.deployments) {
            Map<?,?> spec = (Map<?,?>)item.get("spec");

            // delete quarkus verboseness
            YamlUtil.delete(spec, "template^metadata^annotations");
            YamlUtil.delete(spec, "selector^matchLabels^app.kubernetes.io/version");
            YamlUtil.delete(spec, "selector^matchLabels^app.kubernetes.io/name");
            YamlUtil.delete(spec, "template^metadata^labels^app.kubernetes.io/version");
            YamlUtil.delete(spec, "template^metadata^labels^app.kubernetes.io/name");

            String name = YamlUtil.find(item, "metadata^name", String.class);
            list.add(Map.of("name", name, "spec", spec));
        }
        return list;

    }
}
