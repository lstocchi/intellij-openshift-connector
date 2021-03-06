/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.openshift.actions.component;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Objects;
import javax.swing.tree.TreePath;

import org.jboss.tools.intellij.openshift.actions.OdoAction;
import org.jboss.tools.intellij.openshift.tree.LazyMutableTreeNode;
import org.jboss.tools.intellij.openshift.tree.application.ApplicationNode;
import org.jboss.tools.intellij.openshift.tree.application.ApplicationsRootNode;
import org.jboss.tools.intellij.openshift.tree.application.ComponentNode;
import org.jboss.tools.intellij.openshift.utils.ExecHelper;
import org.jboss.tools.intellij.openshift.utils.UIHelper;
import org.jboss.tools.intellij.openshift.utils.odo.Component;
import org.jboss.tools.intellij.openshift.utils.odo.ComponentInfo;
import org.jboss.tools.intellij.openshift.utils.odo.ComponentState;
import org.jboss.tools.intellij.openshift.utils.odo.Odo;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public abstract class DebugComponentAction extends OdoAction {

    private static final Logger LOG = LoggerFactory.getLogger(DebugComponentAction.class);

    private RunnerAndConfigurationSettings runSettings;

    private ExecutionEnvironment environment;

    public DebugComponentAction() {
        super(ComponentNode.class);
    }

    @Override
    public boolean isVisible(Object selected) {
        boolean visible = super.isVisible(selected);
        if (visible) {
            ComponentNode componentNode = (ComponentNode) selected;
            Component component = (Component) componentNode.getUserObject();
            return (isPushed(component) && isDebuggable(componentNode, component));
        }
        return false;
    }

    private boolean isPushed(Component component) {
        return component.getState() == ComponentState.PUSHED;
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent,
                                TreePath path, Object selected, Odo odo) {
        ComponentNode componentNode = (ComponentNode) selected;
        Component component = (Component) componentNode.getUserObject();
        ApplicationNode applicationNode = (ApplicationNode) componentNode.getParent();
        LazyMutableTreeNode projectNode = (LazyMutableTreeNode) applicationNode.getParent();

        Project project = anActionEvent.getData(CommonDataKeys.PROJECT);
        if (project == null) {
            return;
        }

        RunManager runManager = RunManager.getInstance(project);
        final Integer port = createOrFindPortFromConfiguration(runManager, component);
        if (port == null) {
            return;
        }
        ExecHelper.submit(() -> {

            // test if the port is available before running odo,
            // if not, it means odo is already running. //TODO use 'odo debug status'
            if (isAvailable(port)) {
                ProgressManager.getInstance().run(
                    new Task.Modal(project, "Starting odo", false) {
                        public void run(@NotNull ProgressIndicator indicator) {
                            indicator.setText("Starting debugger session for the component "
                                + component.getName());
                            indicator.setIndeterminate(true);
                            try {
                                odo.debug(
                                    projectNode.toString(),
                                    applicationNode.toString(),
                                    component.getPath(),
                                    component.getName(),
                                    port);
                            } catch (IOException e) {
                                UIHelper.executeInUI(() -> Messages.showErrorDialog(
                                    "Error: " + e.getLocalizedMessage(), "Odo Debug"));
                                return;
                            }
                            try {
                                Thread.sleep(2000L);
                            } catch (InterruptedException e) {
                                LOG.error(e.getLocalizedMessage(), e);
                            }
                        }
                    });

            }

            // check if debugger process is already running.
            if (ExecutionManagerImpl.isProcessRunning(getEnvironment().getContentToReuse())) {
                UIHelper.executeInUI(() ->
                    Messages.showMessageDialog(
                        "'" + runSettings.getName() + "' is single-instance run configuration "
                            + "and is already running.",
                        "Process '" + runSettings.getName() + "' Is Running",
                        Messages.getInformationIcon()));
                return;
            }
            ApplicationManager.getApplication().invokeLater(
                () -> {
                    try {
                        Objects.requireNonNull(ProgramRunnerUtil.getRunner(
                            DefaultDebugExecutor.getDebugExecutorInstance().getId(),
                            runSettings)).execute(getEnvironment());
                    } catch (ExecutionException e) {
                        LOG.error(e.getLocalizedMessage(), e);
                    }
                });
        });

    }

    private Integer createOrFindPortFromConfiguration(RunManager runManager, Component component) {
        ConfigurationType configurationType = getConfigurationType();
        String configurationName = component.getName() + " Remote Debug";
        Integer port;

        //lookup if existing config already exist, based on name and type
        runSettings = runManager.findConfigurationByTypeAndName(
            configurationType.getId(), configurationName);
        if (runSettings == null) {
            // no run configuration found, create one and assign an available port
            runSettings = runManager.createConfiguration(
                configurationName, configurationType.getConfigurationFactories()[0]);
            try {
                // find an available port and use it
                ServerSocket serverSocket = new ServerSocket(0);
                port = serverSocket.getLocalPort();
                serverSocket.close();
                // delegates configuration of run configuration
                initConfiguration(runSettings.getConfiguration(), port);
                runSettings.setSingleton(true);
                runManager.addConfiguration(runSettings);
            } catch (IOException e) {
                Messages.showErrorDialog(
                    "Error: " + e.getLocalizedMessage(), "Odo Debug");
                return null;
            }
        } else {
            port = getPortFromConfiguration(runSettings.getConfiguration());
            if (port == -1) {
                Messages.showErrorDialog(
                    "Error when retrieving local port from configuration.", "Odo Debug");
                return null;
            }

        }
        runManager.setSelectedConfiguration(runSettings);
        return port;
    }

    private boolean isAvailable(Integer port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private ExecutionEnvironment getEnvironment() {
        if (environment == null) {
            try {
                environment = ExecutionEnvironmentBuilder.create(
                    DefaultDebugExecutor.getDebugExecutorInstance(), runSettings).build();
            } catch (ExecutionException e) {
                LOG.error(e.getLocalizedMessage(), e);
            }
        }
        return environment;
    }

    private boolean isDebuggable(ComponentNode componentNode, Component component) {
        ApplicationNode applicationNode = (ApplicationNode) componentNode.getParent();
        try {
            Odo odo = ((ApplicationsRootNode) componentNode.getRoot()).getOdo();
            LazyMutableTreeNode projectNode = (LazyMutableTreeNode) applicationNode.getParent();
            ApplicationsRootNode root = (ApplicationsRootNode) applicationNode.getRoot();
            ComponentInfo info = odo.getComponentInfo(
                root.getClient(),
                projectNode.toString(),
                applicationNode.toString(),
                component.getName());
            return isDebuggable(info.getComponentTypeName());
        } catch (IOException e) {
            UIHelper.executeInUI(() -> Messages.showErrorDialog(
                "Error: " + e.getLocalizedMessage(), "Debug"));
        }
        return false;
    }

    protected abstract boolean isDebuggable(String componentTypeName);

    protected abstract ConfigurationType getConfigurationType();

    protected abstract void initConfiguration(RunConfiguration configuration, Integer port);

    protected abstract int getPortFromConfiguration(RunConfiguration configuration);


}
