package org.eclipse.epsilon.picto.diff.engines.html;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TryJsoup {

	public static void main(String[] args) throws IOException {

		String htmlFile = new String(Files.readAllBytes(new File("files/risks.html").toPath()));

		Document doc = Jsoup.parse(htmlFile);

		// querying elements
		System.out.println("locate elements by class");
		Elements byClass = doc.select(".diffElement");
		System.out.println(byClass);

		System.out.println("\nand by id");
		Element byId = doc.select("#R3").first();
		System.out.println(byId);

		System.out.println("\nand by custom attribute (pictoId)");
		// it's not case sensitive (pictoid also works)
		Element byCustomAttr = doc.select("[pictoId]").first();
		System.out.println(byCustomAttr);

		// get elements contents (text, attributes, children)
		System.out.println("\nGet contents");
		System.out.println(byId.attr("id"));
		System.out.println("Tag: " + byId.tag());
		System.out.println("Own text: " + byId.ownText());
		System.out.println("Combined text (with children): \n" + byId.text());
		System.out.println("Outer html: \n" + byId.outerHtml());
		System.out.println("List of children: \n" + byId.children());


		// add css classes to these elements
		System.out.println("\nAdd/remove css class");
		byId.addClass("greenBorder");
		System.out.println(byId);
		byId.removeClass("greenBorder"); // toggleClass might also be useful

		// add wrappers (e.g. divs) around existing elements
		// This kind of wrapping works, but we should be careful about using
		//   div wrappers for the diffs, because divs are invalid in some 
		//   html places (such as in the middle of a table as in the example)
		System.out.println("\nWrap element with div");
		byId.wrap("<div class=\"greenBorder\"></div>");
		System.out.println(doc);

		// add new elements
		System.out.println("\nAdd new children to an element");
		Element parent = byId.parent();
		Element newChild = byId.clone();
		newChild.attr("id", byId.attr("id") + "clone");
		parent.appendChild(newChild);
		System.out.println(doc);

		// delete an element
		System.out.println("\nDelete an element from the doc");
		byId.remove();
		System.out.println(doc); // the general document gets updated

		// remaining: more complex examples with nested diffElements

	}

}
