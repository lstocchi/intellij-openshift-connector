package org.jboss.tools.intellij.openshift.utils.odo;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.client.OpenShiftClient;
import me.snowdrop.servicecatalog.api.model.ServiceInstance;

import java.io.IOException;
import java.util.List;

public interface Odo {
    List<Project> getProjects(OpenShiftClient client);

    void createApplication(String project, String application) throws IOException;

    void describeApplication(String project, String application) throws IOException;

    void deleteApplication(String project, String application) throws IOException;

    void push(String project, String application, String context, String component) throws IOException;

    void describeComponent(String project, String application, String context, String component) throws IOException;

    void watch(String project, String application, String component) throws IOException;

    void createComponentLocal(String project, String application, String componentType, String componentVersion, String component, String source) throws IOException;

    void createComponentGit(String project, String application, String context, String componentType, String componentVersion, String component, String source) throws IOException;

    void createService(String project, String application, String serviceTemplate, String servicePlan, String service) throws IOException;

    String getServiceTemplate(OpenShiftClient client, String project, String application, String service);

    void deleteService(String project, String application, String service) throws IOException;

    List<ComponentType> getComponentTypes() throws IOException;

    List<ServiceTemplate> getServiceTemplates() throws IOException;

    void describeServiceTemplate(String template) throws IOException;

    List<Integer> getServicePorts(OpenShiftClient client, String project, String application, String component);

    List<URL> listURLs(String project, String application, String context, String component) throws IOException;

    void createURL(String project, String application, String context, String component, String name, Integer port) throws IOException;

    void deleteURL(String project, String application, String component, String name) throws IOException;

    void deleteComponent(String project, String application, String context, String component) throws IOException;

    void follow(String project, String application, String context, String component) throws IOException;

    void log(String project, String application, String context, String component) throws IOException;

    void createProject(String project) throws IOException;

    void deleteProject(String project) throws IOException;

    void login(String url, String userName, char[] password, String token) throws IOException;

    void logout() throws IOException;

    List<Application> getApplications(String project) throws IOException;

    List<Component> getComponents(OpenShiftClient client, String project, String application);

    List<ServiceInstance> getServices(OpenShiftClient client, String project, String application);

    List<PersistentVolumeClaim> getStorages(OpenShiftClient client, String project, String application, String component);

    void listComponents() throws IOException;

    void listServices() throws IOException;

    void about() throws IOException;

    void createStorage(String project, String application, String component, String name, String mountPath, String storageSize) throws IOException;

    void deleteStorage(String project, String application, String component, String storage) throws IOException;

    void link(String project, String application, String component, String source, Integer port) throws IOException;
}
