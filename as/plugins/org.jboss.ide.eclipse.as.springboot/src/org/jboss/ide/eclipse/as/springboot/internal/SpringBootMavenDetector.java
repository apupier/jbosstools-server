/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.ide.eclipse.as.springboot.internal;

import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;

public class SpringBootMavenDetector {

	private static final String POM_FILE_NAME = "pom.xml";

	public boolean hasSpringBootDependencies(ProjectConfigurationRequest request) {
		MavenProject mavenProject = request.getMavenProject();
		return hasSpringBootDependencies(mavenProject.getModel());
	}
	
	public boolean hasSpringBootDependencies(IProject project) {
		Model model;
		try {
			model = MavenPlugin.getMaven().readModel(project.getFile(POM_FILE_NAME).getContents());
			return hasSpringBootDependencies(model);
		} catch (CoreException e) {
			SpringBootPluginActivator.pluginLog().logError(e);
		}
		return false;
	}

	private boolean hasSpringBootDependencies(Model model) {
		List<Dependency> dependencies = model.getDependencies();
		if(dependencies != null) {
			return dependencies.stream()
					.filter(dep -> "org.springframework.boot".equals(dep.getGroupId()))
					.filter(dep -> "spring-boot".equals(dep.getArtifactId()))
					.findAny().isPresent();
		}
		return false;
	}
}
