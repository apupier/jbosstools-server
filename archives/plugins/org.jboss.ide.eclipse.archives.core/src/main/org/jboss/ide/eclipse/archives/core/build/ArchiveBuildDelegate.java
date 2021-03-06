/*******************************************************************************
 * Copyright (c) 2007 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.ide.eclipse.archives.core.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.jboss.ide.eclipse.archives.core.ArchivesCore;
import org.jboss.ide.eclipse.archives.core.ArchivesCoreMessages;
import org.jboss.ide.eclipse.archives.core.model.ArchivesModel;
import org.jboss.ide.eclipse.archives.core.model.DirectoryScannerFactory.DirectoryScannerExtension.FileWrapper;
import org.jboss.ide.eclipse.archives.core.model.EventManager;
import org.jboss.ide.eclipse.archives.core.model.IArchive;
import org.jboss.ide.eclipse.archives.core.model.IArchiveFileSet;
import org.jboss.ide.eclipse.archives.core.model.IArchiveFolder;
import org.jboss.ide.eclipse.archives.core.model.IArchiveModelRootNode;
import org.jboss.ide.eclipse.archives.core.model.IArchiveNode;
import org.jboss.ide.eclipse.archives.core.model.IArchiveNodeVisitor;
import org.jboss.ide.eclipse.archives.core.util.ModelUtil;
import org.jboss.ide.eclipse.archives.core.util.PathUtils;
import org.jboss.ide.eclipse.archives.core.util.TrueZipUtil;
import org.jboss.ide.eclipse.archives.core.util.internal.ModelTruezipBridge;
import org.jboss.ide.eclipse.archives.core.util.internal.ModelTruezipBridge.FileWrapperStatusPair;

/**
 * This delegate will either build from the model completely
 * (if the builder has been given a full build request) or
 * incrementally update the changed files in
 * **ANY AND ALL** filesets that they match, regardless of project.
 *
 * @author Rob Stryker (rob.stryker@redhat.com)
 *
 */
public class ArchiveBuildDelegate {

	public static int CANNOT_REGISTER_CODE = 31415;
	public ArchiveBuildDelegate() {
	}


	/**
	 * A full project build has been requested.
	 * @param project The project containing the archive model
	 */
	public IStatus fullProjectBuild(IPath project, IProgressMonitor monitor) {
		EventManager.cleanProjectBuild(project);
		EventManager.startedBuild(project);

		IArchiveModelRootNode root = ArchivesModel.instance().getRoot(project);
		if( root == null ) {
			// Adding a code here. If the project isn't registered yet, 
			// the eclipse code can launch a registration job
			if(ArchivesModel.instance().canReregister(project)) {
				IArchiveModelRootNode registered = ArchivesModel.instance().registerProject(project, monitor);
				if( registered != null ) 
					return Status.OK_STATUS;
			}
			IStatus s = new Status(IStatus.ERROR, ArchivesCore.PLUGIN_ID, CANNOT_REGISTER_CODE,
					ArchivesCore.bind(ArchivesCoreMessages.ErrorLocatingRootNode, project.toOSString()), null);
			EventManager.error(null, new IStatus[]{s});
			monitor.done();
			return Status.OK_STATUS;
		} else {
			IArchiveNode[] nodes = root.getChildren(IArchiveNode.TYPE_ARCHIVE);
			ArrayList<IStatus> errors = new ArrayList<IStatus>();

			String title = ArchivesCore.bind(ArchivesCoreMessages.BuildingProject,
					ArchivesCore.getInstance().getVFS().getProjectName(project));
			SubMonitor progress = SubMonitor.convert(monitor, title, nodes.length * 1000);
			
			for( int i = 0; i < nodes.length; i++ ) {
				errors.addAll(Arrays.asList(
						fullArchiveBuild(
								((IArchive)nodes[i]), progress.split(1000), false)));
			}

			EventManager.finishedBuild(project);
			EventManager.error(null, errors.toArray(new IStatus[errors.size()]));
			monitor.done();
			if( errors.size() == 0 )
				return Status.OK_STATUS;
			
			MultiStatus ms = new MultiStatus(ArchivesCore.PLUGIN_ID, IStatus.ERROR, ArchivesCoreMessages.ErrorBuilding, null);
			for( int i = 0; i < errors.size(); i++ )
				ms.add(errors.get(i));
			return ms;
		}
	}

