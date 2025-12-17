package haven;

import java.awt.*;
import java.util.*;

public class BarrelContentsGobInfo extends GobInfo {

	private static final Map<String, Tex> contentTexCache = new HashMap<>();

    protected BarrelContentsGobInfo(Gob owner) {
	super(owner);
	center = new Pair<>(0.5, 1.0);
    }
    
    @Override
    protected boolean enabled() {
		return OptWnd.showBarrelContentsTextCheckBox.a;
    }

    @Override
    protected Tex render() {
	if(gob == null || gob.getres() == null) {return null;}
	up(6);
	return content();
    }

    private Tex content() {
	String resName = gob.getres().name;
	if(resName == null) {return null;}
	Optional<String> contents = Optional.empty();
	
	if(resName.startsWith("gfx/terobjs/barrel")) {
	    contents = gob.ols.stream()
		.map(Gob.Overlay::getSprResName)
		.filter(name -> name.startsWith("gfx/terobjs/barrel-"))
		.map(name -> name.substring(name.lastIndexOf("-") + 1))
		.findAny();
	    
	}
	
	if(contents.isPresent()) {
	    String text = contents.get();
		if (!text.isEmpty()) {
			text = text.substring(0, 1).toUpperCase() + text.substring(1);
		}
		text = addSpaceAndCapitalize(text);

		try {
			if (!contentTexCache.containsKey(text)) {
				Tex contentTex = new TexI(ItemInfo.catimgsh(3, 0, null, PUtils.strokeImg(Text.std.renderstroked(text, Color.white, Color.black).img)));
				contentTexCache.put(text, contentTex);
				return contentTex;
			} else {
				return contentTexCache.get(text);
			}
		} catch (NullPointerException ignored) {
			return null;
		}
	}
	return null;
    }

    @Override
    public String toString() {
	Resource res = gob.getres();
	return String.format("GobInfo<%s>", res != null ? res.name : "<loading>");
    }


	public static String addSpaceAndCapitalize(String str) { // ND: I ChatGPT'd the crap out of this method
		String[] stringStartTarget = {"Color", "Cave"};
		String[] stringEndTargets = {"flour", "fluid", "dust", "oil"}; // Add other end targets as needed

		// Check for start targets
		for (String target : stringStartTarget) {
			if (str.contains(target)) {
				// Find the index after the target
				int index = str.indexOf(target) + target.length();
				if (index < str.length()) {
					// Create the new string with a space
					str = str.substring(0, index) + " " + str.substring(index);
					// Capitalize the first letter after the space
					str = str.substring(0, index + 1) +
							str.substring(index + 1, index + 2).toUpperCase() +
							str.substring(index + 2);
				}
				return str;
			}
		}

		// Check for end targets
		for (String endTarget : stringEndTargets) {
			int index = str.toLowerCase().indexOf(endTarget);
			if (index != -1) { // Check if the end target is found
				// Create the new string with a space before the end target
				str = str.substring(0, index) + " " + str.substring(index);
				// Capitalize the first letter of the end target
				if (index + 1 < str.length()) { // Ensure we are within bounds
					return str.substring(0, index + 1) +
							str.substring(index + 1, index + 2).toUpperCase() +
							str.substring(index + 2);
				} else {
					return str; // If it's out of bounds, just return the modified string
				}
			}
		}

		return str; // Return the original string if no targets are found
	}



}