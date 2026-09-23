package dev.nuclr.plugin.core.quick.viewer.jvm;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;

import org.apache.commons.io.FilenameUtils;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrResource;
import dev.nuclr.platform.plugin.QuickViewNuclrPlugin;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClassQuickViewPlugin implements QuickViewNuclrPlugin {

	private NuclrPluginContext context;
	private ClassQuickViewPanel panel;
	private volatile AtomicBoolean currentCancelled;
	private NuclrResource currentResource;

	@Override
	public JComponent panel() {
		if (panel == null) {
			panel = new ClassQuickViewPanel();
			panel.applyTheme(context.getTheme());
		}
		return panel;
	}

	@Override
	public void preinit(NuclrPluginContext context) {
		this.context = context;
		applyTheme(context.getTheme());
	}

	@Override
	public void init() {
	}

	@Override
	public NuclrPluginContext getContext() {
		return this.context;
	}

	@Override
	public void unload() {
		closeResource();
		panel = null;
		context = null;
	}

	@Override
	public boolean supports(NuclrResource resource) {
		var path = resource.getName();
		return path != null && "class".equalsIgnoreCase(extension(resource));
	}

	private static String extension(Path path) {
		var name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
		return FilenameUtils.getExtension(name);
	}

	private static String extension(NuclrResource resource) {
		if (resource == null || resource.getName() == null) {
			return null;
		}
		String name = resource.getName();
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return null;
		}
		return name.substring(dot + 1);
	}
	

	@Override
	public boolean openResource(NuclrResource resource, AtomicBoolean cancelled) {
		if (currentCancelled != null) {
			currentCancelled.set(true);
		}
		this.currentResource = resource;
		currentCancelled = cancelled;
		panel();
		return panel.load(resource, cancelled);
	}

	@Override
	public boolean supportsThumbnails() {
		return true;
	}

	/** The decompiled class as a page of source, starting at its declaration. */
	@Override
	public BufferedImage thumbnail(NuclrResource resource, int maxWidth, int maxHeight, AtomicBoolean cancelled) {
		if (maxWidth <= 0 || maxHeight <= 0 || resource == null || !supports(resource)) {
			return null;
		}
		try {
			String source = ClassQuickViewPanel.decompile(resource);
			if (source == null || source.isBlank() || (cancelled != null && cancelled.get())) {
				return null;
			}
			// The banner comment, package and imports are the same on every class; the
			// declaration is what tells one thumbnail from the next.
			List<PageThumbnail.Line> lines = source.lines()
					.dropWhile(ClassQuickViewPlugin::isPreamble)
					.limit(150)
					.map(PageThumbnail.Line::mono)
					.toList();
			return PageThumbnail.render(lines, maxWidth, maxHeight, cancelled);
		} catch (Exception e) {
			log.debug("No thumbnail for {}: {}", resource.getName(), e.toString());
			return null;
		}
	}

	private static boolean isPreamble(String line) {
		String trimmed = line.strip();
		return trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
				|| trimmed.startsWith("package ") || trimmed.startsWith("import ");
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

	private String id = "dev.nuclr.plugin.core.quickviewer.jvm";


	@Override
	public void updateTheme(NuclrThemeScheme themeScheme) {
		applyTheme(themeScheme);
	}

	@Override
	public NuclrResource getCurrentResource() {
		return currentResource;
	}

	@Override
	public String uuid() {
		return id;
	}


}
