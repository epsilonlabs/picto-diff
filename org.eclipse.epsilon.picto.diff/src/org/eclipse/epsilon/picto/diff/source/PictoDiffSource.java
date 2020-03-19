package org.eclipse.epsilon.picto.diff.source;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.emc.plainxml.PlainXmlModel;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.picto.StringContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.FileWrapperEditorPart;
import org.eclipse.epsilon.picto.diff.PictoDiffPlugin;
import org.eclipse.epsilon.picto.diff.ViewTreeMerger;
import org.eclipse.epsilon.picto.diff.engines.dot.DotDiffContext;
import org.eclipse.epsilon.picto.diff.engines.dot.DotDiffEngine;
import org.eclipse.epsilon.picto.diff.engines.dot.DotDiffEngine.DISPLAY_MODE;
import org.eclipse.epsilon.picto.dom.Parameter;
import org.eclipse.epsilon.picto.dom.Picto;
import org.eclipse.epsilon.picto.dom.PictoFactory;
import org.eclipse.epsilon.picto.source.PictoSource;
import org.eclipse.epsilon.picto.source.PictoSourceExtensionPointManager;
import org.eclipse.epsilon.picto.source.SimpleSource;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

public class PictoDiffSource extends SimpleSource {

	@Override
	public String getFormat() {
		return "html";
	}

	@Override
	public String getFileExtension() {
		return "pictodiff";
	}
	
	@Override
	public Picto getRenderingMetadata(IEditorPart editorPart) {
		IFile diffFile = getFile(editorPart);
		if (diffFile.exists()) {
			Picto metadata = PictoFactory.eINSTANCE.createPicto();
			metadata.setTemplate(getFile(editorPart).getLocation().toOSString());
			metadata.setFormat(getFormat());
			// get the two internal picto files as parameters of the Picto dom
			// TODO: check that both files have compatible representations/technologies for diffing
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(diffFile.getContents(true)));
				Parameter pLeft = PictoFactory.eINSTANCE.createParameter();
				pLeft.setName("left");
				pLeft.setFile(reader.readLine());
				metadata.getParameters().add(pLeft);
				Parameter pRight = PictoFactory.eINSTANCE.createParameter();
				pRight.setName("right");
				pRight.setFile(reader.readLine());
				metadata.getParameters().add(pRight);
			} catch (Exception e) {
				return null;
			}
			return metadata;
		}
		return null;
	}
	
	@Override
	public boolean supports(IEditorPart editorPart) {
		if (!super.supports(editorPart)) return false;
		Picto picto = getRenderingMetadata(editorPart);
		return picto != null;
	}
	
	@Override
	public ViewTree getViewTree(IEditorPart editor) throws Exception {

		Picto pictoDiff = getRenderingMetadata(editor);
		List<Parameter> parameters = pictoDiff.getParameters();
		Parameter pLeft = parameters.stream().filter(p -> p.getName().equals("left")).findFirst().get();
		Parameter pRight = parameters.stream().filter(p -> p.getName().equals("right")).findFirst().get();

		IProject project = null;
		if (editor.getEditorInput() instanceof IFileEditorInput) {
			IFileEditorInput input = (IFileEditorInput)editor.getEditorInput();
			project = input.getFile().getProject();
		}
		if (project == null) {
			return null;
		}
		FileWrapperEditorPart leftWrapper = 
				new FileWrapperEditorPart(project.getFile(new Path(pLeft.getFile())));
		FileWrapperEditorPart rightWrapper = 
				new FileWrapperEditorPart(project.getFile(new Path(pRight.getFile())));

		PictoSource leftPictoSource = getSource(leftWrapper);
		PictoSource rightPictoSource = getSource(rightWrapper);

		// asuming all we are working with now are dot files
		DotDiffContext diffContext = 
				new DotDiffContext(
						leftWrapper.getFile().getLocation().toOSString(),
						rightWrapper.getFile().getLocation().toOSString());
		DotDiffEngine diffEngine = new DotDiffEngine(diffContext, DISPLAY_MODE.CHANGED);
		diffEngine.load();
		diffEngine.compare();

		File tempDir = Files.createTempDirectory("picto").toFile();
		File temp = Files.createTempFile(tempDir.toPath(), "temp-svg", ".dot").toFile();
		diffEngine.saveSVGFile(temp);
		
		PlainXmlModel xml_model = new PlainXmlModel();
		StringProperties targetProperties = new StringProperties();
		targetProperties.put(PlainXmlModel.PROPERTY_FILE, temp.getAbsolutePath());
		targetProperties.put(PlainXmlModel.PROPERTY_NAME, "M");
		targetProperties.put(PlainXmlModel.PROPERTY_READONLOAD, "true");
		targetProperties.put(PlainXmlModel.PROPERTY_STOREONDISPOSAL, "true");
		xml_model.load(targetProperties);


		ArrayList<IModel> allTheModels = new ArrayList<IModel>();
		allTheModels.add(xml_model);
		
		EolModule eolModule = new EolModule();
		for (IModel theModel : allTheModels) {
			eolModule.getContext().getModelRepository().addModel(theModel);
		}
		java.net.URI eolFile = PictoDiffPlugin.getDefault().getBundle()
				.getResource("transformations/addScriptToSVG.eol").toURI();
		eolModule.parse(eolFile);
		eolModule.execute();
		eolModule.getContext().getModelRepository().dispose();
		
		String c = new String(Files.readAllBytes(Paths.get(temp.toURI())));
		
		ViewTree viewTree = new ViewTree();
		String graph_format = "html";
		String graph_icon = "diagram-ff0000";
		ArrayList<String> p = new ArrayList<String>();
		p.add("Diff Model");
		viewTree.addPath(p, new StringContentPromise(c), graph_format, graph_icon, new ArrayList<>());
		
		p.remove(0);
		p.add("Left Model");
		StringContentPromise promise = new StringContentPromise(diffContext.getSourceGraphPromise());
		viewTree.addPath(p, promise, graph_format, graph_icon, new ArrayList<>());
		
		p.remove(0);
		p.add("Right Model");
		promise = new StringContentPromise(diffContext.getTargetGraphPromise());
		viewTree.addPath(p, promise, graph_format, graph_icon, new ArrayList<>());

		p.remove(0);
		p.add("");
		ViewTreeMerger.append(viewTree, leftPictoSource.getViewTree(leftWrapper), p, "Original Left");
		ViewTreeMerger.append(viewTree, rightPictoSource.getViewTree(rightWrapper), p,  "Original Right");
		
		return viewTree;
	}

	protected PictoSource getSource(IEditorPart editorPart) {
		List<PictoSource> sources = 
				new PictoSourceExtensionPointManager().getExtensions();
		for (PictoSource source : sources) {
			if (source.supports(editorPart)) {
				return source;
			}
		}
		return null;
	}

}