	protected IArchive[] findReferences(IArchive node2) {
		final Set<IArchive> s = new TreeSet<IArchive>();
		final IPath dest = node2.getArchiveFilePath();
		ArchivesModel.instance().accept(new IArchiveNodeVisitor(){
			public boolean visit(IArchiveNode node) {
				if( node.getNodeType() == IArchiveNode.TYPE_ARCHIVE_FILESET) {
					IArchiveFileSet fs = (IArchiveFileSet)node;
					FileWrapper[] wrapper = fs.getMatches(dest);
					if( wrapper != null && wrapper.length > 0 )
						s.add(fs.getRootArchive());
				}
				return true;
			}
		});
		return (IArchive[]) s.toArray(new IArchive[s.size()]);
	}
	
	/**
	 * Builds an archive entirely, overwriting whatever was in the output destination.
	 * @param pkg The archive to build
	 */
	public IStatus fullArchiveBuild(IArchive pkg, IProgressMonitor monitor) {
		return fullArchiveBuild(pkg, monitor, true);
	}
	protected IStatus fullArchiveBuild(IArchive pkg, IProgressMonitor monitor, boolean log) {
		if( !pkg.canBuild() ) {
			IStatus s = new Status(IStatus.ERROR, ArchivesCore.PLUGIN_ID,
					ArchivesCore.bind(ArchivesCoreMessages.CannotBuildBadConfiguration, pkg.getName()), null);
			if( log )
				EventManager.error(pkg, new IStatus[]{s});
			monitor.done();
			return s;
		}

		EventManager.cleanArchiveBuild(pkg);
		EventManager.startedBuildingArchive(pkg);
		
		IPath filePath = pkg.getArchiveFilePath();
		if( filePath.toFile().isFile()) 
			filePath.toFile().delete();
		else
			TrueZipUtil.javaIODeleteDir(filePath.toFile());
		
		//ModelTruezipBridge.deleteArchive(pkg);
		IPath dest = PathUtils.getGlobalLocation(pkg);
		if( dest != null && !dest.toFile().exists() ) {
			if( !dest.toFile().mkdirs() ) {
				IStatus s = new Status(IStatus.ERROR, ArchivesCore.PLUGIN_ID,
						ArchivesCore.bind(ArchivesCoreMessages.CannotBuildOutputLocationNotWriteable,
								pkg.getName(), dest.toString()), null);
				if( log )
					EventManager.error(pkg, new IStatus[]{s});
				monitor.done();
				return s;
			}
		}

		ArrayList<IStatus> errors = new ArrayList<IStatus>();

		/* 3 steps:
		 * create file: 200
		 * create folders: 800
		 * build filesets: 7000
		 */
		SubMonitor progress = SubMonitor.convert(monitor, ArchivesCore.bind(
				ArchivesCoreMessages.BuildingArchive, pkg.toString()), 8000);

		// Run the pre actions
//		IArchiveAction[] actions = pkg.getActions();
//		for( int i = 0; i < actions.length; i++ ) {
//			if( actions[i].getTime().equals(IArchiveAction.PRE_BUILD)) {
//				actions[i].execute();
//			}
//		}


		if( !ModelTruezipBridge.createFile(pkg) ) {
			IStatus e = new Status(IStatus.ERROR, ArchivesCore.PLUGIN_ID,
					ArchivesCore.bind(ArchivesCoreMessages.ErrorCreatingOutputFile,
							ModelTruezipBridge.getFilePath(pkg),
							pkg.toString()));
			errors.add(e);
		}
		progress.split(200);

		// force create all folders
		IArchiveFolder[] folders = ModelUtil.findAllDescendentFolders(pkg);
		IProgressMonitor folderMonitor = progress.split(800);
		folderMonitor.beginTask(ArchivesCoreMessages.CreatingFolders, folders.length * 100);
		for( int i = 0; i < folders.length; i++ ) {
			if( !ModelTruezipBridge.createFile(folders[i])) {
				IStatus e = new Status(IStatus.ERROR, ArchivesCore.PLUGIN_ID, 
						ArchivesCore.bind(ArchivesCoreMessages.ErrorCreatingOutputFile,
						ModelTruezipBridge.getFilePath(folders[i]),
						folders[i].toString()));
				errors.add(e);
			}
			folderMonitor.worked(100);
		}
		folderMonitor.done();

		// build the filesets
		IArchiveFileSet[] filesets = ModelUtil.findAllDescendentFilesets(pkg);
		IProgressMonitor filesetMonitor = progress.split(6000);
		filesetMonitor.beginTask(ArchivesCoreMessages.BuildingFilesets, filesets.length * 1000);
		for( int i = 0; i < filesets.length; i++ ) {
			IStatus[] errors2 = fullFilesetBuild(filesets[i], progress.split(1000), pkg);
			errors.addAll(Arrays.asList(errors2));
		}
		filesetMonitor.done();

//		// Run the post actions
//		for( int i = 0; i < actions.length; i++ ) {
//			if( actions[i].getTime().equals(IArchiveAction.POST_BUILD)) {
//				actions[i].execute();
//			}
//		}
		
		
		ArrayList<IArchive> referencingArchives = new ArrayList<IArchive>();
		referencingArchives.addAll(Arrays.asList(findReferences(pkg)));
		
		SubMonitor referenceMon = SubMonitor.convert(progress.split(1000), ArchivesCoreMessages.BuildingArchive, 
				referencingArchives.size() * 1000);
		for( Iterator<IArchive> i = referencingArchives.iterator(); i.hasNext();) {
			IArchive toBuild = i.next();
			if( !toBuild.equals(pkg)) {
				errors.add(fullArchiveBuild(toBuild, referenceMon.split(1000), log));
			} else {
				// RECURSE ERROR
			}
		}

		

		EventManager.finishedBuildingArchive(pkg);
		IStatus[] errors2 = errors.toArray(new IStatus[errors.size()]);
		if( log )
			EventManager.error(pkg, errors2 );
		monitor.done();
		if( errors2.length == 0)
			return Status.OK_STATUS;
		
		MultiStatus ms = new MultiStatus(ArchivesCore.PLUGIN_ID, IStatus.ERROR, ArchivesCoreMessages.ErrorBuilding, null);
		for( int i = 0; i < errors.size(); i++ )
			ms.add(errors.get(i));
		return ms;
	}

