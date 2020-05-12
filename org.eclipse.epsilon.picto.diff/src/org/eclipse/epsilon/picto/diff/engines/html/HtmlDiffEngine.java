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
import org.jsoup.select.Elements;

public class HtmlDiffEngine implements DiffEngine {

	// any html element to be diffed should declare this attribute
	private static final String DIFF_ID = "pictoId";
	private static final String DIFF_ELEMS_SELECTOR = String.format("[%s]", DIFF_ID);

	// html elements to compare and merge; traversed at the same time
	private Elements leftDiffElems;
	private Elements rightDiffElems;
	private int leftDiffIndex;
	private int rightDiffIndex;

	private Element currentLeft;
	private Element currentRight;

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

	//	private void compareElement(Element diffParentElem, Element leftElem) {
	//		if (leftElem.hasAttr(DIFF_ID)) {
	//			// if leftElem is a diff element, then merge it
	//			mergeElement(diffParentElem, leftElem);
	//		}
	//		else {
	//			// if not, append it to diffParentElement, continue with its children
	//			Element leftClone = leftElem.shallowClone();
	//			leftClone.appendTo(diffParentElem);
	//
	//			for (Element leftChild : leftElem.children()) {
	//				compareElement(leftClone, leftChild);
	//			}
	//		}
	//	}

	private void compareElement(Element diffParentElem) {
		if (currentLeft != null) {
			if (currentLeft.hasAttr(DIFF_ID)) {
				mergeElement(diffParentElem);
			}
			else {
				if (currentRight.hasAttr(DIFF_ID)) {
					// invariant: currentRight must be pointing to an added element,
					//   that shares the same parent of currentLeft.
					// even more, there could be several of them
					while (!shallowEquals(currentLeft, currentRight)) {
						addedElement(diffParentElem, currentRight);
						currentRight = currentRight.nextElementSibling();
					}
				}
				// invariant: both html elements are no diffElements, so they
				//   should match (without taking their children into account).

				// The only supported differences between left and right documents 
				//   are added, changed or deleted  diffElements, nothing else
				if (!shallowEquals(currentLeft, currentRight)) {
					throw new RuntimeException(
							"Detected html differences outside of diffElements");
				}

				// add element to the diff document 
				Element newDiffParent = shallowCopy(currentLeft);
				newDiffParent.appendTo(diffParentElem);

				Elements leftChildren = currentLeft.children();
				Elements rightChildren = currentRight.children();

				int leftIndex = 0;
				int rightIndex = 0;

				while (leftIndex < leftChildren.size() && rightIndex < rightChildren.size()) {
					currentLeft = leftChildren.get(leftIndex);
					currentRight = rightChildren.get(rightIndex);
					compareElement(newDiffParent);
					leftIndex++;
					rightIndex++;
				}
				// some or both indexes are out of bounds
				while (leftIndex < leftChildren.size()) {
					currentLeft = leftChildren.get(leftIndex);
					currentRight = null;
					compareElement(newDiffParent);
					leftIndex++;
				}
				while (rightIndex < rightChildren.size()) {
					currentLeft = null;
					currentRight = rightChildren.get(rightIndex);
					compareElement(newDiffParent);
					rightIndex++;
				}
			}
		}
		else {
			// the only possibility are any remaining added elements in the right
			while (currentRight != null) {
				// must be an added element too
				addedElement(diffParentElem, currentRight);
				currentRight = currentRight.nextElementSibling();
			}
		}
	}

