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

import org.jboss.tools.foundation.core.plugin.BaseCorePlugin;
import org.jboss.tools.foundation.core.plugin.log.IPluginLog;

public class SpringBootPluginActivator extends BaseCorePlugin {
	
	public static final String PLUGIN_ID = "org.jboss.ide.eclipse.as.springboot";
	private static SpringBootPluginActivator instance;
	
	public SpringBootPluginActivator() {
		SpringBootPluginActivator.setInstance(this);
	}

	private static void setInstance(SpringBootPluginActivator springBootPluginActivator) {
		instance = springBootPluginActivator;
	}

	public static IPluginLog pluginLog() {
		return instance.pluginLogInternal();
	}
}
