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
import org.eclipse.wst.common.project.facet.core.IFacetedProjectWorkingCopy;
import org.eclipse.wst.common.project.facet.core.ProjectFacetDetector;
import org.eclipse.wst.common.project.facet.core.VersionFormatException;
import org.jboss.ide.eclipse.as.springboot.SpringBootFacet;

public class SpringBootProjectFacetDetector extends ProjectFacetDetector {

	public SpringBootProjectFacetDetector() {
		// keep for reflection instantiation
	}

	@Override
	public void detect(IFacetedProjectWorkingCopy fpjwc, IProgressMonitor monitor) throws CoreException {
		if(fpjwc.hasProjectFacet(SpringBootFacet.FACET)) {
			return;
		}
		IProject project = fpjwc.getFacetedProject().getProject();
		if(new SpringBootMavenDetector().hasSpringBootDependencies(project)) {
			try {
				fpjwc.addProjectFacet(SpringBootFacet.FACET.getLatestVersion());
			} catch (VersionFormatException | CoreException e) {
				SpringBootPluginActivator.pluginLog().logError(e);
			}
		}
	}

}
