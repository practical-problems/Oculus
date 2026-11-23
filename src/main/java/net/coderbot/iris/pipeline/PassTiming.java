package net.coderbot.iris.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.coderbot.iris.Iris;
import net.coderbot.iris.config.ExperimentalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

import java.util.ArrayList;
import java.util.List;

/**
 * CPU and GPU time per render pass, in milliseconds. Idea from Emberlens's PassTimers (GPU only, logged every 2000
 * frames); this version also measures CPU time and uses GL timestamp queries, which may nest (DH LODs inside the
 * gbuffers passes). Results are read {@link #RING} frames later and only if available, so the CPU never waits for the GPU.
 * Does nothing unless the timing overlay or the timing log is on.
 */
public final class PassTiming {
	public enum Pass {
		WORLD("world total"),
		SHADOW("shadow"),
		DH_SHADOW("  dh_shadow"),
		OPAQUE("gbuffers opaque"),
		DH_LODS("  dh lods"),
		DEFERRED("deferred"),
		TRANSLUCENT("gbuffers transl."),
		COMPOSITE("composite"),
		FINAL("final");

		final String label;

		Pass(String label) {
			this.label = label;
		}
	}

	private static final int RING = 4;
	private static final int MAX_PER_FRAME = 8;
	private static final long OVERLAY_WINDOW_NS = 1_000_000_000L;

	private static final Timer[] TIMERS = new Timer[Pass.values().length];
	private static int frame;
	private static boolean inFrame;
	private static boolean gpuDisabled;
	private static boolean gpuChecked;

	private static long overlayWindowStart;
	/** Rows of {label, cpu ms, gpu ms}; row 0 is the header. */
	private static List<String[]> overlayRows = new ArrayList<>();
	private static long logWindowStart;

	static {
		for (int i = 0; i < TIMERS.length; i++) {
			TIMERS[i] = new Timer();
		}
	}

	private PassTiming() {
	}

	private static final class Timer {
		int[][] startQueries;
		int[][] endQueries;
		final int[] used = new int[RING];
		final long[] cpuNs = new long[RING];
		long cpuStart;
		boolean open;
		int openSlot;
		// Accumulators: overlay window and log window
		long overlayCpu, overlayGpu;
		int overlayCpuCount, overlayGpuCount;
		long logCpu, logGpu;
		int logCpuCount, logGpuCount;
		// Last overlay averages, in ns
		double shownCpu = -1, shownGpu = -1;

		void ensureQueries() {
			if (startQueries == null) {
				startQueries = new int[RING][MAX_PER_FRAME];
				endQueries = new int[RING][MAX_PER_FRAME];
				for (int i = 0; i < RING; i++) {
					GL15C.glGenQueries(startQueries[i]);
					GL15C.glGenQueries(endQueries[i]);
				}
			}
		}
	}

	/** Start of a shader-pipeline world frame. */
	public static void frameStart() {
		if (!ExperimentalConfig.timingActive()) {
			inFrame = false;
			return;
		}

		frame++;
		int slot = frame % RING;

		// Collect the frame that used this slot RING frames ago, then free the slot.
		for (Timer timer : TIMERS) {
			int count = timer.used[slot];
			if (count > 0 || timer.cpuNs[slot] > 0) {
				timer.overlayCpu += timer.cpuNs[slot];
				timer.overlayCpuCount++;
				timer.logCpu += timer.cpuNs[slot];
				timer.logCpuCount++;
			}
			if (count > 0 && timer.startQueries != null && !gpuDisabled) {
				long gpu = 0;
				boolean available = true;
				for (int i = 0; i < count; i++) {
					if (GL15C.glGetQueryObjecti(timer.endQueries[slot][i], GL15C.GL_QUERY_RESULT_AVAILABLE) == 0) {
						available = false;
						break;
					}
					long start = GL33C.glGetQueryObjecti64(timer.startQueries[slot][i], GL15C.GL_QUERY_RESULT);
					long end = GL33C.glGetQueryObjecti64(timer.endQueries[slot][i], GL15C.GL_QUERY_RESULT);
					gpu += Math.max(0, end - start);
				}
				if (available) {
					timer.overlayGpu += gpu;
					timer.overlayGpuCount++;
					timer.logGpu += gpu;
					timer.logGpuCount++;
				}
			}
			timer.used[slot] = 0;
			timer.cpuNs[slot] = 0;
			timer.open = false;
		}

		inFrame = true;
		begin(Pass.WORLD);
	}

	/** End of a shader-pipeline world frame. */
	public static void frameEnd() {
		if (!inFrame) {
			return;
		}
		end(Pass.WORLD);
		inFrame = false;

		long now = System.nanoTime();
		if (now - overlayWindowStart >= OVERLAY_WINDOW_NS) {
			overlayWindowStart = now;
			updateOverlay();
		}

		int logSeconds = ExperimentalConfig.timingLogSeconds;
		if (logSeconds > 0) {
			if (logWindowStart == 0) {
				logWindowStart = now;
			} else if (now - logWindowStart >= logSeconds * 1_000_000_000L) {
				logWindowStart = now;
				writeLog(logSeconds);
			}
		} else {
			logWindowStart = 0;
		}
	}

