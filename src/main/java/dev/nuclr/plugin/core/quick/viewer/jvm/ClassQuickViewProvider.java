package dev.nuclr.plugin.core.quick.viewer.jvm;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrMenuResource;
import dev.nuclr.platform.plugin.NuclrPlugin;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrResourcePath;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClassQuickViewProvider implements NuclrPlugin {

	private static final String THEME_UPDATED_EVENT_TYPE = "dev.nuclr.platform.theme.updated";

	private NuclrPluginContext context;
	private ClassQuickViewPanel panel;
	private volatile AtomicBoolean currentCancelled;

	@Override
	public JComponent panel() {
		if (panel == null) {
			panel = new ClassQuickViewPanel();
			panel.applyTheme(context.getTheme());
		}
		return panel;
	}

	@Override
	public List<NuclrMenuResource> menuItems(NuclrResourcePath source) {
		return List.of();
	}

	@Override
	public void load(NuclrPluginContext context) {
		this.context = context;
		applyTheme(context.getTheme());
	}

	@Override
	public void unload() {
		closeResource();
		panel = null;
		context = null;
	}

	@Override
	public boolean supports(NuclrResourcePath resource) {
		return resource != null && "class".equalsIgnoreCase(resource.getExtension());
	}

	@Override
	public int priority() {
		return 1;
	}

	@Override
	public boolean openResource(NuclrResourcePath resource, AtomicBoolean cancelled) {
		if (currentCancelled != null) {
			currentCancelled.set(true);
		}
		currentCancelled = cancelled;
		panel();
		return panel.load(resource, cancelled);
	}

	@Override
	public void closeResource() {
		if (currentCancelled != null) {
			currentCancelled.set(true);
			currentCancelled = null;
		}
		if (panel != null) {
			panel.clear();
		}
	}

	public void applyTheme(NuclrThemeScheme theme) {
		if (panel != null) {
			panel.applyTheme(theme);
		}
	}

	@Override
	public boolean onFocusGained() {
		return false;
	}

	@Override
	public void onFocusLost() {
	}

	@Override
	public boolean isFocused() {
		return false;
	}

	private String name = "JVM Class Quick Viewer";
	private String id = "dev.nuclr.plugin.core.quickviewer.jvm";
	private String version = "1.0.0";
	private String description = "Decompiles and previews Java .class files using Vineflower.";
	private String author = "Nuclr Development Team";
	private String license = "Apache-2.0";
	private String website = "https://nuclr.dev";
	private String pageUrl = "https://nuclr.dev/plugins/core/jvm-quick-viewer.html";
	private String docUrl = "https://nuclr.dev/plugins/core/jvm-quick-viewer.html";

	@Override
	public String id() {
		return id;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String version() {
		return version;
	}

	@Override
	public String description() {
		return description;
	}

	@Override
	public String author() {
		return author;
	}

	@Override
	public String license() {
		return license;
	}

	@Override
	public String website() {
		return website;
	}

	@Override
	public String pageUrl() {
		return pageUrl;
	}

	@Override
	public String docUrl() {
		return docUrl;
	}

	@Override
	public Developer type() {
		return Developer.Official;
	}

	@Override
	public void updateTheme(NuclrThemeScheme themeScheme) {

	}

}
