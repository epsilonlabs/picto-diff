package org.eclipse.epsilon.picto.diff.engines.html;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

import org.eclipse.epsilon.picto.StaticContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

public class HtmlDiffEngine implements DiffEngine {

	// any html element to be diffed should declare this attribute
	private static final String DIFF_ID = "pictoid";
	private static final String DIFF_ELEMS_SELECTOR = String.format("[%s]", DIFF_ID);

	private static final String ADDED_CLASS = "added";
	private static final String PREVIOUS_CHANGED_CLASS = "previouschanged";
	private static final String CURRENT_CHANGED_CLASS = "currentchanged";
	private static final String DELETED_CLASS = "deleted";
	private static final String UNCHANGED_CLASS = "unchanged";

	// html diff elements to compare and merge; traversed at the same time
	private Elements leftDiffElems;
	private Elements rightDiffElems;
	private int leftDiffIndex;
	private int rightDiffIndex;

	public static void main(String[] args) throws Exception {

		String filesLocationFormat = "files/htmlDiffEngine/%s";
		String outputFolder = "diffResult/htmlDiffEngine";
		String outputLocationFormat = outputFolder + "/%s-diffResult.html";

		File directory = new File(outputFolder);
		if (!directory.exists()) {
			directory.mkdirs();
		}

		String baselineContent = new String(
				Files.readAllBytes(Paths.get(String.format(filesLocationFormat, "risks-baseline.html"))));
		ViewTree baselineView = new ViewTree();
		baselineView.setPromise(new StaticContentPromise(baselineContent));
		baselineView.setFormat("html");

		List<String> modifiedFiles = Arrays.asList(
				"risks-addRisk.html",
				"risks-deleteRisk.html",
				"risks-modifyRisk.html");

		//		modifiedFiles = Arrays.asList("risks-deleteRisk.html");

		for (String file : modifiedFiles) {

			String modifiedFile = String.format(filesLocationFormat, file);
			String modifiedContent = new String(Files.readAllBytes(Paths.get(modifiedFile)));

			ViewTree modifiedView = new ViewTree();
			modifiedView.setFormat("html");
			modifiedView.setPromise(new StaticContentPromise(modifiedContent));

			ViewTree diffView = new ViewTree();
			HtmlDiffEngine engine = new HtmlDiffEngine();
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
		return format.equals("html");
	}

	private void addedElement(Document diffDoc, Element addedElem) {
		// add the whole element as new
		// TODO: update this method if support for nested diffelements is needed
		Element addedCopy = addedElem.clone();
		addedCopy.addClass(ADDED_CLASS);
		
		appendElement(diffDoc, addedElem.parents(), addedCopy);
	}

	private void changedElement(Document diffDoc, Element leftElem, Element rightElem) {
		// add the previous element as previous, and the new as new
		// TODO: update this method if support for nested diffelements is needed
		Element leftCopy = leftElem.clone();
		leftCopy.addClass(PREVIOUS_CHANGED_CLASS);
		appendElement(diffDoc, leftElem.parents(), leftCopy);

		Element rightCopy = rightElem.clone();
		rightCopy.addClass(CURRENT_CHANGED_CLASS);
		appendElement(diffDoc, rightElem.parents(), rightCopy);
	}

	private void deletedElement(Document diffDoc, Element deletedElem) {
		// add the whole element as deleted.
		// TODO: update this method if support for nested diffelements is needed
		Element deletedCopy = deletedElem.clone();
		deletedCopy.addClass(DELETED_CLASS);

		appendElement(diffDoc, deletedElem.parents(), deletedCopy);
	}

	private void unchangedElement(Document diffDoc, Element unchangedElem) {
		// add the whole element as unchanged.
		// TODO: update this method if support for nested diffelements is needed
		Element unchangedCopy = unchangedElem.clone();
		unchangedCopy.addClass(UNCHANGED_CLASS);

		appendElement(diffDoc, unchangedElem.parents(), unchangedCopy);
	}

	/**
	 * Include element in the document under the specified parents path
	 */
	private void appendElement(Document diffDoc, Elements parents, Element elem) {

		Element diffParent = diffDoc.body();
		// first two parents are html and body, we can start with the third
		int parentIndex = parents.size() - 3;

		while (parentIndex >= 0) {
			Element parent = parents.get(parentIndex);
			Element newDiffParent = null;
			for (Element child : diffParent.children()) {
				if (shallowEquals(child, parent)) {
					newDiffParent = child;
					break;
				}
			}
			if (newDiffParent == null) {
				throw new RuntimeException(
						"The same path from the root must exist");
			}
			diffParent = newDiffParent;
			parentIndex--;
		}
		elem.appendTo(diffParent);
	}

	private Element findElement(Element elem, Elements elems) {
		for (Element otherElem : elems) {
			if (equalDiffElements(elem, otherElem)) {
				return otherElem;
			}
		}
		return null;
	}

	/**
	 * Copy element contents up until finding the first child element or a
	 * blank line
	 */
	private Element shallowCopy(Element elem) {
		Element copy = elem.shallowClone();
		for (Node child : elem.childNodes()) {
			if (child instanceof Element) {
				break;
			}
			else if (child instanceof TextNode) {
				String text = ((TextNode) child).text();
				if (text.trim().equals("")) {
					break; // avoid blank lines
				}
				else {
					copy.appendChild(child.clone());
				}
			}
		}
		return copy;
	}

	/**
	 * The content of both elements is the same, without comparing children elements
	 */
	private boolean shallowEquals(Element thisElem, Element thatElem) {
		return shallowCopy(thisElem).hasSameValue(shallowCopy(thatElem));
	}

	/**
	 * The path to the root matches for both elements
	 */
	private boolean sameParents(Element thisElem, Element thatElem) {
		Elements thisParents = thisElem.parents();
		Elements thatParents = thatElem.parents();

		if (thisParents.size() != thatParents.size()) {
			return false;
		}

		int thisParentsIndex = 0;
		int thatParentsIndex = 0;
		while (thisParentsIndex < thisParents.size() && thatParentsIndex < thatParents.size()) {
			if (!shallowEquals(thisParents.get(thisParentsIndex), thatParents.get(thatParentsIndex))) {
				return false;
			}
			thisParentsIndex++;
			thatParentsIndex++;
		}

		return true;
	}

	/**
	 * Both elements share the same DIFF_ID
	 */
	private boolean equalDiffElements(Element thisElem, Element thatElem) {
		return thisElem.attr(DIFF_ID).equals(thatElem.attr(DIFF_ID))
				&& sameParents(thisElem, thatElem);
	}

	private Document compare(Document leftDoc, Document rightDoc) {

		leftDiffElems = leftDoc.select(DIFF_ELEMS_SELECTOR);
		rightDiffElems = rightDoc.select(DIFF_ELEMS_SELECTOR);

		leftDiffIndex = 0;
		rightDiffIndex = 0;

		Document diffDoc = leftDoc.clone();
		includeDiffClasses(diffDoc.head());
		// remove previous diffElements from diffDoc (get "empty" html skeleton)
		diffDoc.select(DIFF_ELEMS_SELECTOR).remove();


		while (leftDiffIndex < leftDiffElems.size() && rightDiffIndex < rightDiffElems.size()) {
			compareElement(diffDoc, leftDiffElems.get(leftDiffIndex), rightDiffElems.get(rightDiffIndex));
		}
		while (leftDiffIndex < leftDiffElems.size()) {
			compareElement(diffDoc, leftDiffElems.get(leftDiffIndex), null);
		}
		while (rightDiffIndex < rightDiffElems.size()) {
			compareElement(diffDoc, null, rightDiffElems.get(rightDiffIndex));
		}
		return diffDoc;
	}

	private void includeDiffClasses(Element head) {
		// TODO: allow users to define and provide custom classes
		head.append(String.format(
				"<style>.%s{border: dashed #228833; background: #defade}</style>",
				ADDED_CLASS));
		head.append(String.format(
				"<style>.%s{border: dashed grey; background: #f6f6f6}</style>",
				PREVIOUS_CHANGED_CLASS));
		head.append(String.format(
				"<style>.%s{border: dashed #C2952D; background: #f3ead5}</style>",
				CURRENT_CHANGED_CLASS));
		head.append(String.format(
				"<style>.%s{border: dashed #CC3311; background: #f5dede}</style>",
				DELETED_CLASS));
		head.append(String.format(
				"<style>.%s{color: grey}</style>",
				UNCHANGED_CLASS));
	}

	private void compareElement(Document diffDoc, Element currentLeft, Element currentRight) {
		if (currentLeft != null) {
			if (currentRight != null) {
				if (equalDiffElements(currentLeft, currentRight)) {
					if (currentLeft.hasSameValue(currentRight)) {
						// left and right elements are the same: omit them
						unchangedElement(diffDoc, currentLeft);
					}
					else {
						// some changes are present
						changedElement(diffDoc, currentLeft, currentRight);
					}
					leftDiffIndex++;
					rightDiffIndex++;
				}
				else {
					Element rightElem = findElement(currentLeft, rightDiffElems);
					if (rightElem != null) {
						// a matched right element exists, but one or more added
						//   elements appear first. treat them
						addedElement(diffDoc, currentRight);
						rightDiffIndex++;
					}
					else {
						// left element has been deleted
						deletedElement(diffDoc, currentLeft);
						leftDiffIndex++;
					}
				}
			}
			else {
				// left element has been deleted
				deletedElement(diffDoc, currentLeft);
				leftDiffIndex++;
			}
		}
		else {
			// invariant: currentRight cannot be null here
			if (currentRight == null) {
				throw new RuntimeException("Both current left and right cannot be null");
			}
			// currentRight is a new element
			addedElement(diffDoc, currentRight);
			rightDiffIndex++;
		}
	}

	@Override
	public void diff(ViewTree diffView, ViewTree left, ViewTree right) throws Exception {

		Document leftDoc = Jsoup.parse(left.getContent().getText(), "", Parser.xmlParser());
		Document rightDoc = Jsoup.parse(right.getContent().getText(), "", Parser.xmlParser());

		// TODO: convert to lazy promise
		Document diffDoc = compare(leftDoc, rightDoc);
		
		diffView.setFormat("html");
		diffView.setPromise(new StaticContentPromise(diffDoc.html()));
	}
}
