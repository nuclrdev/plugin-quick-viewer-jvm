package dev.nuclr.plugin.core.quick.viewer.jvm;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Manifest;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClassQuickViewPanel extends JPanel {

	private final RSyntaxTextArea textArea;
	private final RTextScrollPane scroll;

	public ClassQuickViewPanel() {
		super(new BorderLayout());

		textArea = new RSyntaxTextArea();

		loadSyntaxTheme(true);

		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
		textArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
		textArea.setCodeFoldingEnabled(true);
		textArea.setAntiAliasingEnabled(true);
		textArea.setTabSize(4);
		textArea.setTabsEmulated(false);
		textArea.setEditable(false);

		scroll = new RTextScrollPane(textArea);
		scroll.setLineNumbersEnabled(true);
		SwingUtilities.updateComponentTreeUI(scroll);

		add(scroll, BorderLayout.CENTER);
	}

	public void applyTheme(NuclrThemeScheme theme) {
		// Colors come from the active look-and-feel (UIManager): the host installs
		// the selected theme as the L&F and only overlays a scheme's *explicit*
		// overrides on top. Built-in themes carry no color overrides, so reading
		// solely from the scheme would yield stale defaults (e.g. black line
		// numbers, a selection indistinguishable from the panel background).
		Color background = uiColor(theme, "Panel.background", getBackground());
		Color foreground = uiColor(theme, "Panel.foreground", textArea.getForeground());

		// Load the bundled RSyntax palette whose token colors match the host
		// background luminance, so highlighting stays readable on light themes.
		// The overrides below then nudge it to the exact host colors.
		loadSyntaxTheme(isDark(background));

		textArea.setFont(editorFont(UIManager.getFont("defaultFont")));
		scroll.getGutter().setLineNumberFont(textArea.getFont());

		Color gutterBackground = uiColor(theme, "TableHeader.background", background);
		Color gutterForeground = uiColor(theme, "Label.foreground", foreground);
		// Use the host's real text-selection color so the selection is clearly
		// distinct from the panel background and tracks light/dark themes.
		Color selectionBackground = uiColor(theme, "TextArea.selectionBackground",
				uiColor(theme, "Table.selectionBackground", textArea.getSelectionColor()));

		setBackground(background);
		scroll.setBackground(background);
		scroll.getViewport().setBackground(background);
		scroll.getGutter().setBackground(gutterBackground);
		scroll.getGutter().setLineNumberColor(gutterForeground);

		textArea.setBackground(background);
		textArea.setForeground(foreground);
		textArea.setCaretColor(foreground);
		textArea.setSelectionColor(selectionBackground);
		textArea.setSelectedTextColor(foreground);
		textArea.setCurrentLineHighlightColor(blend(background, uiColor(theme, "Table.gridColor", gutterBackground), 0.35f));
	}

	/**
	 * Decompiles {@code item} and loads the result into the text area.
	 * Called from a background thread; all Swing updates go to the EDT.
	 *
	 * @return {@code true} if this provider handled the item (success or error
	 *         message), {@code false} if the item should not be shown here
	 */
	public boolean load(NuclrResource item, AtomicBoolean cancelled) {
		if (cancelled.get()) return false;

		String content;
		try {
			content = decompile(item);
		} catch (Exception e) {
			log.error("Failed to decompile: {}", item.getName(), e);
			showMessage("// Decompilation failed: " + e.getMessage(), cancelled);
			return true;
		}

		if (cancelled.get()) return false;

		if (content == null || content.isBlank()) {
			showMessage("// Decompiler produced no output.", cancelled);
			return true;
		}

		final String text = content;
		SwingUtilities.invokeLater(() -> {
			if (!cancelled.get()) setText(text);
		});
		return true;
	}

	public void clear() {
		SwingUtilities.invokeLater(() ->
				textArea.setDocument(new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_NONE)));
	}

	// ── Internals ─────────────────────────────────────────────────────────────

	private void loadSyntaxTheme(boolean dark) {
		String resource = dark
				? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
				: "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
		try (InputStream themeIn = getClass().getResourceAsStream(resource)) {
			if (themeIn != null) {
				Theme.load(themeIn).apply(textArea);
			}
		} catch (IOException e) {
			log.warn("Could not load RSyntaxTextArea theme: {}", resource, e);
		}
	}

	/**
	 * Resolve a UI color, preferring the theme scheme's explicit override and
	 * otherwise falling back to the active look-and-feel ({@link UIManager}). A
	 * {@code fallback} guards keys the L&amp;F does not define.
	 */
	private static Color uiColor(NuclrThemeScheme theme, String key, Color fallback) {
		Color lafColor = UIManager.getColor(key);
		Color base = lafColor != null ? lafColor : fallback;
		return theme != null ? theme.color(key, base) : base;
	}

	/** Perceived-luminance test (ITU-R BT.601); below mid-grey counts as a dark background. */
	private static boolean isDark(Color c) {
		double luminance = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
		return luminance < 128;
	}

	private static Font editorFont(Font baseFont) {
		return baseFont != null ? baseFont : new Font(Font.MONOSPACED, Font.PLAIN, 13);
	}

	private static Color blend(Color base, Color overlay, float overlayWeight) {
		float clamped = Math.max(0f, Math.min(1f, overlayWeight));
		float baseWeight = 1f - clamped;
		return new Color(
				Math.round(base.getRed() * baseWeight + overlay.getRed() * clamped),
				Math.round(base.getGreen() * baseWeight + overlay.getGreen() * clamped),
				Math.round(base.getBlue() * baseWeight + overlay.getBlue() * clamped));
	}

	private String decompile(NuclrResource item) throws Exception {
		Path tempDir = Files.createTempDirectory("nuclr-jvm-");
		try {
			byte[] classBytes;
			Path sourcePath = item.getPath();
			if (sourcePath != null) {
				classBytes = Files.readAllBytes(sourcePath);
			} else {
				try (var in = Files.newInputStream(item.getPath())) {
					classBytes = in.readAllBytes();
				}
			}

			Path tempClass = tempDir.resolve(item.getName());
			Files.write(tempClass, classBytes);

			var saver = new StringResultSaver();
			var decompiler = new BaseDecompiler(
					(externalPath, internalPath) -> Files.readAllBytes(Path.of(externalPath)),
					saver,
					Map.of(),
					new SilentLogger());
			decompiler.addSource(tempClass.toFile());
			decompiler.decompileContext();

			return saver.getResult();
		} finally {
			try (var stream = Files.walk(tempDir)) {
				stream.sorted(Comparator.reverseOrder())
						.map(Path::toFile)
						.forEach(java.io.File::delete);
			} catch (IOException ignored) {}
		}
	}

	private void showMessage(String message, AtomicBoolean cancelled) {
		SwingUtilities.invokeLater(() -> {
			if (!cancelled.get()) setText(message);
		});
	}

	private void setText(String text) {
		RSyntaxDocument doc = new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_JAVA);
		try {
			doc.insertString(0, text, null);
		} catch (BadLocationException e) {
			log.error("Failed to build document", e);
			return;
		}
		textArea.setDocument(doc);
		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
		textArea.setCaretPosition(0);
		textArea.discardAllEdits();
	}

	// ── Vineflower adapters ────────────────────────────────────────────────────

	/** Captures the decompiled source of the first class file saved. */
	private static final class StringResultSaver implements IResultSaver {

		private String result;

		String getResult() { return result; }

		@Override public void saveFolder(String path) {}
		@Override public void copyFile(String source, String path, String entryName) {}

		@Override
		public void saveClassFile(String path, String qualifiedName, String entryName,
				String content, int[] mapping) {
			if (result == null) result = content;
		}

		@Override public void createArchive(String path, String archiveName, Manifest manifest) {}
		@Override public void saveDirEntry(String path, String archiveName, String entryName) {}
		@Override public void copyEntry(String source, String path, String archiveName, String entry) {}
		@Override public void saveClassEntry(String path, String archiveName, String qualifiedName,
				String entryName, String content) {}
		@Override public void closeArchive(String path, String archiveName) {}
	}

	/** Suppresses all Vineflower log output. */
	private static final class SilentLogger extends IFernflowerLogger {
		@Override
		public void writeMessage(String message, Severity severity) {}
		@Override
		public void writeMessage(String message, Severity severity, Throwable t) {}
	}
}