	private void mergeElement(Element diffParentElem) {

		boolean deleted = false;

		if (currentRight != null) {
			if (currentRight.hasAttr(DIFF_ID)) {
				if (!equalDiffElements(currentLeft, currentRight)) {
					// what could be happening is that some added diffElement
					//   appears before the matched diffElement
					if (findElement(currentLeft, rightDiffElems) != null) {
						while (!shallowEquals(currentLeft, currentRight)) {
							addedElement(diffParentElem, currentRight);
							currentRight = currentRight.nextElementSibling();
						}
						// after the loop,
						// the right is a matching diffElement of the left
						if (currentLeft.hasSameValue(currentRight)) {
							// left and right elements are the same: omit them
						}
						else {
							// some changes are present
							changedElement(diffParentElem, currentLeft, currentRight);
						}
						currentRight = currentRight.nextElementSibling();
					}
					else {
						deleted = true;
					}
				}
				else {
					// the right is a matching diffElement of the left
					if (currentLeft.hasSameValue(currentRight)) {
						// left and right elements are the same: omit them
					}
					else {
						// some changes are present
						changedElement(diffParentElem, currentLeft, currentRight);
					}
					currentRight = currentRight.nextElementSibling();
				}
			}
			else {
				// reached a non-diff html element on the right
				//   eventually, the same element must be reached in the left
				//   (several deleted elements may be remaining)
				deleted = true;
			}
		}
		else {
			// end of html nested elements in the right document
			//   (several deleted elements may be remaining)
			deleted = true;
		}
		if (deleted) {
			deletedElement(diffParentElem, currentLeft);
		}
		currentLeft = currentLeft.nextElementSibling();
	}

	//	private void mergeElement(Element diffParentElem, Element leftElem) {
	//		// invariant:
	//		// leftDiffElems.get(currentLeft) always equals leftElem at this point
	//		if (!leftDiffElems.get(leftDiffIndex).equals(leftElem)) {
	//			throw new RuntimeException("Current left elements must match");
	//		}
	//		Element rightElem = findElement(leftElem, rightDiffIndex, rightDiffElems);
	//		if (rightElem == null) {
	//			// leftElem has been deleted
	//			deletedElement(diffParentElem, leftElem);
	//		}
	//		else {
	//			// rightElem exists, but some added nodes might appear first
	//			// TODO: what happens if there are other html elements in the middle?
	//			//       I mean, not diffElements.
	//			//       Currently: not allowed.
	//			processAddedElements(diffParentElem, rightElem);
	//			// now check if left
	//			if (leftElem.hasSameValue(rightElem)) {
	//				// left and right elements are the same: omit them
	//			}
	//			else {
	//				// some changes are present
	//				changedElement(diffParentElem, leftElem, rightElem);
	//			}
	//			rightDiffIndex++;
	//		}
	//		leftDiffIndex++; // TODO: probably left indexes and elements are not needed
	//	}

	private void processAddedElements(Element diffParentElem, Element rightElem) {
		// invariant: rightElem appears in rightDiffElems (at some point) 
		while (!equalDiffElements(rightElem, rightDiffElems.get(rightDiffIndex))) {
			Element addedElem = rightDiffElems.get(rightDiffIndex);
			addedElement(diffParentElem, addedElem);
			rightDiffIndex++;
		}
	}

	private void addedElement(Element diffParentElem, Element addedElem) {
		// add the whole element as new
		// TODO: update this method if support for nested diffelements is needed
		Element addedCopy = addedElem.clone();
		addedCopy.addClass("added");
		addedCopy.appendTo(diffParentElem);
	}

	private void addedElement(Document diffDoc, Element addedElem) {
		// add the whole element as new
		// TODO: update this method if support for nested diffelements is needed
		Element addedCopy = addedElem.clone();
		addedCopy.addClass("added");
		
		appendElement(diffDoc, addedElem.parents(), addedCopy);
	}

	private void changedElement(Document diffDoc, Element leftElem, Element rightElem) {
		// add the previous element as previous, and the new as new
		// TODO: update this method if support for nested diffelements is needed
		Element leftCopy = leftElem.clone();
		leftCopy.addClass("previousModified");
		appendElement(diffDoc, leftElem.parents(), leftCopy);

		Element rightCopy = rightElem.clone();
		rightCopy.addClass("currentModified");
		appendElement(diffDoc, rightElem.parents(), rightCopy);
	}

