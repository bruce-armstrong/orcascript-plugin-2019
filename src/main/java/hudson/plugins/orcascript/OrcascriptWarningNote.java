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
 * Annotation for OrcaScript warning messages
 */
@SuppressWarnings({ "rawtypes" })
public class OrcascriptWarningNote extends ConsoleNote {

	private static final long serialVersionUID = -2026411510609343088L;
	
	/** Pattern to identify warning messages */
    public final static Pattern PATTERN = Pattern.compile("(.*)\\(\\d+(,\\d+){0,1}\\):\\s[Ww]arning\\s(([A-Z]*)\\d+){0,1}:\\s(.*)");
    
    public OrcascriptWarningNote() {
    }

	@Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(0, text.length(), "<span class=warning-inline>", "</span>");
        return null;
    }

    @Extension @Symbol("orcascriptWarning")
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.Orcascript_WarningNoteDescription();
        }
    }
}
