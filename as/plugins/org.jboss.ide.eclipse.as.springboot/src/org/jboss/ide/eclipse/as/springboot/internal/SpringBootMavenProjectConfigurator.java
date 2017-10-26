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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IFacetedProjectWorkingCopy;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.eclipse.wst.common.project.facet.core.VersionFormatException;
import org.jboss.ide.eclipse.as.springboot.SpringBootFacet;

public class SpringBootMavenProjectConfigurator extends AbstractProjectConfigurator {

	public SpringBootMavenProjectConfigurator() {
		// keep for reflection instantiation
	}

	@Override
	public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 2);
		IProject project = request.getMavenProjectFacade().getProject();
		IFacetedProject fproj = ProjectFacetsManager.create(project, true, subMonitor.split(1));
		if(shouldConfigureSpringBootFacet(request, fproj)) {
			installSpringBootFacet(fproj, subMonitor.split(1));
		}
		subMonitor.setWorkRemaining(0);
	}

	protected boolean shouldConfigureSpringBootFacet(ProjectConfigurationRequest request, IFacetedProject fproj) {
		return fproj != null
				&& !fproj.hasProjectFacet(SpringBootFacet.FACET)
				&& new SpringBootMavenDetector().hasSpringBootDependencies(request);
	}

	private void installSpringBootFacet(IFacetedProject fproj, IProgressMonitor monitor) {
		IFacetedProjectWorkingCopy fpwc = fproj.createWorkingCopy();
		try {
			fpwc.addProjectFacet(SpringBootFacet.FACET.getLatestVersion());
			fpwc.commitChanges(monitor);
		} catch (VersionFormatException | CoreException e) {
			SpringBootPluginActivator.pluginLog().logError(e);
		}
	}

}
