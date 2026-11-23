package net.coderbot.iris.compat.dh;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

import com.google.common.primitives.Ints;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.blending.BufferBlendOverride;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.shader.GlShader;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.gl.texture.TextureType;
import net.coderbot.iris.pipeline.ShaderPrinter;
import net.coderbot.iris.pipeline.SodiumTerrainPipeline;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.vendored.joml.Matrix4f;
import net.minecraft.client.Minecraft;

/**
 * Draws Distant Horizons' generic objects (DH clouds, beacon beams and other API boxes) through the shader pack,
 * using its dh_generic program or, like Oculus 1.8.0, its dh_terrain program as a fallback. Ported from 1.8.0's
 * IrisGenericRenderProgram, adapted to this tree:
 * - uniforms, samplers and images come from the same SodiumTerrainPipeline helpers as IrisLodRenderProgram;
 * - matrices are DHCompat's (the same ones LODs use), so generic objects depth-test correctly against LODs;
 * - attribute locations are bound at link time (this tree's transformer cannot emit layout qualifiers).
 *
 * DH draws each object as an instanced unit box (attribute 0 = corner, 1..5 = per-instance colour, scale, chunk and
 * sub-chunk position, material). Only DH's instanced path is supported, like 1.8.0; DH uses it on every platform
 * where it renders generic objects at all (instanced arrays are core in OpenGL 3.3).
 */
public final class IrisGenericRenderProgram implements IDhApiGenericObjectShaderProgram {
	private static boolean warnedDirect;

	private final int id;
	private final int vao;
	private final ProgramUniforms uniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;
	private final net.coderbot.iris.uniforms.custom.CustomUniforms customUniforms;
	private final BlendModeOverride blend;
	private final List<BufferBlendOverride> bufferBlends;

	private final int projection;
	private final int projectionInverse;
	private final int modelView;
	private final int modelViewInverse;
	private final int normalMatrix;
	private final int offsetChunk;
	private final int offsetSubChunk;
	private final int cameraChunk;
	private final int cameraSubChunk;
	private final int blockLight;
	private final int skyLight;

	private final Matrix4f projectionInverseScratch = new Matrix4f();
	private final Matrix4f modelViewInverseScratch = new Matrix4f();
	private final Matrix4f normalMatrixScratch = new Matrix4f();

