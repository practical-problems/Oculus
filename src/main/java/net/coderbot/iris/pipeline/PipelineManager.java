package net.coderbot.iris.pipeline;

import com.mojang.blaze3d.platform.GlStateManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.DimensionId;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20C;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PipelineManager {
	private static PipelineManager instance;
	private final Function<NamespacedId, WorldRenderingPipeline> pipelineFactory;
	private final Map<NamespacedId, WorldRenderingPipeline> pipelinesPerDimension = new HashMap<>();
	private WorldRenderingPipeline pipeline = new FixedFunctionWorldRenderingPipeline();
	// Experimental "Cached pipeline lookup" (Emberlens): getPipeline() returns this instead of a new Optional.
	// Kept in sync with every assignment to pipeline below.
	private Optional<WorldRenderingPipeline> pipelineOptional = Optional.of(pipeline);
	private int versionCounterForSodiumShaderReload = 0;

	public PipelineManager(Function<NamespacedId, WorldRenderingPipeline> pipelineFactory) {
		this.pipelineFactory = pipelineFactory;
	}

	public WorldRenderingPipeline preparePipeline(NamespacedId currentDimension) {
		if (!pipelinesPerDimension.containsKey(currentDimension)) {
			SystemTimeUniforms.COUNTER.reset();
			SystemTimeUniforms.TIMER.reset();

			Iris.logger.info("Creating pipeline for dimension {}", currentDimension);
			pipeline = pipelineFactory.apply(currentDimension);
			pipelineOptional = Optional.ofNullable(pipeline);
			pipelinesPerDimension.put(currentDimension, pipeline);

			if (BlockRenderingSettings.INSTANCE.isReloadRequired()) {
				if (Minecraft.getInstance().levelRenderer != null) {
					Minecraft.getInstance().levelRenderer.allChanged();
				}

				BlockRenderingSettings.INSTANCE.clearReloadRequired();
			}
		} else {
			pipeline = pipelinesPerDimension.get(currentDimension);
			pipelineOptional = Optional.ofNullable(pipeline);
		}

		return pipeline;
	}

	@Nullable
	public WorldRenderingPipeline getPipelineNullable() {
		return pipeline;
	}

	public Optional<WorldRenderingPipeline> getPipeline() {
		if (net.coderbot.iris.config.ExperimentalConfig.cachedPipelineLookup) {
			return pipelineOptional;
		}
		return Optional.ofNullable(pipeline);
	}

	/**
	 * In IrisChunkProgramOverrides#getProgramOverride,
	 * it uses version counter to check whether to reload sodium shaders.
	 * This fixes a compat issue with Immersive Portals(#1188).
	 * Immersive Portals may load multiple client dimensions at the same time,
	 * and every dimension corresponds to a IrisChunkProgramOverrides object.
	 * Multiple dimensions (mod dimensions that fallback to overworld shaders) may use the same pipeline.
	 * This ensures that the sodium shader for each dimension will get properly reloaded.
	 */
	public int getVersionCounterForSodiumShaderReload() {
		return versionCounterForSodiumShaderReload;
	}

	/**
	 * Destroys all the current pipelines.
	 *
	 * <p>This method is <b>EXTREMELY DANGEROUS!</b> It is a huge potential source of hard-to-trace inconsistencies
	 * in program state. You must make sure that you <i>immediately</i> re-prepare the pipeline after destroying
	 * it to prevent the program from falling into an inconsistent state.</p>
	 *
	 * <p>In particular, </p>
	 *
	 * @see <a href="https://github.com/IrisShaders/Iris/issues/1330">this GitHub issue</a>
	 */
	public void destroyPipeline() {
		pipelinesPerDimension.forEach((dimensionId, pipeline) -> {
			Iris.logger.info("Destroying pipeline {}", dimensionId);
			resetTextureState();
			pipeline.destroy();
		});

		pipelinesPerDimension.clear();
		pipeline = null;
		pipelineOptional = Optional.empty();
		versionCounterForSodiumShaderReload++;
	}

	private void resetTextureState() {
		// Unbind all textures
		//
		// This is necessary because we don't want destroyed render target textures to remain bound to certain texture
		// units. Vanilla appears to properly rebind all textures as needed, and we do so too, so this does not cause
		// issues elsewhere.
		//
		// Without this code, there will be weird issues when reloading certain shaderpacks.
		for (int i = 0; i < 16; i++) {
			GlStateManager.glActiveTexture(GL20C.GL_TEXTURE0 + i);
			GlStateManager._bindTexture(0);
		}

		// Set the active texture unit to unit 0
		//
		// This seems to be what most code expects. It's a sane default in any case.
		GlStateManager.glActiveTexture(GL20C.GL_TEXTURE0);
	}
}
