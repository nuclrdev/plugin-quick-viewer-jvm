package dev.nuclr.plugin.core.quick.viewer.jvm;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;

import dev.nuclr.plugin.PluginTheme;
import dev.nuclr.plugin.QuickViewItem;
import dev.nuclr.plugin.QuickViewProvider;

/**
 * Quick-view provider for compiled Java {@code .class} files.
 *
 * <p>Delegates decompilation to Vineflower and displays the result with Java
 * syntax highlighting. Priority 1 ensures this provider is tried early; the
 * text provider is skipped because {@code "class"} is not in its extension set.
 */
public class ClassQuickViewProvider implements QuickViewProvider {

	private ClassQuickViewPanel panel;
	private PluginTheme theme;

	@Override
	public String getPluginClass() {
		return getClass().getName();
	}

	@Override
	public boolean matches(QuickViewItem item) {
		return "class".equalsIgnoreCase(item.extension());
	}

	@Override
	public JComponent getPanel() {
		if (panel == null) {
			panel = new ClassQuickViewPanel();
			panel.applyTheme(theme);
		}
		return panel;
	}

	@Override
	public void applyTheme(PluginTheme theme) {
		this.theme = theme;
		if (panel != null) {
			panel.applyTheme(theme);
		}
	}

	@Override
	public boolean open(QuickViewItem item, AtomicBoolean cancelled) {
		getPanel(); // ensure panel is initialised
		return panel.load(item, cancelled);
	}

	@Override
	public void close() {
		if (panel != null) {
			panel.clear();
		}
	}

	@Override
	public void unload() {
		close();
		panel = null;
	}

	@Override
	public int priority() {
		return 1;
	}
}
