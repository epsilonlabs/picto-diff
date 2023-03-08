package org.eclipse.epsilon.picto.diff.engines.csv;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

import org.eclipse.epsilon.picto.StaticContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.engines.html.HtmlDiffEngine;

public class CsvDiffEngine extends HtmlDiffEngine {

	@Override
	public void diff(ViewTree diffView, ViewTree left, ViewTree right) throws Exception {
		super.diff(diffView, getHtmlViewTree(left), getHtmlViewTree(right));
	}

	private ViewTree getHtmlViewTree(ViewTree csvViewTree) throws Exception {
		ViewTree htmlViewTree = new ViewTree();
		htmlViewTree.setFormat("html");
		htmlViewTree.setContent(new CsvDiffContentTransformer().transform(csvViewTree.getContent()));

		return htmlViewTree;
	}

	public static void main(String[] args) throws Exception {

		String filesLocationFormat = "files/csvDiffEngine/%s";
		String outputFolder = "diffResult/csvDiffEngine";
		String outputLocationFormat = outputFolder + "/%s-diffResult.html";

		File directory = new File(outputFolder);
		if (!directory.exists()) {
			directory.mkdirs();
		}

		String baselineContent = new String(
				Files.readAllBytes(Paths.get(String.format(filesLocationFormat, "types-baseline.csv"))));
		ViewTree baselineView = new ViewTree();
		baselineView.setPromise(new StaticContentPromise(baselineContent));
		baselineView.setFormat("csv");

		List<String> modifiedFiles = Arrays.asList(
				"types-addType.csv");

		for (String file : modifiedFiles) {

			String modifiedFile = String.format(filesLocationFormat, file);
			String modifiedContent = new String(Files.readAllBytes(Paths.get(modifiedFile)));

			ViewTree modifiedView = new ViewTree();
			modifiedView.setFormat("csv");
			modifiedView.setPromise(new StaticContentPromise(modifiedContent));

			ViewTree diffView = new ViewTree();
			CsvDiffEngine engine = new CsvDiffEngine();
			try {
				engine.diff(diffView, baselineView, modifiedView);
				Files.write(Paths.get(String.format(outputLocationFormat, file)),
						diffView.getContent().getText().getBytes(),
						StandardOpenOption.CREATE,
						StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING);
			}
			catch (Exception ex) {
				System.out.println("-----------------------------------------");
				System.out.println(String.format("Modified file %s fails", file));
				ex.printStackTrace();
			}
		}
		System.out.println("done");
	}

	@Override
	public boolean supports(String format) {
		return format.equals("csv");
	}
}

