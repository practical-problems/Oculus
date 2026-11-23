package net.coderbot.iris.compat.dh;

import org.lwjgl.opengl.GL30C;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;

import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.shadows.ShadowRenderingState;

public final class DhFrameBufferWrapper implements IDhApiFramebuffer {
	private final GlFramebuffer terrainFramebuffer;
	private final GlFramebuffer translucentFramebuffer;
	private final GlFramebuffer shadowFramebuffer;
	private int attachedDepthTexture;

	public DhFrameBufferWrapper(GlFramebuffer terrainFramebuffer, GlFramebuffer translucentFramebuffer, GlFramebuffer shadowFramebuffer) {
		this.terrainFramebuffer = terrainFramebuffer;
		this.translucentFramebuffer = translucentFramebuffer;
		this.shadowFramebuffer = shadowFramebuffer;
	}

	private boolean inShadowPass() {
		return shadowFramebuffer != null && ShadowRenderingState.areShadowsCurrentlyBeingRendered();
	}

	private GlFramebuffer currentFramebuffer() {
		if (inShadowPass()) {
			return shadowFramebuffer;
		}
		return DHCompat.isTransparentPass() ? translucentFramebuffer : terrainFramebuffer;
	}

	@Override
	public boolean overrideThisFrame() {
		return DHCompat.isActive();
	}

	@Override
	public void bind() {
		if (inShadowPass()) {
			// The shadow map owns its depth; DH's own depth texture is not used here.
			shadowFramebuffer.bind();
			return;
		}
		GlFramebuffer framebuffer = currentFramebuffer();
		int depthTexture = DHCompat.getDepthTexture();
		if (depthTexture != 0 && depthTexture != attachedDepthTexture) {
			attachDepthTexture(depthTexture);
		}
		framebuffer.bind();
	}

	@Override
	public void addDepthAttachment(int textureId, boolean combinedStencil) {
		// DH calls this when it creates or recreates its depth texture. Force the
		// attachment even when OpenGL has reused the same numeric texture id.
		attachDepthTexture(textureId, true);
		DHCompat.setDepthTexture(textureId);
	}

	void attachDepthTexture(int textureId) {
		attachDepthTexture(textureId, false);
	}

	private void attachDepthTexture(int textureId, boolean force) {
		if (textureId == 0 || (!force && textureId == attachedDepthTexture)) {
			return;
		}
		terrainFramebuffer.addExternalDepthAttachment(textureId);
		if (translucentFramebuffer != terrainFramebuffer) {
			translucentFramebuffer.addExternalDepthAttachment(textureId);
		}
		attachedDepthTexture = textureId;
	}

	@Override
	public int getId() {
		return currentFramebuffer().getId();
	}

	@Override
	public int getStatus() {
		bind();
		return GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
	}

	@Override
	public void addColorAttachment(int attachmentIndex, int textureId) {
		// Oculus owns the shader-pack colortex attachments.
	}

	@Override
	public void destroy() {
		// The Oculus pipeline owns this framebuffer.
	}
}