	private IrisGenericRenderProgram(int id, ProgramSource source, SodiumTerrainPipeline pipeline) {
		this.id = id;
		ProgramUniforms.Builder builder = pipeline.initUniforms(id);
		this.uniforms = builder.buildUniforms();
		this.samplers = pipeline.initTerrainSamplers(id);
		this.images = pipeline.initTerrainImages(id);
		this.customUniforms = pipeline.getCustomUniforms();
		this.customUniforms.mapholderToPass(builder, this);

		this.blend = source.getDirectives().getBlendModeOverride().orElse(null);
		this.bufferBlends = new ArrayList<>();
		source.getDirectives().getBufferBlendOverrides().forEach(info -> {
			int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), info.getIndex());
			if (index >= 0) {
				bufferBlends.add(new BufferBlendOverride(index, info.getBlendMode()));
			}
		});

		this.projection = uniform("iris_ProjectionMatrix");
		this.projectionInverse = uniform("iris_ProjectionMatrixInverse");
		this.modelView = uniform("iris_ModelViewMatrix");
		this.modelViewInverse = uniform("iris_ModelViewMatrixInverse");
		this.normalMatrix = uniform("iris_NormalMatrix");
		this.offsetChunk = uniform("uOffsetChunk");
		this.offsetSubChunk = uniform("uOffsetSubChunk");
		this.cameraChunk = uniform("uCameraPosChunk");
		this.cameraSubChunk = uniform("uCameraPosSubChunk");
		this.blockLight = uniform("uBlockLight");
		this.skyLight = uniform("uSkyLight");

		this.vao = GL30C.glGenVertexArrays();
	}

	public static IrisGenericRenderProgram create(ProgramSource source, SodiumTerrainPipeline pipeline) {
		Map<PatchShaderType, String> transformed = TransformPatcher.patchDHGeneric(
			source.getVertexSource().orElseThrow(IllegalStateException::new),
			source.getGeometrySource().orElse(null),
			source.getFragmentSource().orElseThrow(IllegalStateException::new));
		String name = source.getName() + "_dh_generic";
		ShaderPrinter.printProgram(name).addSources(transformed).print();

		GlShader vertex = new GlShader(ShaderType.VERTEX, name + ".vsh", transformed.get(PatchShaderType.VERTEX));
		GlShader fragment = new GlShader(ShaderType.FRAGMENT, name + ".fsh", transformed.get(PatchShaderType.FRAGMENT));
		String geometrySource = transformed.get(PatchShaderType.GEOMETRY);
		GlShader geometry = geometrySource == null ? null : new GlShader(ShaderType.GEOMETRY, name + ".gsh", geometrySource);

		int program = GlStateManager.glCreateProgram();
		// DH's generic renderer feeds these attribute indices (GlGenericObjectRenderer.renderBoxGroupInstanced).
		GlStateManager._glBindAttribLocation(program, 0, "vPosition");
		GlStateManager._glBindAttribLocation(program, 1, "iris_color");
		GlStateManager._glBindAttribLocation(program, 2, "aScale");
		GlStateManager._glBindAttribLocation(program, 3, "aTranslateChunk");
		GlStateManager._glBindAttribLocation(program, 4, "aTranslateSubChunk");
		GlStateManager._glBindAttribLocation(program, 5, "aMaterial");
		GlStateManager.glAttachShader(program, vertex.getHandle());
		if (geometry != null) {
			GlStateManager.glAttachShader(program, geometry.getHandle());
		}
		GlStateManager.glAttachShader(program, fragment.getHandle());
		GlStateManager.glLinkProgram(program);
		IrisRenderSystem.detachShader(program, vertex.getHandle());
		IrisRenderSystem.detachShader(program, fragment.getHandle());
		if (geometry != null) {
			IrisRenderSystem.detachShader(program, geometry.getHandle());
		}
		vertex.destroy();
		fragment.destroy();
		if (geometry != null) {
			geometry.destroy();
		}

		String log = IrisRenderSystem.getProgramInfoLog(program);
		if (!log.isEmpty()) {
			Iris.logger.warn("Program link log for " + name + ": " + log);
		}
		if (GlStateManager.glGetProgrami(program, GL20C.GL_LINK_STATUS) != GL20C.GL_TRUE) {
			GlStateManager.glDeleteProgram(program);
			throw new RuntimeException("Linking " + name + " failed, see the log for details");
		}

		return new IrisGenericRenderProgram(program, source, pipeline);
	}

	private int uniform(String name) {
		return GL20C.glGetUniformLocation(id, name);
	}

	@Override
	public boolean overrideThisFrame() {
		return DHCompat.isActive();
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public void free() {
		GlStateManager.glDeleteProgram(id);
		GL30C.glDeleteVertexArrays(vao);
	}

	@Override
	public void bind(DhApiRenderParam param) {
		GL30C.glBindVertexArray(vao);
		GlStateManager._glUseProgram(id);
		if (blend != null) {
			blend.apply();
		}
		for (BufferBlendOverride bufferBlend : bufferBlends) {
			bufferBlend.apply();
		}

		Matrix4f projectionMatrix = DHCompat.createProjectionMatrix(param);
		Matrix4f modelViewMatrix = DHCompat.getModelViewMatrix(param);
		projectionInverseScratch.set(projectionMatrix).invert();
		modelViewInverseScratch.set(modelViewMatrix).invert();
		normalMatrixScratch.set(modelViewInverseScratch).transpose();
		setMatrix(projection, projectionMatrix);
		setMatrix(projectionInverse, projectionInverseScratch);
		setMatrix(modelView, modelViewMatrix);
		setMatrix(modelViewInverse, modelViewInverseScratch);
		setMatrix(normalMatrix, normalMatrixScratch);

		// Same as Oculus 1.8.0: make sure the lightmap is on its sampler unit for lmcoord lookups.
		Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
		IrisRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), IrisSamplers.LIGHTMAP_TEXTURE_UNIT,
			RenderSystem.getShaderTexture(2));

		uniforms.update();
		customUniforms.push(this);
		samplers.update();
		images.update();
	}

	@Override
	public void unbind() {
		GL30C.glBindVertexArray(0);
		GlStateManager._glUseProgram(0);
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		BlendModeOverride.restore();
	}

	@Override
	public void bindVertexBuffer(int vbo) {
		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
		GL20C.glEnableVertexAttribArray(0);
		GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, 12, 0L);
	}

	@Override
	public void fillIndirectUniformData(DhApiRenderParam param, DhApiRenderableBoxGroupShading shading,
			IDhApiRenderableBoxGroup boxGroup, DhApiVec3d cameraPos) {
		bind(param);
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11C.GL_LEQUAL);

		DhApiVec3d origin = boxGroup.getOriginBlockPos();
		setChunkUniforms(offsetChunk, offsetSubChunk, origin.x, origin.y, origin.z);
		setChunkUniforms(cameraChunk, cameraSubChunk, cameraPos.x, cameraPos.y, cameraPos.z);
		if (blockLight != -1) {
			GL20C.glUniform1i(blockLight, boxGroup.getBlockLight());
		}
		if (skyLight != -1) {
			GL20C.glUniform1i(skyLight, boxGroup.getSkyLight());
		}
	}

	@Override
	public void fillSharedDirectUniformData(DhApiRenderParam param, DhApiRenderableBoxGroupShading shading,
			IDhApiRenderableBoxGroup boxGroup, DhApiVec3d cameraPos) {
		warnDirect();
	}

	@Override
	public void fillDirectUniformData(DhApiRenderParam param, IDhApiRenderableBoxGroup boxGroup,
			DhApiRenderableBox box, DhApiVec3d cameraPos) {
		warnDirect();
	}

	private static void warnDirect() {
		// Oculus 1.8.0 throws here; logging once keeps the game running on a GPU without instanced arrays.
		if (!warnedDirect) {
			warnedDirect = true;
			Iris.logger.warn("Distant Horizons is drawing generic objects without instancing, which the shader pack path does not support; they may be misplaced.");
		}
	}

	/** Splits a world position into chunk (integer) and in-chunk (float) parts, as DH and 1.8.0 do, to keep precision. */
	private static void setChunkUniforms(int chunkLocation, int subChunkLocation, double x, double y, double z) {
		int cx = (int) Math.floor(x / 16.0);
		int cy = (int) Math.floor(y / 16.0);
		int cz = (int) Math.floor(z / 16.0);
		if (chunkLocation != -1) {
			GL30C.glUniform3i(chunkLocation, cx, cy, cz);
		}
		if (subChunkLocation != -1) {
			GL20C.glUniform3f(subChunkLocation, (float) (x - cx * 16.0), (float) (y - cy * 16.0), (float) (z - cz * 16.0));
		}
	}

	private static void setMatrix(int location, Matrix4f matrix) {
		if (location == -1) {
			return;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer buffer = stack.mallocFloat(16);
			matrix.get(buffer);
			IrisRenderSystem.uniformMatrix4fv(location, false, buffer);
		}
	}
}
