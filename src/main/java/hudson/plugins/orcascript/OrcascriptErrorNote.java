package hudson.plugins.orcascript;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import hudson.plugins.orcascript.Messages;

import org.jenkinsci.Symbol;

import java.util.regex.Pattern;

/**
 * Annotation for OrcaScript error messages
 */
@SuppressWarnings("rawtypes")
public class OrcascriptErrorNote extends ConsoleNote {
    /**
	 * 
	 */
	private static final long serialVersionUID = 7696829546694298723L;
	/** Pattern to identify error messages */
    public final static Pattern PATTERN = Pattern.compile("(.*)[Ee]rror\\s(([A-Z]*)\\d+){0,1}:\\s(.*)");
    
    public OrcascriptErrorNote() {
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(0, text.length(), "<span class=error-inline>", "</span>");
        return null;
    }

    @Extension @Symbol("orcascriptError")
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.Orcascript_ErrorNoteDescription();
        }
    }
}