	private void deletedElement(Document diffDoc, Element deletedElem) {
		// add the whole element as deleted.
		// TODO: update this method if support for nested diffelements is needed
		Element deletedCopy = deletedElem.clone();
		deletedCopy.addClass("deleted");

		appendElement(diffDoc, deletedElem.parents(), deletedCopy);
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

	private void changedElement(Element diffParentElem, Element leftElem, Element rightElem) {
		// add the previous element as previous, and the new as new
		// TODO: update this method if support for nested diffelements is needed
		Element leftCopy = leftElem.clone();
		leftCopy.addClass("previousModified");
		leftCopy.appendTo(diffParentElem);

		Element rightCopy = rightElem.clone();
		rightCopy.addClass("currentModified");
		rightCopy.appendTo(diffParentElem);
	}

	private void deletedElement(Element diffParentElem, Element deletedElem) {
		// add the whole element as deleted.
		// TODO: update this method if support for nested diffelements is needed
		Element deletedCopy = deletedElem.clone();
		deletedCopy.addClass("deleted");
		deletedCopy.appendTo(diffParentElem);
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
	 * Checks both path to the root and content (except children)
	 */
	private boolean equalContentAndParents(Element thisElem, Element thatElem) {
		return shallowEquals(thisElem, thatElem) && sameParents(thisElem, thatElem);
	}

	/**
	 * Both elements share the same DIFF_ID
	 */
	private boolean equalDiffElements(Element thisElem, Element thatElem) {
		return thisElem.attr(DIFF_ID).equals(thatElem.attr(DIFF_ID))
				&& sameParents(thisElem, thatElem);
	}

	private Document comparev3(Document leftDoc, Document rightDoc) {

		leftDiffElems = leftDoc.select(DIFF_ELEMS_SELECTOR);
		rightDiffElems = rightDoc.select(DIFF_ELEMS_SELECTOR);

		leftDiffIndex = 0;
		rightDiffIndex = 0;

		Document diffDoc = leftDoc.clone();
		// remove previous diffElements from diffDoc (get "empty" html skeleton)
		diffDoc.select(DIFF_ELEMS_SELECTOR).remove();

		while (leftDiffIndex < leftDiffElems.size() && rightDiffIndex < rightDiffElems.size()) {
			compareElementv3(diffDoc, leftDiffElems.get(leftDiffIndex), rightDiffElems.get(rightDiffIndex));
		}
		while (leftDiffIndex < leftDiffElems.size()) {
			compareElementv3(diffDoc, leftDiffElems.get(leftDiffIndex), null);
		}
		while (rightDiffIndex < rightDiffElems.size()) {
			compareElementv3(diffDoc, null, rightDiffElems.get(rightDiffIndex));
		}
		return diffDoc;
	}

	private void compareElementv3(Document diffDoc, Element currentLeft, Element currentRight) {
		if (currentLeft != null) {
			if (currentRight != null) {
				if (equalDiffElements(currentLeft, currentRight)) {
					if (currentLeft.hasSameValue(currentRight)) {
						// left and right elements are the same: omit them
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

	private Document compare(Document leftDoc, Document rightDoc) {

		Document diffDoc = Document.createShell("");
		// copy head elements
		for (Node child : leftDoc.head().childNodesCopy()) {
			diffDoc.head().appendChild(child);
		}

		leftDiffElems = leftDoc.select(DIFF_ELEMS_SELECTOR);
		rightDiffElems = rightDoc.select(DIFF_ELEMS_SELECTOR);

		leftDiffIndex = 0;
		rightDiffIndex = 0;

		Elements leftChildren = leftDoc.body().children();
		Elements rightChildren = rightDoc.body().children();

		int leftIndex = 0;
		int rightIndex = 0;

		while (leftIndex < leftChildren.size() && rightIndex < rightChildren.size()) {
			currentLeft = leftChildren.get(leftIndex);
			currentRight = rightChildren.get(rightIndex);
			compareElement(diffDoc.body());
			leftIndex++;
			rightIndex++;
		}
		// some or both indexes are out of bounds
		while (leftIndex < leftChildren.size()) {
			currentLeft = leftChildren.get(leftIndex);
			currentRight = null;
			compareElement(diffDoc.body());
			leftIndex++;
		}
		while (rightIndex < rightChildren.size()) {
			currentLeft = null;
			currentRight = rightChildren.get(rightIndex);
			compareElement(diffDoc.body());
			rightIndex++;
		}

		return diffDoc;
	}

	@Override
	public void diff(ViewTree diffView, ViewTree left, ViewTree right) throws Exception {

		Document leftDoc = Jsoup.parse(left.getPromise().getContent());
		Document rightDoc = Jsoup.parse(right.getPromise().getContent());

		// TODO: convert to lazy promise
		Document diffDoc = comparev3(leftDoc, rightDoc);
		
		diffView.setFormat("html");
		diffView.setPromise(new StaticContentPromise(diffDoc.toString()));
	}
}