	public static void begin(Pass pass) {
		if (!inFrame) {
			return;
		}
		Timer timer = TIMERS[pass.ordinal()];
		if (timer.open) {
			return;
		}
		int slot = frame % RING;
		timer.open = true;
		timer.openSlot = slot;
		timer.cpuStart = System.nanoTime();
		int i = timer.used[slot];
		if (i < MAX_PER_FRAME && gpuAvailable()) {
			try {
				timer.ensureQueries();
				GL33C.glQueryCounter(timer.startQueries[slot][i], GL33C.GL_TIMESTAMP);
			} catch (RuntimeException e) {
				disableAfterError(e);
			}
		}
	}

	public static void end(Pass pass) {
		Timer timer = TIMERS[pass.ordinal()];
		if (!timer.open) {
			return;
		}
		timer.open = false;
		int slot = timer.openSlot;
		timer.cpuNs[slot] += System.nanoTime() - timer.cpuStart;
		int i = timer.used[slot];
		if (i < MAX_PER_FRAME && timer.startQueries != null && !gpuDisabled) {
			GL33C.glQueryCounter(timer.endQueries[slot][i], GL33C.GL_TIMESTAMP);
			timer.used[slot] = i + 1;
		}
	}

	private static boolean gpuAvailable() {
		if (!gpuChecked) {
			gpuChecked = true;
			org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
			if (!(caps.OpenGL33 || caps.GL_ARB_timer_query)) {
				gpuDisabled = true;
				Iris.logger.warn("Pass timing: GPU timer queries are not supported; only CPU times will be shown");
			}
		}
		return !gpuDisabled;
	}

	private static void disableAfterError(RuntimeException e) {
		if (!gpuDisabled) {
			gpuDisabled = true;
			// Drop anything recorded so far, since some start queries may never have been issued.
			for (Timer timer : TIMERS) {
				java.util.Arrays.fill(timer.used, 0);
			}
			Iris.logger.warn("Pass timing: GPU timer queries failed; only CPU times will be shown", e);
		}
	}

	private static String ms(long totalNs, int count) {
		return count == 0 ? "-" : String.format("%.2f", totalNs / (double) count / 1_000_000.0);
	}

	private static void updateOverlay() {
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] {Iris.MODNAME + " timing (avg ms)", "CPU", "GPU"});
		boolean any = false;
		for (Pass pass : Pass.values()) {
			Timer t = TIMERS[pass.ordinal()];
			if (t.overlayCpuCount == 0 && t.overlayGpuCount == 0) {
				continue;
			}
			any = true;
			rows.add(new String[] {pass.label, ms(t.overlayCpu, t.overlayCpuCount), gpuDisabled ? "n/a" : ms(t.overlayGpu, t.overlayGpuCount)});
			t.overlayCpu = t.overlayGpu = 0;
			t.overlayCpuCount = t.overlayGpuCount = 0;
		}
		if (!any) {
			rows.add(new String[] {"no shader pack frames", "", ""});
		}
		overlayRows = rows;
	}

	private static void writeLog(int seconds) {
		StringBuilder line = new StringBuilder("Pass timing, last ").append(seconds).append(" s (avg ms CPU/GPU):");
		boolean any = false;
		for (Pass pass : Pass.values()) {
			Timer t = TIMERS[pass.ordinal()];
			if (t.logCpuCount == 0 && t.logGpuCount == 0) {
				continue;
			}
			any = true;
			line.append(' ').append(pass.label.trim().replace(' ', '_')).append('=')
				.append(ms(t.logCpu, t.logCpuCount)).append('/').append(gpuDisabled ? "n/a" : ms(t.logGpu, t.logGpuCount));
			t.logCpu = t.logGpu = 0;
			t.logCpuCount = t.logGpuCount = 0;
		}
		if (any) {
			Iris.logger.info(line.toString());
		}
	}

	/** Forge RenderGuiEvent.Post listener. */
	public static void renderOverlay(RenderGuiEvent.Post event) {
		if (!ExperimentalConfig.timingOverlay) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.renderDebug) {
			return; // Don't draw over F3
		}
		List<String[]> rows = overlayRows;
		if (rows.isEmpty()) {
			rows = new ArrayList<>();
			rows.add(new String[] {Iris.MODNAME + " timing: waiting for shader pack frames", "", ""});
		}
		PoseStack poseStack = event.getPoseStack();
		Font font = minecraft.font;
		int labelWidth = 0;
		int numberWidth = font.width("000.00");
		for (String[] row : rows) {
			labelWidth = Math.max(labelWidth, font.width(row[0]));
			numberWidth = Math.max(numberWidth, Math.max(font.width(row[1]), font.width(row[2])));
		}
		int x = 2;
		int y = 2;
		int cpuRight = x + labelWidth + 8 + numberWidth;
		int gpuRight = cpuRight + 8 + numberWidth;
		GuiComponent.fill(poseStack, x - 1, y - 1, gpuRight + 2, y + rows.size() * 10, 0x90000000);
		RenderSystem.enableBlend();
		for (int i = 0; i < rows.size(); i++) {
			String[] row = rows.get(i);
			int color = i == 0 ? 0xFFFF55 : 0xFFFFFF;
			int rowY = y + i * 10;
			font.drawShadow(poseStack, row[0], x, rowY, color);
			font.drawShadow(poseStack, row[1], cpuRight - font.width(row[1]), rowY, color);
			font.drawShadow(poseStack, row[2], gpuRight - font.width(row[2]), rowY, color);
		}
	}
}
