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
package org.jboss.tools.as.test.core.springboot;

import static org.junit.Assert.*;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.ui.internal.UpdateMavenProjectJob;
import org.eclipse.wst.common.project.facet.core.internal.FacetedProjectNature;
import org.jboss.tools.jmx.core.test.util.TestProjectProvider;
import org.junit.Test;

public class SpringBootTest {
	
	@Test
	public void testFacetAdded() throws Exception {
		TestProjectProvider projectProvider = new TestProjectProvider("org.jboss.tools.as.test.core", null, "SpringBootProject", true);
		IProject project = projectProvider.getProject();
		project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		UpdateMavenProjectJob updateMavenProjectJob = new UpdateMavenProjectJob(new IProject[] {project});
		updateMavenProjectJob.schedule();
		updateMavenProjectJob.join();
		assertTrue(project.exists());
		assertTrue(project.isAccessible());
		assertTrue(project.isNatureEnabled( FacetedProjectNature.NATURE_ID ));
	}

}