	/**
	 * Build the given fileset
	 * @param fileset The fileset to match
	 * @param topLevel The top level archive that the fileset belongs to
	 */
	protected IStatus[] fullFilesetBuild(IArchiveFileSet fileset, IProgressMonitor monitor, IArchive topLevel) {
		EventManager.startedCollectingFileSet(fileset);

		// reset the scanner. It *is* a full build afterall
		fileset.resetScanner();
		FileWrapper[] paths = fileset.findMatchingPaths();

		FileWrapperStatusPair result = ModelTruezipBridge.fullFilesetBuild(fileset, monitor, true);

		EventManager.filesUpdated(topLevel, fileset, paths);
		EventManager.finishedCollectingFileSet(fileset);
		return result.s;
	}

	/**
	 * Incremental build.
	 * Parameters are instance sof changed IPath objects
	 * Will search only the given node for matching descendent filesets
	 * @param archive   An archive to limit the scope to, or null if the entire default model
	 * @param addedChanged  A list of added or changed resource paths
	 * @param removed       A list of removed resource paths
	 */
	public void incrementalBuild(IArchive archive, Set<IPath> addedChanged,
			Set<IPath> removed, boolean workspaceRelative, IProgressMonitor monitor2) {
		ArrayList<IStatus> errors = new ArrayList<IStatus>();

		if( addedChanged.size() == 0 && removed.size() == 0 )
			return;
		
		// removed get more work because all filesets are being rescanned before handling the removed
		int totalWork = (addedChanged.size()*100) + (removed.size()*200) + 50 + 500;
		SubMonitor progress = SubMonitor.convert(monitor2, ArchivesCoreMessages.ProjectArchivesIncrementalBuild, totalWork);

		// find any and all filesets that match each file
		Iterator<IPath> i;
		IPath path, globalPath;
		IArchiveFileSet[] matchingFilesets;
		ArrayList<IArchive> topPackagesChanged = new ArrayList<IArchive>();
		ArrayList<IArchiveFileSet> seen = new ArrayList<IArchiveFileSet>();

		// Handle the removed files first. Hopefully the fileset hasn't been reset yet
		// or it could make this block of code fail.
		i = removed.iterator();
		while(i.hasNext()) {
			path = ((IPath)i.next());
			globalPath = !workspaceRelative ? path :
				ArchivesCore.getInstance().getVFS().workspacePathToAbsolutePath(path);

			matchingFilesets = ModelUtil.getMatchingFilesets(archive, path, workspaceRelative);
			localFireAffectedTopLevelPackages(topPackagesChanged, matchingFilesets);
			for( int j = 0; j < matchingFilesets.length; j++ ) {
				IStatus[] errors2 = ModelTruezipBridge.deleteFiles(
						matchingFilesets[j], matchingFilesets[j].getMatches(globalPath),
						new NullProgressMonitor(), false);
				errors.addAll(Arrays.asList(errors2));
				if( !seen.contains(matchingFilesets[j])) {
					seen.add(matchingFilesets[j]);
				}
			}
			EventManager.fileRemoved(path, matchingFilesets);
			progress.split(100);
		}

		// reset all of the filesets that have already matched
		Iterator<IArchiveFileSet> fit = seen.iterator();
		while(fit.hasNext())
			fit.next().resetScanner();

		i = addedChanged.iterator();
		while(i.hasNext()) {
			path = i.next();
			globalPath = !workspaceRelative ? path :
				ArchivesCore.getInstance().getVFS().workspacePathToAbsolutePath(path);
			matchingFilesets = ModelUtil.getMatchingFilesets(archive, path, workspaceRelative);
			localFireAffectedTopLevelPackages(topPackagesChanged, matchingFilesets);
			for( int j = 0; j < matchingFilesets.length; j++ ) {
				if( !seen.contains(matchingFilesets[j])) {
					seen.add(matchingFilesets[j]);
					matchingFilesets[j].resetScanner();
				}
				IStatus[] errors2 = ModelTruezipBridge.copyFiles(matchingFilesets[j],
						matchingFilesets[j].getMatches(globalPath),
						new NullProgressMonitor(), true, false);
				errors.addAll(Arrays.asList(errors2));
			}
			EventManager.fileUpdated(path, matchingFilesets);
			progress.split(200);
		}


		// NOW do the synch
		TrueZipUtil.sync();
		Comparator<IPath> c = new Comparator<IPath>() {
			public int compare(IPath o1, IPath o2) {
				return o1.toOSString().compareTo(o2.toOSString());
			}
		}; 
		Set<IPath> changedPaths = new TreeSet<IPath>(c);
		Iterator<IArchive> i2 = topPackagesChanged.iterator();
		SubMonitor consumedMon = progress.split(500);
		while(i2.hasNext()) {
			try {
				IArchive changed = i2.next();
				if( !addedChanged.contains(changed.getArchiveFilePath()))
					changedPaths.add(changed.getArchiveFilePath());
				EventManager.finishedBuildingArchive(changed);
			} catch( ClassCastException cce ) {
				cce.printStackTrace();  
			}
		}
		
		if( errors.size() > 0 )
			EventManager.error(null, errors.toArray(new IStatus[errors.size()]));
		else {
			incrementalBuild(null, changedPaths, new TreeSet<IPath>(), false, 
					consumedMon);
		}
		progress.split(50);
		progress.done();
	}

	private void localFireAffectedTopLevelPackages(ArrayList<IArchive> affected, IArchiveFileSet[] filesets) {
		for( int i = 0; i < filesets.length; i++ ) {
			if( !affected.contains(filesets[i].getRootArchive())) {
				affected.add(filesets[i].getRootArchive());
				EventManager.startedBuildingArchive(filesets[i].getRootArchive());
			}
		}
	}
}
